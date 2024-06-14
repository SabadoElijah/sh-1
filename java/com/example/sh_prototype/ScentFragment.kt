package com.example.sh_prototype

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class ScentFragment : Fragment() {

    private lateinit var switchButton: SwitchCompat
    private lateinit var databaseReference: DatabaseReference
    private lateinit var humidifier: ImageView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_scent, container, false)
        initializeFirebase()
        switchButton = view.findViewById(R.id.switch_button)
        humidifier = view.findViewById<ImageView>(R.id.humidifier)
        setupSwitchListener()
        fetchInitialSwitchState() // Fetch the initial state from Firebase
        return view
    }
    private fun initializeFirebase() {
        val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid
        if(currentUserUid != null){
            databaseReference = FirebaseDatabase.getInstance().getReference("Users").child(currentUserUid).child("DeviceStatus")
        }
    }
    private fun setupSwitchListener() {
        switchButton.setOnCheckedChangeListener { _, isChecked ->
            val status = if (isChecked) 1 else 0
            if (isChecked) {
                Glide.with(requireContext()).load(R.raw.humid).into(humidifier)
            } else {
                humidifier.setImageResource(R.drawable.humidd)
            }
            updateHumidifierStatus(status)
        }
    }

    private fun fetchInitialSwitchState() {
        databaseReference.child("Humidifier").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.getValue(Int::class.java) ?: 0
                switchButton.isChecked = status == 1
                if (status == 1) {
                    Glide.with(requireContext()).load(R.raw.humid).into(humidifier)
                } else {
                    humidifier.setImageResource(R.drawable.humidd)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Log or handle error
            }
        })
    }

    private fun updateHumidifierStatus(status: Int) {
        databaseReference.child("Humidifier").setValue(status)
            .addOnSuccessListener {
            }
            .addOnFailureListener {
                // Log or handle error
            }
    }
}
