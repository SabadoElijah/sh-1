package com.example.sh_prototype

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class Infofour : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_infofour)

        val backButton: ImageButton = findViewById(R.id.backButton)
        backButton.setOnClickListener {
            // Simply finish the current activity to go back to the previous one
            finish()
        }
    }
}