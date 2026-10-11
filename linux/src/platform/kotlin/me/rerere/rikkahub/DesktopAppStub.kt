package me.rerere.rikkahub


/**
 * 这几个通知渠道 ID 在 Android 端声明于 RikkaHubApp（Application 子类），
 * ChatNotificationManager / MediaCreationService 引用它们。桌面端不启用 Application 壳，
 * 所以常量放在这里，名字与值都保持和上游一致。
 */
const val CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID = "chat_completed"
const val CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID = "chat_live_update"
const val WEB_SERVER_NOTIFICATION_CHANNEL_ID = "web_server"
const val MEDIA_CREATION_NOTIFICATION_CHANNEL_ID = "media_creation"
