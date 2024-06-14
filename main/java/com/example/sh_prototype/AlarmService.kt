package com.example.sh_prototype

import android.app.*
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class AlarmService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var databaseLightRef: DatabaseReference
    private lateinit var databaseSpeakersRef: DatabaseReference

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        createNotificationChannel()
        initMediaPlayer()
        Log.d("AlarmService", "AlarmServiceStarted")
    }

    private fun initMediaPlayer() {
        mediaPlayer = MediaPlayer.create(this, R.raw.alarmsound)
        mediaPlayer?.isLooping = true
        mediaPlayer?.setOnErrorListener { mp, what, extra ->
            Log.e("MediaPlayer", "Error occurred in MediaPlayer: What: $what Extra: $extra")
            mediaPlayer?.reset()
            mediaPlayer?.release()
            mediaPlayer = null
            true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        Log.d("AlarmService","On Start Command Intialized")
        startForeground(NOTIFICATION_ID, notification)
        when (intent?.action) {
            STOP_ACTION -> {
                stopAlarm()

            }
            else -> {
                playAlarmSound()
            }
        }

        return START_STICKY
    }

    private fun playAlarmSound() {
        if (mediaPlayer == null) {
            initMediaPlayer()
        }
        mediaPlayer?.start()
    }
    private fun stopAlarm() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        stopSelf()

        val intent = Intent("com.example.sh_prototype.ALARM_STOPPED")
        sendBroadcast(intent)

        val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserUid != null) {
            databaseLightRef = FirebaseDatabase.getInstance().getReference("Users").child(currentUserUid).child("DeviceStatus").child("Light")
            databaseLightRef.setValue(5)
            databaseSpeakersRef = FirebaseDatabase.getInstance().getReference("Users").child(currentUserUid).child("DeviceStatus").child("Speakers")
            databaseSpeakersRef.setValue(0)
        } else {
            Log.e("AlarmService", "Firebase update failed: User is not logged in")
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, AlarmService::class.java).apply {
            action = STOP_ACTION
        }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Alarm Notification")
            .setContentText("Your Alarm is Active")
            .setSmallIcon(R.drawable.ic_notification_icon)
            .addAction(R.drawable.ic_notification_icon, "Stop", stopPendingIntent)
            .build().also {
            }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "AlarmChannel"
        const val STOP_ACTION = "com.example.sh_prototype.STOP_ALARM"
    }
}