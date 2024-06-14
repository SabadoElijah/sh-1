package com.example.sh_prototype

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.google.firebase.auth.FirebaseAuth

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var getStartedButton: Button
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            // User is already logged in, redirect to DashboardActivity
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
        }

        viewPager = findViewById(R.id.viewPager)
        getStartedButton = findViewById(R.id.GetStarted)
        viewPager.adapter = OnboardingAdapter(this)

        setupDots(3)
        setupButtonVisibility()
        setupButtonClick()
    }

    private fun setupDots(count: Int) {
        val dotsContainer = findViewById<LinearLayout>(R.id.dotsContainer)
        val dots = Array(count) { ImageView(this) }

        // Adjust dot appearance
        for (i in dots.indices) {
            dots[i].setImageDrawable(ContextCompat.getDrawable(this, R.drawable.onboardcircle)) // inactive dot drawable
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(10, 0, 10, 0)
            dots[i].layoutParams = params
            dotsContainer.addView(dots[i])
        }

        // Set the active dot
        val changeListener = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                for (i in dots.indices) {
                    if (i == position) {
                        dots[i].setImageDrawable(ContextCompat.getDrawable(this@OnboardingActivity, R.drawable.onboardbilog)) // active dot drawable
                    } else {
                        dots[i].setImageDrawable(ContextCompat.getDrawable(this@OnboardingActivity, R.drawable.onboardcircle)) // inactive dot drawable
                    }
                }
            }
        }

        viewPager.registerOnPageChangeCallback(changeListener)
    }

    private fun setupButtonVisibility() {
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position == 2) { // Assuming the third screen is at position 2
                    getStartedButton.visibility = Button.VISIBLE
                } else {
                    getStartedButton.visibility = Button.GONE
                }
            }
        })
    }

    private fun setupButtonClick() {
        getStartedButton.setOnClickListener {
            // Start the registration activity here
            val intent = Intent(this, Registration::class.java)
            startActivity(intent)
            // Finish the onboarding activity if needed
            finish()
        }
    }
}
