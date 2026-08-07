package xyz.five82.takeup.data

import java.io.IOException

enum class DownloadResult { Started, NotEnoughSpace, Unavailable }

internal class LoomRepository(
    private val preferences: ServerPreferences,
    private val client: LoomClient = LoomClient(),
    private val downloads: DownloadStore? = null,
    private val offlineArtwork: OfflineArtwork? = null,
    private val offlineProgress: OfflineProgressStore? = null,
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
        val nextUp = client.nextUp(server)
        val recentlyAdded = client.recentlyAdded(server)
        val contextualItems = addEpisodeContext(
            items = (continueWatching + nextUp + recentlyAdded).distinctBy { it.id },
            itemById = { id -> runCatching { client.item(server, id) }.getOrNull() },
        ).associateBy { it.id }
        return HomeContent(
            continueWatching = continueWatching.map { contextualItems[it.id] ?: it },
            nextUp = nextUp.map { contextualItems[it.id] ?: it },
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

    // Listings carry playback progress, so the season draws its watched markers
    // and resume bars from one request rather than one per episode. They still
    // omit media duration; the details screen fetches the full item and is where
    // an episode's runtime is shown.
    suspend fun episodes(
        serverUrl: String,
        show: LoomItem,
        season: LoomItem,
    ): List<LoomItem> = client.children(ServerAddress.parse(serverUrl), season.id)
        .filter { it.kind == "episode" }
        .map { it.copy(seriesTitle = show.title, seasonTitle = season.title) }

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
        refreshOfflineArtwork(serverUrl, item.id)
        return client.item(server, item.id).withContextFrom(item)
    }

    suspend fun resetArtwork(
        serverUrl: String,
        item: LoomItem,
        kind: ArtworkKind,
    ): LoomItem {
        val server = ServerAddress.parse(serverUrl)
        client.resetArtwork(server, item.id, kind)
        refreshOfflineArtwork(serverUrl, item.id)
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
        val downloaded = downloads?.entry(summary.id)
        return try {
            val item = client.item(server, summary.id).withContextFrom(summary)
            val playback = client.playback(server, item.id)
            PreparedPlayback(
                itemId = item.id,
                title = item.title,
                contextTitle = item.episodeContext(),
                streamUrl = resolveStreamUrl(
                    downloaded = downloaded,
                    playbackTag = playback.tag,
                    resolvedUrl = server.stream(playback.streamPath).toString(),
                ),
                durationMs = playback.durationMs,
                resumePositionMs = item.progress?.resumePositionMs ?: 0L,
                container = playback.container,
            )
        } catch (error: IOException) {
            // Loom is unreachable. A completed download is entirely self-contained,
            // so play it from the snapshot rather than surfacing a connection error.
            val entry = downloaded?.takeIf { it.state == DownloadState.Completed } ?: throw error
            PreparedPlayback(
                itemId = entry.item.id,
                title = entry.item.title,
                contextTitle = entry.item.withContextFrom(summary).episodeContext(),
                streamUrl = entry.uri,
                durationMs = entry.item.mediaDurationMs,
                resumePositionMs = offlineResumePositionMs(entry.item),
                container = "",
            )
        }
    }

    private fun offlineResumePositionMs(item: LoomItem): Long =
        offlineProgress?.pending(item.id)?.positionMs ?: item.progress?.resumePositionMs ?: 0L

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

    /**
     * Captures the item response as the download's offline snapshot, so browsing and
     * playing it later needs nothing from Loom.
     */
    suspend fun startDownload(serverUrl: String, summary: LoomItem): DownloadResult {
        val store = downloads ?: return DownloadResult.Unavailable
        val server = ServerAddress.parse(serverUrl)
        val itemJson = client.itemJson(server, summary.id)
        val item = LoomJson.item(itemJson).withContextFrom(summary)
        val playback = client.playback(server, summary.id)
        if (!hasRoomFor(playback.sizeBytes, store.usableSpaceBytes())) {
            return DownloadResult.NotEnoughSpace
        }
        store.enqueue(
            itemId = summary.id,
            streamUrl = server.stream(playback.streamPath).toString(),
            itemJson = itemJson,
        )
        offlineArtwork?.save(serverUrl, item)
        return DownloadResult.Started
    }

    /** Best-effort catch-up for progress saved while Loom was unreachable. */
    suspend fun flushPendingProgress(serverUrl: String) {
        val store = offlineProgress ?: return
        val pending = store.all()
        if (pending.isEmpty()) return
        val server = ServerAddress.parse(serverUrl)
        pending.forEach { (itemId, progress) ->
            val sent = runCatching {
                client.saveProgress(server, itemId, progress.positionMs, progress.durationMs)
            }
            if (sent.isSuccess || sent.exceptionOrNull() is LoomHttpException) {
                store.clear(itemId)
            }
        }
    }

    /** Keeps a downloaded item's offline artwork in step with an artwork change. */
    private suspend fun refreshOfflineArtwork(serverUrl: String, itemId: Long) {
        if (downloads?.entry(itemId) == null) return
        val item = runCatching {
            client.item(ServerAddress.parse(serverUrl), itemId)
        }.getOrNull() ?: return
        offlineArtwork?.save(serverUrl, item)
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
