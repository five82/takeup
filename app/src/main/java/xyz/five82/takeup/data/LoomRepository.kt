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
        val continueWatching = client.continueWatching(server)
        val recentlyAdded = client.recentlyAdded(server)
        val contextualItems = addEpisodeContext(
            items = (continueWatching + recentlyAdded).distinctBy { it.id },
            itemById = { id -> runCatching { client.item(server, id) }.getOrNull() },
        ).associateBy { it.id }
        return HomeContent(
            continueWatching = continueWatching.map { contextualItems[it.id] ?: it },
            recentlyAdded = recentlyAdded.map { contextualItems[it.id] ?: it },
            movies = client.movies(server),
            shorts = client.shorts(server),
            shows = client.shows(server),
        )
    }

    suspend fun movies(serverUrl: String, genreId: Long = 0): List<LoomItem> =
        client.movies(ServerAddress.parse(serverUrl), genreId)

    suspend fun genres(serverUrl: String): List<GenreSummary> =
        client.genres(ServerAddress.parse(serverUrl))

    suspend fun search(serverUrl: String, query: String): List<LoomItem> =
        client.search(ServerAddress.parse(serverUrl), query)

    suspend fun shorts(serverUrl: String): List<LoomItem> =
        client.shorts(ServerAddress.parse(serverUrl))

    suspend fun shows(serverUrl: String): List<LoomItem> =
        client.shows(ServerAddress.parse(serverUrl))

    suspend fun seasons(serverUrl: String, show: LoomItem): List<LoomItem> =
        client.children(ServerAddress.parse(serverUrl), show.id)
            .filter { it.kind == "season" }
            .map { it.copy(seriesTitle = show.title) }

    suspend fun episodes(
        serverUrl: String,
        show: LoomItem,
        season: LoomItem,
    ): List<LoomItem> {
        val server = ServerAddress.parse(serverUrl)
        // Loom's child summaries omit media duration and playback progress.
        return client.children(server, season.id)
            .filter { it.kind == "episode" }
            .map {
                client.item(server, it.id).copy(
                    seriesTitle = show.title,
                    seasonTitle = season.title,
                )
            }
    }

    suspend fun item(serverUrl: String, summary: LoomItem): LoomItem =
        client.item(ServerAddress.parse(serverUrl), summary.id).withContextFrom(summary)

    suspend fun artworkOptions(
        serverUrl: String,
        itemId: Long,
        kind: ArtworkKind,
    ): List<ArtworkOption> = client.artworkOptions(ServerAddress.parse(serverUrl), itemId, kind)

    suspend fun selectArtwork(
        serverUrl: String,
        item: LoomItem,
        kind: ArtworkKind,
        option: ArtworkOption,
    ): LoomItem {
        val server = ServerAddress.parse(serverUrl)
        client.selectArtwork(server, item.id, kind, option)
        return client.item(server, item.id).withContextFrom(item)
    }

    suspend fun resetArtwork(
        serverUrl: String,
        item: LoomItem,
        kind: ArtworkKind,
    ): LoomItem {
        val server = ServerAddress.parse(serverUrl)
        client.resetArtwork(server, item.id, kind)
        return client.item(server, item.id).withContextFrom(item)
    }

    suspend fun nextEpisode(serverUrl: String, current: LoomItem): LoomItem? {
        if (current.kind != "episode" || current.parentId <= 0) return null
        val server = ServerAddress.parse(serverUrl)
        val episodes = client.children(server, current.parentId)
            .filter { it.kind == "episode" }
        val currentIndex = episodes.indexOfFirst { it.id == current.id }
        if (currentIndex < 0) return null
        val next = episodes.getOrNull(currentIndex + 1) ?: return null
        return client.item(server, next.id).copy(
            seriesTitle = current.seriesTitle,
            seasonTitle = current.seasonTitle,
        )
    }

    suspend fun preparePlayback(serverUrl: String, summary: LoomItem): PreparedPlayback {
        val server = ServerAddress.parse(serverUrl)
        val item = client.item(server, summary.id).withContextFrom(summary)
        val playback = client.playback(server, item.id)
        return PreparedPlayback(
            itemId = item.id,
            title = item.title,
            contextTitle = item.episodeContext(),
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

    private fun LoomItem.withContextFrom(summary: LoomItem): LoomItem = copy(
        seriesTitle = summary.seriesTitle.ifBlank { seriesTitle },
        seasonTitle = summary.seasonTitle.ifBlank { seasonTitle },
    )
}

internal suspend fun addEpisodeContext(
    items: List<LoomItem>,
    itemById: suspend (Long) -> LoomItem?,
): List<LoomItem> {
    val parents = mutableMapOf<Long, LoomItem?>()

    suspend fun parent(id: Long): LoomItem? {
        if (id <= 0) return null
        if (parents.containsKey(id)) return parents[id]
        val item = itemById(id)
        parents[id] = item
        return item
    }

    return items.map { item ->
        if (item.kind != "episode") return@map item
        val season = parent(item.parentId) ?: return@map item
        val show = parent(season.parentId) ?: return@map item
        item.copy(
            seriesTitle = show.title,
            seasonTitle = season.title,
        )
    }
}
