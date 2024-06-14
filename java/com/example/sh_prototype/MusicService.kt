package com.example.sh_prototype

import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class MusicService : Service() {
    private var myBinder = MyBinder()
    var mediaPlayer: MediaPlayer? = null
    private lateinit var runnable: Runnable
    var isPlaying: Boolean = false

    override fun onBind(intent: Intent?): IBinder? {
        return myBinder
    }
    inner class MyBinder : Binder() {
        fun currentService(): MusicService {
            return this@MusicService
        }
    }

    override fun onCreate() {
        super.onCreate()
        initializeMediaPlayer()
    }

    private fun initializeMediaPlayer() {
        mediaPlayer = MediaPlayer.create(this, R.raw.whitenoise)
    }
    fun createMediaPlayer() {
        try {
            PlayerActivity.binding.tvSeekBarStart.text = formatDuration(mediaPlayer!!.currentPosition.toLong())
            PlayerActivity.binding.tvSeekBarEnd.text = formatDuration(mediaPlayer!!.duration.toLong())
            PlayerActivity.binding.seekBarPA.progress = 0
            PlayerActivity.binding.seekBarPA.max = mediaPlayer!!.duration
        } catch (e: Exception) {
            Log.e("MusicService", "Error in createMediaPlayer: ${e.message}")
            return
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.extras?.getString("ACTION")?.let { action ->
            when (action) {
                "PLAY_MUSIC" -> {
                    playMusic()
                }
                "PAUSE_MUSIC" -> {
                    pauseMusic()
                }
                else -> Log.e("MusicService", "onStartCommand: Unexpected action received: $action")
            }
        }
        return START_STICKY
    }
    fun playMusic() {
        if (mediaPlayer != null) {
            mediaPlayer?.start()
            isPlaying = true
            updateSpeakerStatus(true)
            broadcastUpdate("PLAYING")
        } else {
            Log.e("MusicService", "Failed to play music - MediaPlayer is null")
        }
    }

    fun pauseMusic() {
        if (mediaPlayer != null) {
            mediaPlayer?.pause()
            isPlaying = false
            updateSpeakerStatus(false)
            broadcastUpdate("PAUSED")
        } else {
            Log.e("MusicService", "Failed to pause music - MediaPlayer is null")
        }
    }

    private fun updateSpeakerStatus(isPlaying: Boolean) {
        val speakerStatus = if (isPlaying) 1 else 2
        val userUid = FirebaseAuth.getInstance().currentUser?.uid  // Replace with your logic to get the current user's UID

        if (userUid != null) {
            val ref = FirebaseDatabase.getInstance().getReference("Users/$userUid/DeviceStatus/Speakers")
            ref.setValue(speakerStatus)
                .addOnSuccessListener {
                }
                .addOnFailureListener {
                }
        } else {
            Log.e("PlayerActivity", "Failed to update speaker status - User UID is null")
        }
    }

    private fun broadcastUpdate(action: String) {
        val intent = Intent("com.example.sh_prototype.MUSIC_UPDATE")
        intent.putExtra("ACTION", action)
        sendBroadcast(intent)
    }
    // Add debug logs for seekBarSetup method
    fun seekBarSetup() {
        runnable = Runnable {
            val mediaPlayer = mediaPlayer ?: return@Runnable
            try {
                PlayerActivity.binding.tvSeekBarStart.text = formatDuration(mediaPlayer.currentPosition.toLong())
                PlayerActivity.binding.seekBarPA.progress = mediaPlayer.currentPosition
            } catch (e: IllegalStateException) {
                // Handle the IllegalStateException here
                Log.e("MusicService", "seekBarSetup: IllegalStateException: ${e.message}")
                e.printStackTrace()
            }
            Handler(Looper.getMainLooper()).postDelayed(runnable, 200)
        }
        Handler(Looper.getMainLooper()).postDelayed(runnable, 0)
    }


    override fun onDestroy() {
        mediaPlayer?.release() // Release media player resources
        mediaPlayer = null
        super.onDestroy()
    }
}
