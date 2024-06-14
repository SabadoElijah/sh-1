package com.example.sh_prototype

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.SharedPreferences
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequestBuilder
import java.util.concurrent.TimeUnit
import com.google.android.material.snackbar.Snackbar
import java.util.Calendar
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase


interface AlarmCallback {
    fun onAlarmStopped()
}

class Alarm : Fragment() {
    private lateinit var sharedPreferences: SharedPreferences
    companion object {
        const val ALARM_REQUEST_CODE = 1234  // Example request code
    }
    private lateinit var setAlarmButton: Button
    private lateinit var timeSetTextView: TextView
    private lateinit var stopAlarmButton: Button
    private val alarmHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private lateinit var alarmRunnable: Runnable
    private var alarmViewModel: AlarmViewModel? = null
    private lateinit var updateTextReceiver: BroadcastReceiver

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_alarm, container, false)
        sharedPreferences = requireActivity().getSharedPreferences("sharedPref", Context.MODE_PRIVATE)
        initializeUI(view)
        alarmRunnable = Runnable { checkAndUpdateAlarmText() }
        alarmHandler.post(alarmRunnable)
        setupBroadcastReceiver()
        restoreAlarmTime()
        return view
    }
    private fun restoreAlarmTime() {
        val timeSet = sharedPreferences.getString("alarmTime", null)
        timeSet?.let {
            timeSetTextView.text = it
        }
    }

    private fun initializeUI(view: View) {
        stopAlarmButton = view.findViewById(R.id.stopAlarmButton)
        stopAlarmButton.setOnClickListener { stopAlarm() }
        setAlarmButton = view.findViewById(R.id.setAlarmButton)
        setAlarmButton.setOnClickListener { showTimePickerDialog() }
        timeSetTextView = view.findViewById(R.id.TimeSet)
        alarmViewModel = ViewModelProvider(requireActivity()).get(AlarmViewModel::class.java)
    }

    private fun setupBroadcastReceiver() {
        updateTextReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                timeSetTextView.text = intent?.getStringExtra("TEXT") ?: "Set an alarm"
            }
        }
    }

    override fun onStart() {
        super.onStart()

        val filter = IntentFilter("UPDATE_UI")
        requireActivity().registerReceiver(updateTextReceiver, filter)
        IntentFilter().apply {
            addAction("com.example.sh_prototype.ALARM_STOPPED")
            addAction("UPDATE_UI")  // If you're using this for other updates
        }.also { filter ->
            requireActivity().registerReceiver(alarmUpdateReceiver, filter)
        }
    }
    private val alarmUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.sh_prototype.ALARM_STOPPED") {
                timeSetTextView.text = "Set Alarm Time"
            } else {
                timeSetTextView.text = intent?.getStringExtra("TEXT") ?: "Set an alarm"
            }
        }
    }
    override fun onStop() {
        super.onStop()
        requireActivity().unregisterReceiver(updateTextReceiver)
        requireActivity().unregisterReceiver(alarmUpdateReceiver)
    }
    private fun showTimePickerDialog() {
        val calendar = Calendar.getInstance()
        TimePickerDialog(requireContext(), { _, hour, minute ->
            val futureTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
            }
            val currentTime = System.currentTimeMillis()

            if (futureTime.timeInMillis - currentTime < 3 * 60 * 1000) {
                showTimeError()
            } else {
                alarmViewModel?.let {
                    it.isAlarmSet = true
                    it.alarmHour = hour
                    it.alarmMinute = minute
                    updateTimeSetTextView(hour, minute)
                    setAlarm(hour, minute)
                }
            }
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
    }

    private fun showTimeError() {
        Snackbar.make(requireView(), "Alarm must be set for at least 3 minutes in the future.", Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.black))
            .setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            .show()
    }
    private fun updateTimeSetTextView(hour: Int, minute: Int) {
        val formattedText = "Alarm set for: <b>${String.format("%02d:%02d", hour, minute)}</b>"
        timeSetTextView.text = android.text.Html.fromHtml(formattedText, android.text.Html.FROM_HTML_MODE_LEGACY)
        // Save the formatted text to SharedPreferences
        sharedPreferences.edit().putString("alarmTime", timeSetTextView.text.toString()).apply()
    }
    private fun setAlarm(hour: Int, minute: Int) {
        val intent = Intent(requireContext(), AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(requireContext(), ALARM_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
        }

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)

        val timeBeforeLast3Minutes = calendar.timeInMillis - 3 * 60 * 1000
        if (System.currentTimeMillis() < timeBeforeLast3Minutes) {
            val delay = timeBeforeLast3Minutes - System.currentTimeMillis()
            val preAlarmRequest = OneTimeWorkRequestBuilder<PreAlarmWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(requireContext()).enqueue(preAlarmRequest)
        } else {
            updateDeviceStatusInFirebase()
        }
    }


    private fun stopAlarm() {
        val intent = Intent(requireContext(), AlarmService::class.java)
        intent.action = AlarmService.STOP_ACTION
        requireContext().startService(intent)
    }
    private fun checkAndUpdateAlarmText() {
        alarmViewModel?.let { viewModel ->
            if (viewModel.isAlarmSet) {
                val alarmTime = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, viewModel.alarmHour)
                    set(Calendar.MINUTE, viewModel.alarmMinute)
                    set(Calendar.SECOND, 0)
                }
                val currentTime = System.currentTimeMillis()
                val alarmTimeMillis = alarmTime.timeInMillis

                if (currentTime >= alarmTimeMillis) {
                    showAlarmNotification()
                    timeSetTextView.text = "Set an alarm"
                    viewModel.isAlarmSet = false
                    viewModel.firebaseUpdated = false  // Reset flag when alarm is over
                } else {
                    val delayMillis = alarmTimeMillis - currentTime
                    if (currentTime >= alarmTimeMillis - 3 * 60 * 1000 && currentTime < alarmTimeMillis && !viewModel.firebaseUpdated)
                    {
                        updateDeviceStatusInFirebase()
                        viewModel.firebaseUpdated = true  // Set flag to prevent further updates
                    }
                    alarmHandler.postDelayed(alarmRunnable, Math.max(1000, delayMillis))
                }
            }
        }
    }

    private fun updateDeviceStatusInFirebase() {
        FirebaseAuth.getInstance().currentUser?.uid?.let { currentUserUid ->
            val userDeviceStatusRef = FirebaseDatabase.getInstance().getReference("Users/$currentUserUid/DeviceStatus")
            userDeviceStatusRef.child("Light").setValue(4)
        } ?: Log.e("FirebaseUpdate", "User is not logged in or UID is null")
    }
    private fun showAlarmNotification() {
        val rootView = requireActivity().findViewById<View>(android.R.id.content)
        val snackbar = Snackbar.make(rootView, "Alarm Triggered", Snackbar.LENGTH_LONG)
        snackbar.setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.black))
        snackbar.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
        snackbar.show()
    }
}
