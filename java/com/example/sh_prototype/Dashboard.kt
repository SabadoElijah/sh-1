package com.example.sh_prototype

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.sh_prototype.databinding.FragmentDashboardBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class Dashboard : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var buttonSleepy: ImageButton
    private lateinit var buttonRelaxed: ImageButton
    private lateinit var buttonAnxious: ImageButton
    private lateinit var buttonExcited: ImageButton
    private lateinit var databaseReference: DatabaseReference
    private var deviceStatusListener: ValueEventListener? = null // Reference to the listener

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid
        if(currentUserUid != null){
            databaseReference = FirebaseDatabase.getInstance().getReference("Users").child(currentUserUid).child("DeviceStatus")
            listenToDeviceStatusUpdates()
        }
        buttonSleepy = binding.buttonSleepy
        buttonRelaxed = binding.buttonRelaxed
        buttonAnxious = binding.buttonAnxious
        buttonExcited = binding.buttonExcited

        setDefaultMood()

        setupMoodButtons(binding.textViewMessage, binding.imageViewMood)

        binding.textViewQuickTips.setOnClickListener {
            navigateToInfoOne()
        }
        binding.MusicandLight.setOnClickListener {
            navigateToInfoTwo()
        }
        binding.Productivity.setOnClickListener {
            navigateToInfoThree()
        }
        binding.Myths.setOnClickListener {
            navigateToInfoFour()
        }
    }

    private fun setupMoodButtons(messageTextView: TextView, moodImageView: ImageView) {
        val scaleAnim = AnimationUtils.loadAnimation(requireContext(), R.anim.scale)
        buttonSleepy.setOnClickListener {
            buttonSleepy.startAnimation(scaleAnim)
            updateMood("Feeling a bit drowsy? It's your body telling you it's time to recharge. Why not take a moment to pause, relax, and let yourself sink into a peaceful night's sleep? Sweet Dreams!", R.drawable.sleepyy)
        }
        buttonRelaxed.setOnClickListener {
            buttonRelaxed.startAnimation(scaleAnim)
            updateMood("Feeling relaxed? It might be the perfect time to ease into sleep. Let your body and mind unwind, and drift off into a peaceful slumber. Goodnight!", R.drawable.relaxedd)
        }
        buttonAnxious.setOnClickListener {
            buttonAnxious.startAnimation(scaleAnim)
            updateMood("Feeling anxious? Breathe and relax. It'll pass. Let's focus on calming your mind for a peaceful sleep", R.drawable.anxiouss)
        }
        buttonExcited.setOnClickListener {
            buttonExcited.startAnimation(scaleAnim)
            updateMood("Feeling excited? That's awesome! Channel that energy into winding down for a restful night. Let's calm your excitement and prepare for a refreshing sleep.", R.drawable.hello          )
        }
    }

    private fun updateMood(message: String, imageResId: Int) {
        binding.textViewMessage.text = message
        binding.imageViewMood.setImageResource(imageResId)
    }

    private fun navigateToInfoOne() {
        val intent = Intent(activity, Infoone::class.java)
        startActivity(intent)
    }

    private fun navigateToInfoTwo() {
        val intent = Intent(activity, Infotwo::class.java)
        startActivity(intent)
    }

    private fun navigateToInfoThree() {
        val intent = Intent(activity, Infothree::class.java)
        startActivity(intent)
    }

    private fun navigateToInfoFour() {
        val intent = Intent(activity, Infofour::class.java)
        startActivity(intent)
    }

    private fun setDefaultMood() {
        val defaultMessage = "Every day is a fresh start, a new opportunity to make things brighter. Remember, taking a moment for yourself is not a luxury, it’s a necessity. Be gentle with yourself, and let's prepare for a restful night."
        val defaultImageResId = R.drawable.moon
        updateMood(defaultMessage, defaultImageResId)
    }
    private fun listenToDeviceStatusUpdates() {
        val localListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    if (snapshot.exists()) {
                        val lightStatus = snapshot.child("Light").getValue(Int::class.java) ?: 5
                        val humidifierStatus = snapshot.child("Humidifier").getValue(Int::class.java) ?: 0
                        val heartStatus = snapshot.child("MAX30102").getValue(Int::class.java) ?: 0
                        val speakerStatus = snapshot.child("Speakers").getValue(Int::class.java) ?: 0

                        // Update UI elements based on the retrieved values
                        binding.Lightsub.text = when (lightStatus) {
                            1, 2, 3, 4 -> "Turned on"
                            5 -> "Turned off"
                            else -> "Status unknown"  // Handling unexpected values
                        }
                        binding.Scentsub.text = when (humidifierStatus) {
                            1 -> "Turned On"
                            else -> "Turned off"
                        }
                        binding.Heartsub.text = when (heartStatus){
                            1 -> "Turned On"
                            else ->"Turned Off"
                        }
                        binding.MusicSub.text = when (speakerStatus) {
                            1 -> "Playing"
                            2 -> "Paused"
                            else -> "Turned off"
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Dashboard", "Error parsing device status data", e)
                    binding.Lightsub.text = "Status unknown"
                    binding.Scentsub.text = "Status unknown"
                    binding.Heartsub.text = "Status unknown"
                    binding.MusicSub.text = "Status unknown"
                }
            }


            override fun onCancelled(error: DatabaseError) {
                Log.e("Dashboard", "Error listening to device status updates", error.toException())
            }
        }
        deviceStatusListener = localListener
        val userUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        databaseReference = FirebaseDatabase.getInstance().getReference("Users/$userUid/DeviceStatus")
        databaseReference.addValueEventListener(localListener)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        deviceStatusListener?.let { databaseReference.removeEventListener(it) }
        val listener = deviceStatusListener
        if (listener != null) {
            databaseReference.removeEventListener(listener)
        }
        deviceStatusListener = null
    }

}

