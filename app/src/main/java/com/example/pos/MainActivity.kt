package com.example.pos

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Check if savedInstanceState is null (first-time launch)
        if (savedInstanceState == null) {
            // Load the MenuFragment by default
            loadFragment(MenuFragment())
        }

        // Set up bottom navigation and handle fragment switching
        val bottomNavigationView: BottomNavigationView = findViewById(R.id.bottom_navigation)
        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_menu -> {
                    loadFragment(MenuFragment()) // Load the MenuFragment
                    true
                }
                R.id.navigation_sales -> {
                    loadFragment(SalesFragment()) // Load the SalesFragment
                    true
                }
                else -> false
            }
        }
    }

    // Helper function to load fragments
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.commit {
            replace(R.id.nav_host_fragment, fragment)
        }
    }
}
