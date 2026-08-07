@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package xyz.five82.takeup.data

import android.app.Notification
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import xyz.five82.takeup.R
import xyz.five82.takeup.TakeupApplication

internal class MediaDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.download_channel_name,
    0,
) {
    override fun getDownloadManager(): DownloadManager =
        (application as TakeupApplication).container.downloadStore.downloadManager

    // No Scheduler: resuming while the app is dead would need a JobService and a boot
    // receiver to save a LAN transfer that takes minutes. MainActivity resumes instead.
    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: List<Download>,
        notMetRequirements: Int,
    ): Notification = DownloadNotificationHelper(this, CHANNEL_ID).buildProgressNotification(
        this,
        R.drawable.ic_download,
        null,
        null,
        downloads,
        notMetRequirements,
    )

    internal companion object {
        const val FOREGROUND_NOTIFICATION_ID = 1
        const val CHANNEL_ID = "downloads"
    }
}
