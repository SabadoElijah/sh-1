package com.example.sh_prototype


import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.sh_prototype.databinding.ActivityNavbarBinding

class NavBar : AppCompatActivity() {

    private lateinit var binding: ActivityNavbarBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNavbarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNavbar.setOnItemSelectedListener { menuItem ->

            when(menuItem.itemId){
                R.id.dashboard-> {
                    replaceFragment(Dashboard())
                    true
                }
                R.id.data -> {
                    replaceFragment(Data())
                    true
                }
                R.id.alarm -> {
                    replaceFragment(Alarm())
                    true
                }
                // Use the imported Settings class here
                R.id.settings -> {
                    replaceFragment(Settings())
                    true
                }
                R.id.account -> {
                    replaceFragment(Account())
                    true
                }
                else -> false
            }
        }
        replaceFragment(Dashboard())
    }

    private fun replaceFragment(fragment: Fragment): Boolean {
        supportFragmentManager.beginTransaction().replace(R.id.frame_layout, fragment).commit()
        return true
    }
}
