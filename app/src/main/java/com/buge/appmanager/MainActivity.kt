package com.buge.appmanager

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.buge.appmanager.databinding.ActivityMainBinding
import com.buge.appmanager.ui.AppsFragment
import com.buge.appmanager.ui.PermissionsFragment
import com.buge.appmanager.ui.SettingsFragment
import com.buge.appmanager.util.LocaleManager
import com.buge.appmanager.util.PreferencesManager
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requestPermissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        // Handled in SettingsFragment
    }

    override fun attachBaseContext(newBase: Context?) {
        if (newBase != null) {
            val savedLanguage = LocaleManager.getLanguage(newBase)
            if (savedLanguage.isNotEmpty()) {
                val context = LocaleManager.createContextWithLocale(newBase, savedLanguage)
                super.attachBaseContext(context)
                return
            }
        }
        super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply saved theme BEFORE calling super.onCreate()
        val savedTheme = PreferencesManager.getThemeMode(this)
        AppCompatDelegate.setDefaultNightMode(savedTheme)
        
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)

        setupBottomNavigation()

        if (savedInstanceState == null) {
            loadFragment(AppsFragment())
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_apps -> {
                    loadFragment(AppsFragment())
                    true
                }
                R.id.nav_permissions -> {
                    loadFragment(PermissionsFragment())
                    true
                }
                R.id.nav_settings -> {
                    loadFragment(SettingsFragment())
                    true
                }
                else -> false
            }
        }
        // Select apps fragment by default
        binding.bottomNav.selectedItemId = R.id.nav_apps
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
    }
}
