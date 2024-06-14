package com.example.sh_prototype

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase


class LightFragment : Fragment(R.layout.fragment_light), AlarmCallback {
    private lateinit var auraDisplay: View
    private lateinit var lampImageView: ImageView
    private lateinit var redBox: View
    private lateinit var orangeBox: View
    private lateinit var yellowBox: View
    private lateinit var whiteBox: View
    private lateinit var lightOffBox: View
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            // Handle arguments if necessary
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase auth and database reference
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        // Initialize views
        auraDisplay = view.findViewById(R.id.auraDisplay)
        lampImageView = view.findViewById(R.id.lampImageView)
        redBox = view.findViewById(R.id.redLight)
        orangeBox = view.findViewById(R.id.orangeLight)
        yellowBox = view.findViewById(R.id.yellowLight)
        whiteBox = view.findViewById(R.id.whiteLight)
        lightOffBox = view.findViewById(R.id.lightOffButton)

//        // Initialize AlarmService and set callback
//        val alarmService = Alarm()
//        alarmService.setAlarmCallback(this)
//        Log.d("LightFragment","alarm callback set")

        setupListeners()
    }

    private fun setupListeners() {
        redBox.setOnClickListener {
            sendDataToFirebaseColor(1, "Red")
            updateLampDisplay(R.drawable.redlamp)
            updateAuraDisplay(R.drawable.redaura)
        }
        orangeBox.setOnClickListener {
            sendDataToFirebaseColor(2, "Orange")
            updateLampDisplay(R.drawable.orangelamp)
            updateAuraDisplay(R.drawable.orangeaura)
        }
        yellowBox.setOnClickListener {
            sendDataToFirebaseColor(3, "Yellow")
            updateLampDisplay(R.drawable.yellowlamp)
            updateAuraDisplay(R.drawable.yellowaura)
        }
        whiteBox.setOnClickListener {
            sendDataToFirebaseColor(4, "White")
            updateLampDisplay(R.drawable.whitelamp)
            updateAuraDisplay(R.drawable.whiteaura)
        }
        lightOffBox.setOnClickListener {
            sendDataToFirebaseColor(5, "Light Off")
            updateLampDisplay(R.drawable.defaultlamp)
            auraDisplay.visibility = View.GONE // Hide the aura display
        }
    }

    private fun updateAuraDisplay(drawableId: Int) {
        auraDisplay.setBackgroundResource(drawableId)
        auraDisplay.visibility = View.VISIBLE
    }

    private fun updateLampDisplay(drawableId: Int) {
        lampImageView.setImageResource(drawableId)
    }

    private fun sendDataToFirebaseColor(colorCode: Int, status: String) {
        if (::auth.isInitialized && auth.currentUser != null) {
            auth.currentUser!!.uid.let { userId ->
                // Define the reference to the user's light status in the database
                val userLightStatusRef =
                    database.child("Users").child(userId).child("DeviceStatus").child("Light")

                // Set the value of the light color based on the colorCode
                val lightValue = when (colorCode) {
                    1 -> 1
                    2 -> 2
                    3 -> 3
                    4 -> 4
                    else -> 5
                }

                // Set the value of the light color
                userLightStatusRef.setValue(lightValue)
                    .addOnSuccessListener {
                        // Handle success
                        showToast("Light successfully changed to $status, and status set to $lightValue")
                    }
                    .addOnFailureListener { exception ->
                        // Handle failure
                        showToast("Failed to change light to $status. ${exception.message}")
                    }
            }
        } else {
            showToast("User not logged in.")
        }
    }

    private fun showToast(message: String) {
        context?.let {
            Toast.makeText(it, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onAlarmStopped() {
        Log.e("LightFragment","onalarmstopped error")
        // Handle alarm stopped event
        sendDataToFirebaseColor(5, "Light Off")
        updateLampDisplay(R.drawable.defaultlamp)
        auraDisplay.visibility = View.GONE // Hide the aura display
    }
}
