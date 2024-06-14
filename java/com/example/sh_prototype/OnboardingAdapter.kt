package com.example.sh_prototype
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class OnboardingAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {
    override fun getItemCount(): Int = 3  // Adjust the number based on the actual number of screens

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> Onboardingone()
            1 -> Onboardingtwo()
            2 -> Onboardingthree()
            else -> throw IllegalArgumentException("Invalid position")
        }
    }
}
