package com.danwolve.ownvideoplayer.player

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import androidx.media3.datasource.RawResourceDataSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Duration.Companion.milliseconds
import androidx.core.net.toUri

@OptIn(UnstableApi::class)
class VideoPlayerViewModel(application: Application) : AndroidViewModel(application) {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val _playerFlow = MutableStateFlow<Player?>(null)
    val playerFlow: StateFlow<Player?> = _playerFlow.asStateFlow()
    val player: Player? get() = _playerFlow.value

    private val _uiState = MutableStateFlow(VideoPlayerUiState())
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()

    private var progressJob: Job? = null
    private var controlsTimerJob: Job? = null
    private var loadJob: Job? = null
    private var bufferingWatchdogJob: Job? = null
    private var isFirstLoad = true
    private var lastUri: Uri? = null

    init {
        initializeController()
    }

    private fun initializeController() {
        val sessionToken = SessionToken(getApplication(), ComponentName(getApplication(), PlaybackService::class.java))
        controllerFuture = MediaController.Builder(getApplication(), sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get() ?: return@addListener
                _playerFlow.value = controller
                setupPlayerListener(controller)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupPlayerListener(player: Player) {
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED)) {
                    val isBuffering = player.playbackState == Player.STATE_BUFFERING
                    if (isBuffering) {
                        startBufferingWatchdog()
                    } else {
                        bufferingWatchdogJob?.cancel()
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val message = when (error.errorCode) {
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "No tienes conexión a internet"
                    androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "Error de hardware: No se puede decodificar el video"
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "El archivo de video no existe"
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Error del servidor (HTTP ${error.errorCode})"
                    else -> "Error al cargar el video: ${error.errorCodeName}"
                }
                _uiState.update { it.copy(errorMessage = message, isBuffering = false) }
                
                // On fatal error, try to reset player state
                player.prepare()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) {
                    startProgressTracker()
                    if (!isFirstLoad) {
                        resetControlsTimer()
                    }
                } else {
                    stopProgressTracker()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val buffering = playbackState == Player.STATE_BUFFERING
                val duration = if (player.duration == androidx.media3.common.C.TIME_UNSET) 0L else player.duration.coerceAtLeast(0L)

                _uiState.update {
                    it.copy(
                        playbackState = playbackState,
                        isBuffering = buffering,
                        duration = duration,
                        currentPosition = if (buffering || playbackState == Player.STATE_IDLE) it.currentPosition else player.currentPosition
                    )
                }

                if (playbackState == Player.STATE_READY) {
                    if (isFirstLoad) {
                        isFirstLoad = false
                    }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    _uiState.update { it.copy(currentPosition = newPosition.positionMs) }
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                _uiState.update { it.copy(playWhenReady = playWhenReady) }
            }

            override fun onVolumeChanged(volume: Float) {
                _uiState.update { it.copy(isMuted = volume == 0f) }
            }

            override fun onTracksChanged(tracks: Tracks) {
                // Quality selection removed
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                val ratio = if (videoSize.height > 0) {
                    (videoSize.width.toFloat() * videoSize.pixelWidthHeightRatio) / videoSize.height.toFloat()
                } else {
                    16f / 9f
                }
                _uiState.update { it.copy(videoAspectRatio = ratio) }
            }
        })
    }

    fun loadVideo(uri: Uri, notificationInfo: NotificationInfo? = null) {
        lastUri = uri
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // Wait for player to be available with a timeout
            var attempts = 0
            while (_playerFlow.value == null && attempts < 50) {
                delay(100.milliseconds)
                attempts++
            }
            
            val player = _playerFlow.value ?: return@launch
            
            // Si el video ya es el mismo, comprobamos si hay que forzar la carga
            val currentUri = player.currentMediaItem?.localConfiguration?.uri
            val isSameUri = currentUri == uri
            
            if (isSameUri && _uiState.value.errorMessage == null && player.playbackState != Player.STATE_IDLE && player.playerError == null) {
                if (player.playbackState == Player.STATE_ENDED) {
                    player.seekTo(0)
                }
                player.play()
                return@launch
            }

            _uiState.update { it.copy(errorMessage = null, isBuffering = true) }

            // Solo comprobamos internet si vamos a cargar un video nuevo y es remoto
            if (uri.scheme?.startsWith("http") == true && !isNetworkAvailable()) {
                _uiState.update { it.copy(errorMessage = "No tienes conexión a internet", isBuffering = false) }
                return@launch
            }

            val metadataBuilder = androidx.media3.common.MediaMetadata.Builder()
            
            // Título: Prioridad Info > Nombre de archivo > Default
            val title = notificationInfo?.title ?: getFileNameFromUri(uri) ?: "Video Player"
            metadataBuilder.setTitle(title)
            metadataBuilder.setArtist(notificationInfo?.artist ?: "OwnVideoPlayer")

            // Foto: Prioridad Info > Frame de video
            if (notificationInfo?.photoUrl != null) {
                metadataBuilder.setArtworkUri(notificationInfo.photoUrl.toUri())
            } else {
                val frameData = getFrameFromUri(uri)
                if (frameData != null) {
                    metadataBuilder.setArtworkData(frameData, androidx.media3.common.MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                }
            }

            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setMediaId(uri.toString())
                .setMediaMetadata(metadataBuilder.build())
                .build()
            
            player.pause()
            player.stop()
            player.clearMediaItems()
            player.setMediaItem(mediaItem)
            player.seekTo(0)
            player.prepare()
            player.play()
        }
    }

