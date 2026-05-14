package com.example.ownvideoplayer.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface Route {
    @Serializable
    data object Player : Route

    @Serializable
    data object FullScreenPlayer : Route
}
