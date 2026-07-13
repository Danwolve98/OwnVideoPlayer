package com.example.ownvideoplayer.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface Route {
    @Serializable
    data object Menu : Route

    @Serializable
    data object EmbeddedPlayer : Route

    @Serializable
    data object FullScreenPlayer : Route
}
