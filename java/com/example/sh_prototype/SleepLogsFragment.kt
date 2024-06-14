package com.example.sh_prototype

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Button
import androidx.fragment.app.DialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class SleepLogsDialogFragment : DialogFragment() {
    private var sleepData: String? = null
    private var dataListener: ValueEventListener? = null

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            val width = (resources.displayMetrics.widthPixels * 0.9).toInt()
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        if (sleepData == null) {
            val textView = view?.findViewById<TextView>(R.id.sleepDataText)
            textView?.text = "Loading..."
            loadSleepData(textView)
        } else {
            view?.findViewById<TextView>(R.id.sleepDataText)?.text = sleepData
        }
    }

    override fun onStop() {
        super.onStop()
        dataListener?.let {
            val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid
            if (currentUserUid != null) {
                val ref = FirebaseDatabase.getInstance().getReference("Users/$currentUserUid/TimeToSleepHistory")
                ref.removeEventListener(it)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_sleep_logs, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val closeButton = view.findViewById<Button>(R.id.closeButton)
        closeButton.setOnClickListener {
            dismiss()
        }
    }

    private fun loadSleepData(textView: TextView?) {
        val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserUid != null) {
            val ref = FirebaseDatabase.getInstance().getReference("Users/$currentUserUid/TimeToSleepHistory")
            dataListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val sb = StringBuilder()
                    if (!snapshot.exists()) {
                        sleepData = "No sleep data found."
                        textView?.text = sleepData
                        return
                    }

                    for (child in snapshot.children) {
                        sb.append(child.value.toString()).append("\n")
                    }
                    sleepData = if (sb.isNotEmpty()) sb.toString() else "No sleep data found."
                    textView?.text = sleepData
                }

                override fun onCancelled(error: DatabaseError) {
                    sleepData = "Failed to load data. Error: ${error.message}"
                    textView?.text = sleepData
                }
            }
            ref.addValueEventListener(dataListener!!)
        } else {
            sleepData = "User not logged in."
            textView?.text = sleepData
        }
    }
}
