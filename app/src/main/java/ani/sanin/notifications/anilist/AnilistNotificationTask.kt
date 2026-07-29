package ani.sanin.notifications.anilist

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ani.sanin.App
import ani.sanin.MainActivity
import ani.sanin.R
import ani.sanin.connections.anilist.Anilist
import ani.sanin.connections.anilist.api.NotificationType
import ani.sanin.notifications.Task
import ani.sanin.notifications.subscription.NotificationPopupActivity
import ani.sanin.profile.activity.ActivityItemBuilder
import ani.sanin.settings.saving.PrefManager
import ani.sanin.settings.saving.PrefName
import ani.sanin.util.Logger
import ani.sanin.util.TvKeyboardUtil
import eu.kanade.tachiyomi.data.notification.Notifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AnilistNotificationTask : Task {
    override suspend fun execute(context: Context): Boolean {
        try {
            withContext(Dispatchers.IO) {
                PrefManager.init(context) //make sure prefs are initialized
                val userId = PrefManager.getVal<String>(PrefName.AnilistUserId)
                if (userId.isNotEmpty()) {
                    Anilist.getSavedToken()
                    val res = Anilist.query.getNotifications(
                        userId.toInt(),
                        resetNotification = false
                    )
                    val unreadNotificationCount = res?.data?.user?.unreadNotificationCount ?: 0
                    if (unreadNotificationCount > 0) {
                        val unreadNotifications =
                            res?.data?.page?.notifications?.sortedBy { it.id }
                                ?.takeLast(unreadNotificationCount)
                        val lastId = PrefManager.getVal<Int>(PrefName.LastAnilistNotificationId)
                        val newNotifications = unreadNotifications?.filter { it.id > lastId }
                        val filteredTypes =
                            PrefManager.getVal<Set<String>>(PrefName.AnilistFilteredTypes)
                        val mediaSectionTypes = setOf(
                            NotificationType.AIRING.value,
                            NotificationType.MEDIA_MERGE.value,
                            NotificationType.MEDIA_DELETION.value,
                            NotificationType.MEDIA_DATA_CHANGE.value,
                            NotificationType.RELATED_MEDIA_ADDITION.value
                        )

                        val notificationEpisodeAiring = PrefManager.getVal<Boolean>(PrefName.NotificationEpisodeAiring)
                        val notificationNewComment = PrefManager.getVal<Boolean>(PrefName.NotificationNewComment)
                        val notificationCompletedEpisode = PrefManager.getVal<Boolean>(PrefName.NotificationCompletedEpisode)
                        val notificationCompletedAnime = PrefManager.getVal<Boolean>(PrefName.NotificationCompletedAnime)
                        val notificationNewFollower = PrefManager.getVal<Boolean>(PrefName.NotificationNewFollower)

                        var userCount = 0
                        var mediaCount = 0

                        newNotifications?.forEach {
                            if (!filteredTypes.contains(it.notificationType)) {
                                val allowed = when (it.notificationType) {
                                    NotificationType.AIRING.value -> notificationEpisodeAiring
                                    NotificationType.ACTIVITY_REPLY.value,
                                    NotificationType.ACTIVITY_MESSAGE.value,
                                    NotificationType.THREAD_COMMENT_REPLY.value,
                                    NotificationType.THREAD_SUBSCRIBED.value,
                                    NotificationType.THREAD_COMMENT_MENTION.value,
                                    NotificationType.ACTIVITY_MENTION.value,
                                    NotificationType.ACTIVITY_REPLY_SUBSCRIBED.value -> notificationNewComment
                                    NotificationType.MEDIA_DATA_CHANGE.value -> notificationCompletedEpisode
                                    NotificationType.MEDIA_MERGE.value,
                                    NotificationType.MEDIA_DELETION.value,
                                    NotificationType.RELATED_MEDIA_ADDITION.value -> notificationCompletedAnime
                                    NotificationType.FOLLOWING.value -> notificationNewFollower
                                    else -> true
                                }
                                if (!allowed) return@forEach

                                val content = ActivityItemBuilder.getContent(it)
                                val notification = createNotification(context, content, it.id)
                                if (ActivityCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    NotificationManagerCompat.from(context)
                                        .notify(
                                            Notifications.CHANNEL_ANILIST,
                                            System.currentTimeMillis().toInt(),
                                            notification
                                        )
                                }
                                if (PrefManager.getVal<Boolean>(PrefName.NotificationPopup) && !TvKeyboardUtil.isTv(context)) {
                                    val coverUrl = it.media?.coverImage?.large ?: it.image
                                    App.currentActivity()?.let { activity ->
                                        val popupIntent = Intent(context, NotificationPopupActivity::class.java).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
                                            putExtra("title", content)
                                            putExtra("text", it.context?.take(120) ?: content.take(120))
                                            putExtra("coverUrl", coverUrl)
                                        }
                                        context.startActivity(popupIntent)
                                    }
                                }
                                // Track counts per section
                                if (it.notificationType in mediaSectionTypes) {
                                    mediaCount++
                                } else {
                                    userCount++
                                }
                            }
                        }
                        
                        // Update per-section counts
                        if (userCount > 0) {
                            val currentUserCount = PrefManager.getVal<Int>(PrefName.UnreadUserNotifications)
                            PrefManager.setVal(PrefName.UnreadUserNotifications, currentUserCount + userCount)
                        }
                        if (mediaCount > 0) {
                            val currentMediaCount = PrefManager.getVal<Int>(PrefName.UnreadMediaNotifications)
                            PrefManager.setVal(PrefName.UnreadMediaNotifications, currentMediaCount + mediaCount)
                        }
                        
                        if (newNotifications?.isNotEmpty() == true) {
                            PrefManager.setVal(
                                PrefName.LastAnilistNotificationId,
                                newNotifications.last().id
                            )
                        }
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Logger.log("AnilistNotificationTask: ${e.message}")
            Logger.log(e)
            return false
        }
    }

    private fun createNotification(
        context: Context,
        content: String,
        notificationId: Int? = null
    ): android.app.Notification {
        val title = "New Anilist Notification"
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("FRAGMENT_TO_LOAD", "NOTIFICATIONS")
            if (notificationId != null) {
                Logger.log("notificationId: $notificationId")
                putExtra("activityId", notificationId)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId ?: 0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, Notifications.CHANNEL_ANILIST)
            .setSmallIcon(R.drawable.notification_icon)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
    }

}