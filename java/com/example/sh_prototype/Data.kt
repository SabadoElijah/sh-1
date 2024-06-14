package com.example.sh_prototype

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.sh_prototype.databinding.FragmentDataBinding
import com.google.firebase.database.*
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.firebase.database.DataSnapshot
import com.google.firebase.auth.FirebaseAuth
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.Response
import kotlinx.coroutines.*
import android.os.Build
import android.widget.Button


data class DataPoint(val timestamp: String, val y: Float)
data class TimeToSleep(val durationHours: Int, val durationMinutes: Int, val durationSeconds: Int)
data class BpmData(val BPM: Float)
data class SleepStateResponse(val predicted_state: Int)
data class SleepData(val entries: List<Entry>, val labels: List<String>)
class Data : Fragment(), CoroutineScope by MainScope() {

    private lateinit var binding: FragmentDataBinding
    private lateinit var database: DatabaseReference
    private lateinit var lineChart: LineChart
    private lateinit var lineChart2: LineChart
    private var firstAwakeTime: String? = null
    private var firstAsleepTime: String? = null
    private var lastPredictedState: String? = null
    private val bpmData = mutableListOf<DataPoint>()
    private var bpmListener: ValueEventListener? = null // Store the listener as a nullable property
    private val existingData = mutableMapOf<String, Any>()
    private var lastUpdateTimestamp: Long? = null
    private lateinit var sleepDataModal: SleepData
    private var consecutiveAwakeCount: Int = 0
    private var dataPushed: Boolean = false
    private var timeDurationHours = 1
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentDataBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val btnSleepLogs = view.findViewById<Button>(R.id.btnSleepLogs)
        btnSleepLogs.setOnClickListener {
            val dialogFragment = SleepLogsDialogFragment()
            dialogFragment.show(childFragmentManager, "SleepLogsDialogFragment")
            Log.d("onViewCreated", "Status of firstAwakeTime $firstAwakeTime")
            Log.d("onViewCreated", "Status of firstAsleepTime $firstAsleepTime")
        }


