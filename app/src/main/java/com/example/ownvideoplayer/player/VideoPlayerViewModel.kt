package com.example.ownvideoplayer.player

import android.app.Application
import android.net.Uri
import kotlin.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class VideoPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private var _player: ExoPlayer? = null
    val player: Player? get() = _player

    private val _uiState = MutableStateFlow(VideoPlayerUiState())
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()

    private var progressJob: Job? = null

    init {
        initializePlayer()
    }

    private fun initializePlayer() {
        _player = ExoPlayer.Builder(getApplication()).build().apply {
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _uiState.update { it.copy(isPlaying = isPlaying) }
                    if (isPlaying) {
                        startProgressTracker()
                    } else {
                        stopProgressTracker()
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    _uiState.update {
                        it.copy(
                            playbackState = playbackState,
                            duration = duration.coerceAtLeast(0L)
                        )
                    }
                }

                override fun onVolumeChanged(volume: Float) {
                    _uiState.update { it.copy(isMuted = volume == 0f) }
                }

                override fun onTracksChanged(tracks: Tracks) {
                    updateAvailableQualities(tracks)
                }
            })
        }
    }

    private fun updateAvailableQualities(tracks: Tracks) {
        val qualities = mutableListOf<VideoQuality>()
        qualities.add(VideoQuality("Auto", null))
        
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
        
        _uiState.update { it.copy(availableQualities = qualities.sortedByDescending { q -> q.height ?: 0 }) }
    }

    fun setQuality(quality: VideoQuality) {
        _player?.let { p ->
            if (quality.height == null) {
                p.trackSelectionParameters = p.trackSelectionParameters
                    .buildUpon()
                    .clearOverrides()
                    .build()
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

    fun toggleMute() {
        _player?.let {
            if (it.volume == 0f) {
                it.volume = 1f
            } else {
                it.volume = 0f
            }
        }
    }

    fun toggleFullScreen() {
        _uiState.update { it.copy(isFullScreen = !it.isFullScreen) }
    }

    fun loadVideo(uri: Uri) {
        val mediaItem = MediaItem.fromUri(uri)
        _player?.setMediaItem(mediaItem)
        _player?.prepare()
        _player?.play()
    }

    fun playPause() {
        _player?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                it.play()
            }
        }
    }

    fun seekTo(position: Long) {
        _player?.seekTo(position)
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
        _player?.release()
        _player = null
    }
}

data class VideoPlayerUiState(
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val playbackState: Int = Player.STATE_IDLE,
    val isMuted: Boolean = false,
    val availableQualities: List<VideoQuality> = emptyList(),
    val selectedQuality: VideoQuality? = null,
    val isFullScreen: Boolean = false
)

@OptIn(UnstableApi::class)
data class VideoQuality(
    val label: String,
    val height: Int?,
    val group: Tracks.Group? = null,
    val index: Int = 0
)
