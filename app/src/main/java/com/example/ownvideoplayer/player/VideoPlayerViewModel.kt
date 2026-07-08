package com.example.ownvideoplayer.player

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerNotificationManager
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@OptIn(UnstableApi::class)
class VideoPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private var _player: ExoPlayer? = null
    val player: Player? get() = _player

    private val _uiState = MutableStateFlow(VideoPlayerUiState())
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()

    private var progressJob: Job? = null
    private var controlsTimerJob: Job? = null
    private var isFirstLoad = true
    private var notificationManager: PlayerNotificationManager? = null

    init {
        initializePlayer()
    }

    private fun initializePlayer() {
        // Optimized LoadControl for better video loading/buffering
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000, // Min buffer before starting playback
                50_000, // Max buffer
                1_000,  // Buffer for playback
                2_000   // Buffer for playback after re-buffer
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        _player = ExoPlayer.Builder(getApplication())
            .setLoadControl(loadControl)
            .build().apply {
                playWhenReady = true
                addListener(object : Player.Listener {
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
                                duration = duration.coerceAtLeast(0L)
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
        val mediaItem = MediaItem.fromUri(uri)
        _player?.setMediaItem(mediaItem)
        _player?.prepare()
        _player?.play()

        if (notificationInfo != null) {
            setupNotification(notificationInfo)
        } else {
            notificationManager?.setPlayer(null)
        }
    }

    private fun setupNotification(info: NotificationInfo) {
        val context = getApplication<Application>()
        val channelId = "video_player_channel"
        val notificationId = 1001

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Video Player"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, name, importance)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        notificationManager = PlayerNotificationManager.Builder(context, notificationId, channelId)
            .setMediaDescriptionAdapter(object : PlayerNotificationManager.MediaDescriptionAdapter {
                override fun getCurrentContentTitle(player: Player): CharSequence = info.title
                override fun getCurrentContentText(player: Player): CharSequence = info.artist
                override fun getCurrentLargeIcon(player: Player, callback: PlayerNotificationManager.BitmapCallback): Bitmap? {
                    viewModelScope.launch {
                        val loader = ImageLoader(context)
                        val request = ImageRequest.Builder(context)
                            .data(info.photoUrl)
                            .build()
                        val result = loader.execute(request)
                        if (result is SuccessResult) {
                            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                            if (bitmap != null) callback.onBitmap(bitmap)
                        }
                    }
                    return null
                }
                override fun createCurrentContentIntent(player: Player) = null
            })
            .build().apply {
                setPlayer(_player)
            }
    }

    fun playPause() {
        _player?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
        resetControlsTimer()
    }

    fun seekTo(position: Long) {
        _player?.seekTo(position)
        resetControlsTimer()
    }

    fun rewind() {
        _player?.let { it.seekTo((it.currentPosition - 10000).coerceAtLeast(0)) }
        resetControlsTimer()
    }

    fun fastForward() {
        _player?.let { it.seekTo((it.currentPosition + 10000).coerceAtMost(it.duration)) }
        resetControlsTimer()
    }

    fun toggleMute() {
        _player?.let { it.volume = if (it.volume == 0f) 1f else 0f }
    }

    fun toggleFullScreen() {
        _uiState.update { it.copy(isFullScreen = !it.isFullScreen) }
    }

    fun setFullScreen(enabled: Boolean) {
        _uiState.update { it.copy(isFullScreen = enabled) }
    }

    fun setRepeatMode(enabled: Boolean) {
        _player?.repeatMode = if (enabled) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
    }

    fun toggleControls() {
        _uiState.update { it.copy(isControlsVisible = !it.isControlsVisible) }
        if (_uiState.value.isControlsVisible) {
            resetControlsTimer()
        }
    }

    fun setQuality(quality: VideoQuality) {
        _player?.let { p ->
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
            delay(3000)
            _uiState.update { it.copy(isControlsVisible = false) }
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                _player?.let { p ->
                    _uiState.update { 
                        it.copy(
                            currentPosition = p.currentPosition,
                            duration = p.duration.coerceAtLeast(0L)
                        )
                    }
                }
                delay(500)
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
        notificationManager?.setPlayer(null)
        _player?.release()
        _player = null
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
