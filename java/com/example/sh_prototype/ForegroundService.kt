package com.example.sh_prototype

// ForegroundService.kt
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class ForegroundService : Service() {

    private var mediaPlayer: MediaPlayer? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        playAlarmSound()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        // Release the mediaPlayer
        mediaPlayer?.release()
    }

    private fun createNotification(): Notification {
        // Create an intent for the notification to launch when tapped
        val notificationIntent = Intent(this, NavBar::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        // Build the notification
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Alarm Notification")
            .setContentText("Your alarm is active.")
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentIntent(pendingIntent)
            .build()

        return notification
    }

    private fun playAlarmSound() {
        // Initialize the mediaPlayer if not already initialized
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(this, R.raw.alarm_sound)
        }

        // Start playing the alarm sound
        mediaPlayer?.start()
    }

    companion object {
        private const val NOTIFICATION_ID = 1 // Replace with your desired notification ID
        private const val CHANNEL_ID = "YourChannelId"
        private const val TAG = "ForegroundService"
    }
}