        lineChart = binding.lineChart
        lineChart2 = binding.lineChart2
        setupLineChart()
        setupSleepLineChart()
        setupTimeToSleepListener()
        setupDatabaseListeners()
        Intent(context, SleepTrackingService::class.java).also { intent ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context?.startForegroundService(intent)
            } else {
                context?.startService(intent)
            }
        }

    }
    private fun setupRetrofit(): Retrofit = Retrofit.Builder()
        .baseUrl("https://sleep-haven-fastapi.onrender.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private fun setupLineChart() {
        lineChart.apply {
            description.isEnabled = false
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            axisLeft.setDrawGridLines(true)
            axisRight.isEnabled = false
            setNoDataText("Data loading...")
            setNoDataTextColor(Color.parseColor("#6750a4"))
        }
        lineChart2.apply {
            description.isEnabled = false
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            axisLeft.setDrawGridLines(true)
            axisRight.isEnabled = false
            setNoDataText("Data loading...")
            setNoDataTextColor(Color.parseColor("#6750a4"))
        }
    }
    private fun setupDatabaseListeners() {
        val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserUid != null) {
            database = FirebaseDatabase.getInstance().getReference("Users/$currentUserUid/MAX30102/BPM")
            bpmListener = createBpmListener()
            database.addValueEventListener(bpmListener!!)
        }
    }
    private fun createBpmListener() = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val newBpmData = snapshot.children.mapNotNull {
                it.value.toString().toDoubleOrNull()?.let { bpm ->
                    DataPoint("", bpm.toFloat())  // Assuming timestamp isn't needed here
                }
            }

            if (newBpmData.isNotEmpty()) {
                bpmData.clear()
                bpmData.addAll(newBpmData)

                // Retrieve the last BPM value
                val latestBPM = newBpmData.last().y

                // Update the TextView with the most recent BPM value
                activity?.runOnUiThread {
                    activity?.findViewById<TextView>(R.id.rawData)?.text = "Current BPM: $latestBPM"
                }

                updateNewDataNode(database.child("NewData"), newBpmData)
                sendBpmToApi(latestBPM)
                updateLineChart(bpmData)
            } else {
                Log.e("DataFragment", "No data available in Firebase snapshot.")
            }
        }

        override fun onCancelled(error: DatabaseError) {
            Log.e("DataFragment", "Firebase Database Error: ${error.message}")
        }
    }
    private fun sendBpmToApi(bpmValue: Float) {
        val retrofit = setupRetrofit()
        val service = retrofit.create(SleepStateService::class.java)
        val call = service.getSleepState(BpmData(bpmValue))
        call.enqueue(object : retrofit2.Callback<SleepStateResponse> {
            override fun onResponse(call: Call<SleepStateResponse>, response: Response<SleepStateResponse>) {
                if (response.isSuccessful) {
                    response.body()?.let {
                        val stateText = if (it.predicted_state == 1) "Awake" else "Asleep"
                        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                        updateSleepState(stateText, currentTime)
                    }
                } else {
                    Log.e("DataFragment", "API Response Failed: ${response.code()} - ${response.errorBody()?.string()}")
                }
            }
            override fun onFailure(call: Call<SleepStateResponse>, t: Throwable) {
                Log.e("DataFragment", "API Call Failure: ${t.localizedMessage}")
            }
        })
    }
    private fun updateSleepState(newState: String, newTimestamp: String) {
        val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserUid != null) {
            val currentState = view?.findViewById<TextView>(R.id.personState)?.text.toString()
            logStateChange(newState, currentState,newTimestamp)
            if (currentState != newState) {
                view?.findViewById<TextView>(R.id.personState)?.text = newState
                lastPredictedState = newState
            }
            else if (newState == "Awake" && firstAwakeTime != null) {
                consecutiveAwakeCount++
                if(consecutiveAwakeCount >= 8 && dataPushed == true){
                    firstAwakeTime = null
                    firstAsleepTime = null
                    consecutiveAwakeCount = 0
                    dataPushed = false
                }
            }

        } else {
            Log.e("DataFragment", "User is not logged in")
        }
    }
    private fun logStateChange(newState: String, currentState: String, newTimestamp: String) {
        val timeThreshold = 3000 // 3 seconds threshold for debouncing
        val lastUpdateTime = lastUpdateTimestamp ?: 0
        val currentTime = System.currentTimeMillis()

        if ((currentTime - lastUpdateTime) < timeThreshold) {
            return
        }
        lastUpdateTimestamp = currentTime // Update the last update timestamp
        if (currentState == "Awake" && firstAwakeTime == null) {
            firstAwakeTime = newTimestamp
        } else if (newState == "Asleep" && firstAwakeTime != null && firstAsleepTime == null) {
            firstAsleepTime = newTimestamp
            calculateTimeToSleep(firstAwakeTime!!, firstAsleepTime!!)?.let { (date, timeToSleep) ->
                pushTimeToSleepToFirebase(FirebaseAuth.getInstance().currentUser!!.uid, date, timeToSleep)
            }
        }
    }
    private fun calculateTimeToSleep(asleepTime: String, awakeTime: String): Pair<Date, TimeToSleep>? {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val asleepDate = sdf.parse(asleepTime)
            val awakeDate = sdf.parse(awakeTime)
            val duration = awakeDate.time - asleepDate.time
            val timeToSleep = TimeToSleep((duration / (1000 * 60 * 60) % 24).toInt(), (duration / (1000 * 60) % 60).toInt(), (duration / 1000 % 60).toInt())
            Pair(asleepDate, timeToSleep)
        } catch (e: ParseException) {
            Log.e("DataFragment", "Error parsing sleep time data", e)
            null
        }
    }

    private fun pushTimeToSleepToFirebase(uid: String, date: Date, timeToSleep: TimeToSleep) {
        val timeToSleepRef = FirebaseDatabase.getInstance().getReference("Users").child(uid).child("TimeToSleepHistory")
        val totalSeconds = (timeToSleep.durationHours * 3600) + (timeToSleep.durationMinutes * 60) + timeToSleep.durationSeconds
        val formattedTime = String.format("%02d:%02d:%02d", totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val formattedDate = sdf.format(date)
        val data = "\n SOL: $formattedDate - $formattedTime \n FAT: $firstAwakeTime \n FST:  $firstAsleepTime"
        dataPushed = true
        timeToSleepRef.push().setValue(data).addOnSuccessListener {
        }.addOnFailureListener { e ->
            Log.e("DataFragment", "Error pushing time to sleep data to Firebase history", e)
        }
    }
    private fun updateNewDataNode(newDataRef: DatabaseReference, bpmData: List<DataPoint>) {
        newDataRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                existingData.clear() // Clear the existing data map to prepare for updates

                // Collect existing data safely, avoiding null keys
                snapshot.children.forEach { childSnapshot ->
                    val key = childSnapshot.key
                    val value = childSnapshot.value
                    if (key != null && value != null) {
                        existingData[key] = value
                    }
                }

                // Prepare updates for the NewData node
                val updates = mutableMapOf<String, Any>()
                val dateTimeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                bpmData.forEachIndexed { index, dataPoint ->
                    val value = dataPoint.y
                    val dateTime = dateTimeFormatter.format(Date())  // Format the current date and time

                    // Check if the index already exists in the existing data
                    val existingKey = index.toString()
                    if (existingData.containsKey(existingKey)) {
                        // If the index exists, retain the existing timestamp
                        val existingTimestamp = existingData[existingKey].toString().substringAfter(" --- ")
                        updates[existingKey] = "$value --- $existingTimestamp"
                    } else {
                        // If the index does not exist, update with the current timestamp
                        updates[existingKey] = "$value --- $dateTime"
                    }
                }

                // Update the NewData node with new or existing data
                newDataRef.updateChildren(updates).addOnSuccessListener {
                }.addOnFailureListener { e ->
                    Log.e("DataFragment", "Failed to update data: ${e.message}")
                }

                // Optionally, refresh the UI or charts after the update
                updateLineChart(bpmData)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("DataFragment", "Firebase Database Error: ${error.message}")
            }
        })
    }

    private fun setupSleepLineChart() {
        lineChart.apply {
            description.isEnabled = false
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            axisLeft.setDrawGridLines(true)
            axisRight.isEnabled = false
            setNoDataText("Data loading...")
            setNoDataTextColor(Color.parseColor("#6750a4"))
        }
    }
    private fun updateLineChart(bpmData: List<DataPoint>) {
        // Filter out data points that are not within the specified range
        val filteredData = bpmData.filter { it.y in 50f..100f }
        val first120Entries = filteredData.take(480)
        val entries = first120Entries.mapIndexed { index, data -> Entry(index.toFloat(), data.y) }
        val dataSet = LineDataSet(entries, "BPM Data").apply {
            color = Color.BLUE
            valueTextColor = Color.BLACK

            // Set custom x-axis labels
            lineChart.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String? {
                    val index = value.toInt().coerceIn(0, first120Entries.size - 1)
                    val existingTimestamp = existingData[index.toString()] as? String
                    if (existingTimestamp != null && existingTimestamp.contains(" --- ")) {
                        val datePart = existingTimestamp.split(" --- ")[1].split(" ")[0] // Extract date part
                        val timePart = existingTimestamp.split(" --- ")[1].split(" ")[1]
                        val parts = timePart.split(":")
                        if (parts.size == 3) {
                            val (hour, minute) = parts.take(2).map { it.toIntOrNull() }
                            if (hour != null && hour in 0..23 && minute != null && minute in 0..59) {
                                // Get the current time

                                // Parse the date and time from the existing timestamp
                                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                val existingDateTime = sdf.parse("$datePart $timePart")

                                // Check if the existing date and time are within the past 24 hours
                                if (existingDateTime != null ) {
                                    // Format and return the time if it's within 24 hours
                                    return "%02d:%02d".format(hour, minute)
                                } else {
                                    // Return null if the data point is not within 24 hours
                                    return "N/A"
                                }
                            }
                        }
                    }
                    return "N/A"
                }
            }
        }
        lineChart.data = LineData(dataSet)
        lineChart.invalidate()
    }

    private fun setupTimeToSleepListener() {
        val timeToSleepRef = FirebaseDatabase.getInstance().getReference("Users")
            .child(FirebaseAuth.getInstance().currentUser?.uid ?: "")
            .child("TimeToSleepHistory")

        timeToSleepRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val timeEntries = mutableListOf<Entry>()
                val dateLabels = mutableListOf<String>()
                snapshot.children.forEachIndexed { index, childSnapshot ->
                    childSnapshot.value.toString().split(" - ").let {
                        if (it.size == 2) {
                            val datePart = it[0]
                            val timePart = it[1]
                            try {
                                val calendar = GregorianCalendar.getInstance()
                                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse("$datePart $timePart")?.let { date ->
                                    calendar.time = date
                                    val minutes = calendar.get(Calendar.MINUTE)
                                    val seconds = calendar.get(Calendar.SECOND)
                                    val timeInMinutes = minutes + seconds / 60.0  // Convert seconds to minutes
                                    timeEntries.add(Entry(index.toFloat(), timeInMinutes.toFloat()))  // Adding time in minutes
                                    dateLabels.add(datePart)
                                }
                            } catch (e: ParseException) {
                                Log.e("DataFragment", "Error parsing sleep time data: ${e.message}")
                            }
                        } else {
                            Log.e("DataFragment", "Unexpected data format received: ${childSnapshot.value}")
                        }
                    }
                }
                updateSleepChart(timeEntries, dateLabels)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("DataFragment", "Firebase Database Error: ${error.message}")
            }
        })
    }
    private fun updateSleepChart(entries: List<Entry>, labels: List<String>) {
        try {
            // Update the sleepDataModal with all data points
            sleepDataModal = SleepData(entries, labels)

            // Display only the latest 5 entries and labels
            val latestEntries = entries.takeLast(5)
            val latestLabels = labels.takeLast(5)

            // Ensure entries are correctly indexed to match the labels
            val correctedEntries = latestEntries.mapIndexed { index, entry -> Entry(index.toFloat(), entry.y) }

            val dataSet = LineDataSet(correctedEntries, "Time To Sleep").apply {
                color = Color.MAGENTA
                valueTextColor = Color.BLACK
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val minutes = value.toInt()
                        val seconds = ((value - minutes) * 60).toInt()
                        return "$minutes m : $seconds s"
                    }
                }
            }

            // Configure the X-axis
            lineChart2.xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(latestLabels)
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                setLabelCount(latestLabels.size, true)
                isGranularityEnabled = true
                axisMinimum = 0f  // Start from the first entry
                axisMaximum = correctedEntries.last().x  // Ensure the maximum is set to the last entry
                textSize = 10f
                lineChart2.setExtraOffsets(10f, 0f, 10f, 0f) // Left, top, right, bottom
                labelRotationAngle = -70f
            }

            lineChart2.data = LineData(dataSet)
            lineChart2.notifyDataSetChanged()
            lineChart2.invalidate()
        } catch (e: Exception) {
            // Handle the error gracefully
            Log.e("UpdateSleepChart", "Error updating sleep chart: ${e.message}")
            // You can show a message to the user indicating there was an error
            // Or provide a fallback behavior
        }
    }

//    override fun onDestroyView() {
//        super.onDestroyView()
//        // Remove the listener when the view is destroyed
//        bpmListener?.let { database.removeEventListener(it) }
//    }

    override fun onDestroy() {
        super.onDestroy()
//        bpmListener?.let { database.removeEventListener(it) }
        cancel()  // Cancel coroutines when the Fragment is destroyed
    }
}
