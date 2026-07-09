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
import androidx.media3.common.TrackSelectionOverride
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
    val player: Player? get() = _playerFlow.value

    private val _uiState = MutableStateFlow(VideoPlayerUiState())
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()

    private var progressJob: Job? = null
    private var controlsTimerJob: Job? = null
    private var isFirstLoad = true

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
                updateAvailableQualities(tracks)
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

    private fun updateAvailableQualities(tracks: Tracks) {
        val qualities = mutableListOf<VideoQuality>()
        
        for (group in tracks.groups) {
            if (group.type == androidx.media3.common.C.TRACK_TYPE_VIDEO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    if (format.height != androidx.media3.common.Format.NO_VALUE) {
                        val label = "${format.height}p"
                        if (qualities.none { it.label == label }) {
                            qualities.add(VideoQuality(label, format.height, group, i))
                        }
                    }
                }
            }
        }
        
        val sortedQualities = qualities.sortedByDescending { it.height ?: 0 }.toMutableList()
        val autoQuality = VideoQuality("Auto", null)
        sortedQualities.add(0, autoQuality) // "Auto" at the top
        
        _uiState.update { state ->
            state.copy(
                availableQualities = sortedQualities,
                selectedQuality = state.selectedQuality ?: autoQuality
            )
        }
    }

    fun loadVideo(uri: Uri, notificationInfo: NotificationInfo? = null) {
        viewModelScope.launch {
            // Wait for player to be available
            while (_playerFlow.value == null) {
                delay(100.milliseconds)
            }
            
            val player = _playerFlow.value ?: return@launch
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
        player?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
        resetControlsTimer()
    }

    fun seekTo(position: Long) {
        player?.seekTo(position)
        resetControlsTimer()
    }

    fun rewind() {
        player?.let { it.seekTo((it.currentPosition - 10000).coerceAtLeast(0)) }
        resetControlsTimer()
    }

    fun fastForward() {
        player?.let { it.seekTo((it.currentPosition + 10000).coerceAtMost(it.duration)) }
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

    fun setQuality(quality: VideoQuality) {
        player?.let { p ->
            if (quality.height == null) {
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().clearOverrides().build()
            } else {
                quality.group?.let { group ->
                    p.trackSelectionParameters = p.trackSelectionParameters
                        .buildUpon()
                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, quality.index))
                        .build()
                }
            }
            _uiState.update { it.copy(selectedQuality = quality) }
        }
    }

    private fun resetControlsTimer() {
        controlsTimerJob?.cancel()
        controlsTimerJob = viewModelScope.launch {
            delay(3000.milliseconds)
            _uiState.update { it.copy(isControlsVisible = false) }
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                player?.let { p ->
                    _uiState.update { 
                        it.copy(
                            currentPosition = p.currentPosition,
                            duration = p.duration.coerceAtLeast(0L)
                        )
                    }
                }
                delay(500.milliseconds)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    override fun onCleared() {
        super.onCleared()
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
    val duration: Long = 0L,
    val playbackState: Int = Player.STATE_IDLE,
    val isMuted: Boolean = false,
    val availableQualities: List<VideoQuality> = emptyList(),
    val selectedQuality: VideoQuality? = null,
    val isFullScreen: Boolean = false,
    val isControlsVisible: Boolean = false,
    val videoAspectRatio: Float = 16f / 9f
)

data class VideoQuality(
    val label: String,
    val height: Int?,
    val group: Tracks.Group? = null,
    val index: Int = 0
)

data class NotificationInfo(
    val title: String,
    val artist: String,
    val photoUrl: String
)
