package dev.local.streamerremote

object AppCatalog {
    val labels = mapOf(
        "com.netflix.ninja" to "Netflix",
        "com.amazon.amazonvideo.livingroom" to "Prime Video",
        "com.google.android.youtube.tv" to "YouTube",
        "com.disney.disneyplus" to "Disney+",
        "com.spotify.tv.android" to "Spotify",
        "com.plexapp.android" to "Plex",
        "tv.twitch.android.app" to "Twitch",
        "com.google.android.apps.tv.launcherx" to "Google TV"
    )

    fun label(packageName: String): String =
        labels[packageName] ?: packageName.substringAfterLast('.')
            .replace('_', ' ').replaceFirstChar { it.uppercase() }
}
