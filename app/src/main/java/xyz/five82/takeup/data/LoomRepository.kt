package xyz.five82.takeup.data

internal class LoomRepository(
    private val preferences: ServerPreferences,
    private val client: LoomClient = LoomClient(),
) {
    suspend fun savedServerUrl(): String? = preferences.serverUrl()

    suspend fun connect(value: String): String {
        val server = ServerAddress.parse(value)
        client.checkHealth(server)
        return server.toString().also { preferences.saveServerUrl(it) }
    }

    suspend fun home(serverUrl: String): HomeContent {
        val server = ServerAddress.parse(serverUrl)
        return HomeContent(
            continueWatching = client.continueWatching(server),
            recentlyAdded = client.recentlyAdded(server),
            movies = client.movies(server),
            shows = client.shows(server),
        )
    }

    suspend fun movies(serverUrl: String): List<LoomItem> =
        client.movies(ServerAddress.parse(serverUrl))

    suspend fun shows(serverUrl: String): List<LoomItem> =
        client.shows(ServerAddress.parse(serverUrl))

    suspend fun seasons(serverUrl: String, showId: Long): List<LoomItem> =
        client.children(ServerAddress.parse(serverUrl), showId)
            .filter { it.kind == "season" }

    suspend fun episodes(serverUrl: String, seasonId: Long): List<LoomItem> {
        val server = ServerAddress.parse(serverUrl)
        // Loom's child summaries omit media duration and playback progress.
        return client.children(server, seasonId)
            .filter { it.kind == "episode" }
            .map { client.item(server, it.id) }
    }

    suspend fun item(serverUrl: String, itemId: Long): LoomItem =
        client.item(ServerAddress.parse(serverUrl), itemId)

    suspend fun preparePlayback(serverUrl: String, itemId: Long): PreparedPlayback {
        val server = ServerAddress.parse(serverUrl)
        val item = client.item(server, itemId)
        val playback = client.playback(server, itemId)
        return PreparedPlayback(
            itemId = item.id,
            title = item.title,
            streamUrl = server.stream(playback.streamPath).toString(),
            durationMs = playback.durationMs,
            resumePositionMs = item.progress?.resumePositionMs ?: 0L,
            container = playback.container,
        )
    }

    suspend fun saveProgress(
        serverUrl: String,
        itemId: Long,
        positionMs: Long,
        durationMs: Long,
    ) {
        client.saveProgress(
            server = ServerAddress.parse(serverUrl),
            itemId = itemId,
            positionMs = positionMs,
            durationMs = durationMs,
        )
    }

}
