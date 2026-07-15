package com.example.ownvideoplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.danwolve.ownvideoplayer.player.NotificationInfo
import com.danwolve.ownvideoplayer.ui.OwnVideoPlayerDialog
import com.danwolve.ownvideoplayer.ui.VideoSource
import com.example.ownvideoplayer.R

@Composable
fun VideoPlayerScreen(
    onDismiss: () -> Unit
) {
    var showPlayer by remember { mutableStateOf(true) }

    OwnVideoPlayerDialog(
        show = showPlayer,
        source = VideoSource.Raw(R.raw.michael),
        onDismiss = {
            showPlayer = false
            onDismiss()
        },
        notificationInfo = NotificationInfo()
    )
}