    fun loadRawResource(resourceId: Int, notificationInfo: NotificationInfo? = null) {
        val uri = RawResourceDataSource.buildRawResourceUri(resourceId)
        loadVideo(uri, notificationInfo)
    }

    fun playPause() {
        val currentState = _uiState.value
        if (currentState.errorMessage != null) {
            // Si hay un error, el botón de play actúa como reintento usando la última URI
            lastUri?.let { uri ->
                loadVideo(uri)
            }
        } else {
            player?.let {
                if (it.playWhenReady) it.pause() else it.play()
            }
        }
        resetControlsTimer()
    }

    fun seekTo(position: Long) {
        player?.let {
            it.seekTo(position)
            _uiState.update { state -> 
                state.copy(
                    currentPosition = position,
                    isBuffering = true
                ) 
            }
        }
        resetControlsTimer()
    }

    fun rewind() {
        player?.let { 
            val newPos = (it.currentPosition - 10000).coerceAtLeast(0)
            it.seekTo(newPos)
            _uiState.update { state -> state.copy(currentPosition = newPos, isBuffering = true) }
        }
        resetControlsTimer()
    }

    fun fastForward() {
        player?.let { 
            val newPos = (it.currentPosition + 10000).coerceAtMost(it.duration)
            it.seekTo(newPos)
            _uiState.update { state -> state.copy(currentPosition = newPos, isBuffering = true) }
        }
        resetControlsTimer()
    }

    fun toggleMute() {
        player?.let { it.volume = if (it.volume == 0f) 1f else 0f }
    }

    fun toggleFullScreen() {
        _uiState.update { it.copy(isFullScreen = !it.isFullScreen) }
    }

    fun setFullScreen(enabled: Boolean) {
        _uiState.update { it.copy(isFullScreen = enabled) }
    }

    fun setRepeatMode(enabled: Boolean) {
        player?.repeatMode = if (enabled) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
    }

    fun toggleControls() {
        _uiState.update { it.copy(isControlsVisible = !it.isControlsVisible) }
        if (_uiState.value.isControlsVisible) {
            resetControlsTimer()
        }
    }

    fun stopPlayback() {
        player?.let {
            it.stop()
            it.clearMediaItems()
        }
    }

    private fun startBufferingWatchdog() {
        bufferingWatchdogJob?.cancel()
        bufferingWatchdogJob = viewModelScope.launch {
            delay(10000.milliseconds) // Give it 10 seconds
            if (_uiState.value.isBuffering) {
                player?.let { p ->
                    if (p.playbackState == Player.STATE_BUFFERING) {
                        p.prepare() // Re-prepare to try and kick-start it
                    }
                }
            }
        }
    }

    private fun resetControlsTimer() {
        controlsTimerJob?.cancel()
        controlsTimerJob = viewModelScope.launch {
            delay(3000.milliseconds)
            _uiState.update { it.copy(isControlsVisible = false) }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getApplication<Application>().getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                player?.let { p ->
                    if (p.playbackState != Player.STATE_BUFFERING) {
                        val pos = p.currentPosition
                        val buf = p.bufferedPosition
                        val dur = p.duration.coerceAtLeast(0L)
                        _uiState.update {
                            it.copy(
                                currentPosition = pos,
                                bufferedPosition = buf,
                                duration = dur
                            )
                        }
                    }
                }
                delay(200.milliseconds)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    private suspend fun getFrameFromUri(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        val retriever = android.media.MediaMetadataRetriever()
        try {
            when (uri.scheme) {
                "http", "https" -> {
                    retriever.setDataSource(uri.toString(), HashMap<String, String>())
                }
                "rawresource" -> {
                    val resId = uri.path?.trim('/')?.toIntOrNull()
                    if (resId != null) {
                        val afd = getApplication<Application>().resources.openRawResourceFd(resId)
                        retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        afd.close()
                    } else {
                        retriever.setDataSource(getApplication(), uri)
                    }
                }
                else -> {
                    retriever.setDataSource(getApplication(), uri)
                }
            }
            
            // Intentamos capturar el frame en el segundo 1, si falla, intentamos al inicio (0)
            val bitmap = retriever.getFrameAtTime(1000000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime

            val stream = java.io.ByteArrayOutputStream()
            bitmap?.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, stream)
            stream.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        return try {
            when (uri.scheme) {
                "file", "content" -> uri.lastPathSegment
                "rawresource" -> {
                    val resId = uri.path?.trim('/')?.toIntOrNull()
                    if (resId != null) {
                        getApplication<Application>().resources.getResourceEntryName(resId)
                            .replaceFirstChar { it.uppercase() }
                    } else null
                }
                "android.resource" -> uri.lastPathSegment
                else -> {
                    if (uri.scheme?.startsWith("http") == true) {
                        uri.pathSegments.lastOrNull()?.substringBeforeLast('.')
                    } else null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPlayback()
        stopProgressTracker()
        controlsTimerJob?.cancel()
        loadJob?.cancel()
        bufferingWatchdogJob?.cancel()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }
}

data class VideoPlayerUiState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val playWhenReady: Boolean = false,
    val currentPosition: Long = 0L,
    val bufferedPosition: Long = 0L,
    val duration: Long = 0L,
    val playbackState: Int = Player.STATE_IDLE,
    val isMuted: Boolean = false,
    val isFullScreen: Boolean = false,
    val isControlsVisible: Boolean = false,
    val videoAspectRatio: Float = 16f / 9f,
    val errorMessage: String? = null
)

data class NotificationInfo(
    val title: String? = null,
    val artist: String? = null,
    val photoUrl: String? = null
)
