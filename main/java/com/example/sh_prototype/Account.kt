package com.example.sh_prototype

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import android.widget.Toast
import com.google.firebase.database.FirebaseDatabase

class Account : Fragment() {
    private lateinit var user: FirebaseUser
    private lateinit var auth: FirebaseAuth
    private lateinit var buttonLogout: Button
    private lateinit var textView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        user = auth.currentUser!!
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_account, container, false)

        textView = view.findViewById(R.id.user_details)
        buttonLogout = view.findViewById(R.id.logoutButton)

        if (user == null) {
            // If user is not authenticated, navigate to Login activity
            val intent = Intent(requireActivity(), Login::class.java)
            startActivity(intent)
            requireActivity().finish()
        } else {
            textView.text = user.email
        }

        buttonLogout.setOnClickListener {
            val uid = user.uid
            val database = FirebaseDatabase.getInstance()
            val recentLoginsRef = database.getReference("RecentLogins/$uid/LoginStatus")
            recentLoginsRef.setValue(false)
                .addOnSuccessListener {
                    Log.d("AccountFragment", "LoginStatus updated successfully.")

                    // Perform logout and navigate to Login activity
                    auth.signOut()
                    val intent = Intent(requireActivity(), Login::class.java)
                    startActivity(intent)
                    requireActivity().finish()
                }
                .addOnFailureListener { e ->
                    Log.e("AccountFragment", "Error updating LoginStatus", e)
                    Toast.makeText(requireContext(), "Error logging out. Please try again.", Toast.LENGTH_SHORT).show()
                }
        }
        return view
    }
}