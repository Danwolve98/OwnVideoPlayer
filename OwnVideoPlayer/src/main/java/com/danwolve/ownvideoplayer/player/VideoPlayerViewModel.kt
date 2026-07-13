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
                _uiState.update {
                    it.copy(
                        playbackState = playbackState,
                        isBuffering = buffering,
                        duration = player.duration.coerceAtLeast(0L)
                    )
                }

                if (playbackState == Player.STATE_READY && isFirstLoad) {
                    isFirstLoad = false
                }
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
        viewModelScope.launch {
            // Wait for player to be available
            while (_playerFlow.value == null) {
                delay(100.milliseconds)
            }
            
            val player = _playerFlow.value ?: return@launch
            
            // Si el video ya es el mismo y NO hay error, no hacemos nada.
            val currentUri = player.currentMediaItem?.localConfiguration?.uri
            if (currentUri == uri && _uiState.value.errorMessage == null) {
                return@launch
            }

            _uiState.update { it.copy(errorMessage = null) }

            // Solo comprobamos internet si vamos a cargar un video nuevo y es remoto
            if (!isNetworkAvailable() && uri.scheme?.startsWith("http") == true) {
                _uiState.update { it.copy(errorMessage = "No tienes conexión a internet") }
                return@launch
            }

            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(notificationInfo?.title ?: "Video Player")
                        .setArtist(notificationInfo?.artist ?: "OwnVideoPlayer")
                        .setArtworkUri(notificationInfo?.photoUrl?.toUri() ?: "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRTOcw9rP9JtQTdUaUoAtB0fuyrjU2R33C0v_pViPivO6FSl65HtssQOtv7&s=10".toUri())
                        .build()
                )
                .build()
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
        }
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
                if (it.isPlaying) it.pause() else it.play()
            }
        }
        resetControlsTimer()
    }

    fun seekTo(position: Long) {
        player?.seekTo(position)
        _uiState.update { it.copy(currentPosition = position) }
        resetControlsTimer()
    }

    fun rewind() {
        player?.let { 
            val newPos = (it.currentPosition - 10000).coerceAtLeast(0)
            it.seekTo(newPos)
            _uiState.update { state -> state.copy(currentPosition = newPos) }
        }
        resetControlsTimer()
    }

    fun fastForward() {
        player?.let { 
            val newPos = (it.currentPosition + 10000).coerceAtMost(it.duration)
            it.seekTo(newPos)
            _uiState.update { state -> state.copy(currentPosition = newPos) }
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
                delay(200.milliseconds)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopPlayback()
        stopProgressTracker()
        controlsTimerJob?.cancel()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }
}

data class VideoPlayerUiState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
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
    val title: String,
    val artist: String,
    val photoUrl: String
)
