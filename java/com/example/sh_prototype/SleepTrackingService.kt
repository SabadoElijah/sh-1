package com.example.sh_prototype

import java.util.*
import android.os.Handler
import android.os.Looper
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import android.content.Context
import retrofit2.Retrofit
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.converter.gson.GsonConverterFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import android.util.Log
import com.google.firebase.database.DatabaseReference
import com.google.firebase.FirebaseApp



class SleepTrackingService : Service() {

    private lateinit var databaseRef: DatabaseReference
    private var bpmListener: ValueEventListener? = null
    private var lastSleepState: Boolean? = null
    private var lastStateChangeTime: Long = 0
    private val DEBOUNCE_TIME = 3000 // Debounce time in milliseconds
    private lateinit var soundSensorRef: DatabaseReference
    private lateinit var deviceStatusRef: DatabaseReference
    private var soundDetectionActive: Boolean = true
    private var soundSensorValueEventListener: ValueEventListener? = null


    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserUid != null) {
            databaseRef = FirebaseDatabase.getInstance().getReference("Users/$currentUserUid/MAX30102/BPM")
            soundSensorRef = FirebaseDatabase.getInstance().getReference("Users/$currentUserUid/DeviceData/SoundSensor")
            deviceStatusRef = FirebaseDatabase.getInstance().getReference("Users/$currentUserUid/DeviceStatus")
            // Setup listeners for both BPM and Sound Sensor
            setupBpmListener()
            setupSoundListener() // Ensure this is called here
        }
    }
    private val NOTIFICATION_CHANNEL_ID = "com.example.sh_prototype_service_channel"


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("App is running in background")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        startForeground(1, notification)
        setupBpmListener()
        return START_STICKY
    }

    private fun setupRetrofit(): Retrofit {

        return Retrofit.Builder()
            .baseUrl("https://sleep-haven-fastapi.onrender.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }


    private fun createNotificationChannel() {
        val channelName = "Background Service"
        val chan = NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_MIN)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(chan)
    }

    private fun setupBpmListener() {
        val bpmReceivedTimes = HashMap<Float, Long>()
        val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid
        val deviceStatusRef = FirebaseDatabase.getInstance().getReference("Users/$currentUserUid/DeviceStatus")
        var lastBpmValue: Float? = null // Track the last BPM value received

        val timer = Timer()
        val task = object : TimerTask() {
            override fun run() {
                if (lastBpmValue != null) {
                    // Check if the current time - lastBpmReceivedTime > 20000 (20 seconds)
                    val currentTime = System.currentTimeMillis()
                    val lastBpmReceivedTime = lastBpmValue?.let { bpmReceivedTimes[it] }
                    if (lastBpmReceivedTime != null && currentTime - lastBpmReceivedTime > 60000) {
                        // If the time difference exceeds 20 seconds, turn off MAX30102
                        deviceStatusRef.child("MAX30102").setValue(0)
                        cancel()
                    }
                }
            }
        }

        // Schedule the task to run every second
        timer.schedule(task, 0, 1000)
        bpmListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var latestBPM: Float? = null
                snapshot.children.forEach { child ->
                    val bpmValue = when (val value = child.value) {
                        is Long -> value.toFloat()
                        is Double -> value.toFloat()
                        else -> null
                    }

                    if (bpmValue != null && bpmValue != -999f && bpmValue in 50f..91f) {
                        latestBPM = bpmValue
                    }
                }

                latestBPM?.let { safeLatestBPM ->
                    deviceStatusRef.child("MAX30102").setValue(1)
                    sendBpmToApi(safeLatestBPM)
                    lastBpmValue = safeLatestBPM
                    bpmReceivedTimes[safeLatestBPM] = System.currentTimeMillis()
                } ?: Log.e("SleepTrackingService", "No valid BPM data available within the range 50 to 91.")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SleepTrackingService", "Failed to read BPM from Firebase: ${error.message}")
            }
        }
        databaseRef.addValueEventListener(bpmListener!!)


    }
    private fun turnOffHumidifierAndLight() {
        val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid
        val deviceStatusRef = FirebaseDatabase.getInstance().getReference("Users/$currentUserUid/DeviceStatus")
        deviceStatusRef.child("Humidifier").setValue(0)
            .addOnSuccessListener {
            }
            .addOnFailureListener { exception ->
                Log.e("SleepTrackingService", "Failed to update Humidifier status in Firebase", exception)
                // Handle the failure, e.g., retry or inform the user
            }
        deviceStatusRef.child("Light").setValue(5)
            .addOnSuccessListener {
            }
            .addOnFailureListener { exception ->
                Log.e("SleepTrackingService", "Failed to update Light status in Firebase", exception)
                // Handle the failure, e.g., retry or inform the user
            }
    }

    private fun sendBpmToApi(bpm: Float) {
        val service = setupRetrofit().create(SleepStateService::class.java)
        val call = service.getSleepState(BpmData(bpm))

        call.enqueue(object : Callback<SleepStateResponse> {
            override fun onResponse(call: Call<SleepStateResponse>, response: Response<SleepStateResponse>) {
                if (response.isSuccessful) {
                    response.body()?.let {
                        broadcastSleepState(it.predicted_state == 0) // Assuming '0' means asleep
                    }
                } else {
                    Log.e("SleepTrackingService", "API Response Failed: ${response.code()} - ${response.errorBody()?.string()}")
                }
            }

            override fun onFailure(call: Call<SleepStateResponse>, t: Throwable) {
                Log.e("SleepTrackingService", "API Call Failure: ${t.localizedMessage}")
            }
        })
    }
    private fun setupSoundListener() {
        Log.d("setupSoundListener","Sound Listener Successfully Initialized")
        soundSensorValueEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid

                // Check for '1' (SoundSensor device status indicating it's turned on)
                val soundSensorStatus = snapshot.getValue(Int::class.java)
                if (soundSensorStatus == 1) {
                    val soundSensorDataRef = FirebaseDatabase.getInstance().getReference("Users/$currentUserUid/DeviceData/SoundSensor")
                    soundSensorDataRef.addValueEventListener(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            // Read SoundSensor data
                            val soundSensorData = snapshot.getValue(Int::class.java)
                            if (soundSensorData != null) {
                            } else {

                            }
                            Log.e("SleepTrackingService", "Sound Sensor is Off.")
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.e("SleepTrackingService", "SoundSensor ValueEventListener cancelled, error: ${error.message}")
                        }
                    })

                    // Activate sound detection only if SoundSensor is turned on
                    activateSoundListener()
                } else {
                    // Deactivate sound detection if SoundSensor is turned off
                    deactivateSoundListener()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SleepTrackingService", "Sound sensor ValueEventListener cancelled, error: ${error.message}")
            }
        }
        soundSensorRef.addValueEventListener(soundSensorValueEventListener!!)
    }

    private fun activateSoundListener() {
        soundSensorRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val soundDetected = (snapshot.getValue(Int::class.java) ?: 1) == 0
                if (soundDetected && lastSleepState == true && soundDetectionActive) {
                    soundDetectionActive = false  // Disables further sound detection
                    triggerMusicPlayback()

                    // Schedule the music to stop after 5 minutes
                    Handler(Looper.getMainLooper()).postDelayed({
                        stopMusicPlayback() // Method to stop the music
                        soundDetectionActive = true  // Re-enable sound detection
                    }, 60000) // 1 minute delay = 60000 5 minutes delay = 300000
                } else {
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SleepTrackingService", "Sound sensor ValueEventListener cancelled, error: ${error.message}")
            }
        })
    }
    private fun deactivateSoundListener() {
        // Check if the listener is not null before removing it
        soundSensorValueEventListener?.let {
            // Remove the ValueEventListener from the soundSensorRef
            soundSensorRef.removeEventListener(it)
        }
    }
    private fun triggerMusicPlayback() {
        val playIntent = Intent(this, MusicService::class.java)
        playIntent.putExtra("ACTION", "PLAY_MUSIC")
        startService(playIntent)
    }
    private fun stopMusicPlayback() {
        val stopIntent = Intent(this, MusicService::class.java)
        stopIntent.putExtra("ACTION", "PAUSE_MUSIC")
        startService(stopIntent)
    }
    private fun broadcastSleepState(isAsleep: Boolean) {
        val currentTime = System.currentTimeMillis()
        if (lastSleepState == null || lastSleepState != isAsleep) {
            if ((currentTime - lastStateChangeTime) > DEBOUNCE_TIME) {
                lastStateChangeTime = currentTime
                lastSleepState = isAsleep

                // Broadcast sleep state
                val intent = Intent("SLEEP_STATE_UPDATE")
                intent.putExtra("IS_ASLEEP", isAsleep)
                sendBroadcast(intent)

                // Update device status on Firebase if asleep
                if (isAsleep) {
                    // Set SoundSensor to 1 to turn it on
                    deviceStatusRef.child("SoundSensor").setValue(1)
                        .addOnSuccessListener {
                            turnOffHumidifierAndLight() // Turn off humidifier and light
                        }
                        .addOnFailureListener { exception ->
                            Log.e("SleepTrackingService", "Failed to update SoundSensor status in Firebase", exception)
                            // Handle the failure, e.g., retry or inform the user
                        }
                } else {
                    // Set SoundSensor to 0 to turn it off
                    deviceStatusRef.child("SoundSensor").setValue(0)
                        .addOnSuccessListener {
                        }
                        .addOnFailureListener { exception ->
                            Log.e("SleepTrackingService", "Failed to update SoundSensor status in Firebase", exception)
                            // Handle the failure, e.g., retry or inform the user
                        }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bpmListener != null) {
            databaseRef.removeEventListener(bpmListener!!)
        }
        if (soundSensorRef != null) {
            soundSensorRef.removeEventListener(bpmListener!!)
        }
    }




}
