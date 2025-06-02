package com.parker.hotkey.data.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.parker.hotkey.R
import com.parker.hotkey.MainActivity
import com.parker.hotkey.utils.event.NoticeUpdateEvent
import org.greenrobot.eventbus.EventBus
import timber.log.Timber

class NoticeMessagingService : FirebaseMessagingService() {
    
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.d("새 FCM 토큰: $token")
        // 토큰 서버 전송 또는 저장
    }
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        remoteMessage.data.let { data ->
            val title = data["title"] ?: return
            val content = data["content"] ?: return
            val noticeId = data["noticeId"] ?: return
            
            // 알림 표시
            showNotification(title, content, noticeId)
            
            // 앱 내 이벤트 발생 (EventBus 사용)
            EventBus.getDefault().post(NoticeUpdateEvent(noticeId))
        }
    }
    
    private fun showNotification(title: String, content: String, noticeId: String) {
        // 알림 생성 및 표시
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // 알림 채널 생성 (Android 8.0 이상)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "notice_channel",
                "공지사항",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }
        
        // 알림 생성
        val notificationBuilder = NotificationCompat.Builder(this, "notice_channel")
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
        
        // 클릭 시 액티비티 열기
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("noticeId", noticeId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        notificationBuilder.setContentIntent(pendingIntent)
        
        // 알림 표시
        notificationManager.notify(noticeId.hashCode(), notificationBuilder.build())
    }
} 