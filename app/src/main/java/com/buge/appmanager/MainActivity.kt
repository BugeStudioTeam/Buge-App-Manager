package com.buge.appmanager

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.buge.appmanager.databinding.ActivityMainBinding
import com.buge.appmanager.ui.ActivitiesFragment
import com.buge.appmanager.ui.AppsFragment
import com.buge.appmanager.ui.PermissionsFragment
import com.buge.appmanager.ui.SettingsFragment
import com.buge.appmanager.util.FontOverrideHelper
import com.buge.appmanager.util.LocaleManager
import com.buge.appmanager.util.LogManager
import com.buge.appmanager.util.PreferencesManager
import com.buge.appmanager.util.UpdateChecker
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.util.Locale

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentFragment: Fragment? = null
    private var hasCheckedUpdate = false

    private val requestPermissionResultListener = Shizuku.OnRequestPermissionResultListener { _, _ -> }

    override fun attachBaseContext(newBase: Context?) {
        if (newBase != null) {
            val savedLanguage = LocaleManager.getLanguage(newBase)
            val context = LocaleManager.createContextWithLocale(newBase, savedLanguage)
            super.attachBaseContext(context)
            
            val currentLocale = if (savedLanguage.isEmpty()) {
                Locale.getDefault()
            } else {
                Locale(savedLanguage)
            }
            val isEnglish = currentLocale.language == "en"
            FontOverrideHelper.setEnglishLocaleFlag(isEnglish)
        } else {
            super.attachBaseContext(newBase)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val savedTheme = PreferencesManager.getThemeMode(this)
        AppCompatDelegate.setDefaultNightMode(savedTheme)
        
        super.onCreate(savedInstanceState)
        
        LogManager.init(this)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()

        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)

        setupBottomNavigation()

        if (savedInstanceState == null) {
            loadDefaultPage()
        }
        
        checkForUpdateOnStart()
    }

    private fun checkForUpdateOnStart() {
        // Check if auto update is enabled
        if (!PreferencesManager.getAutoUpdate(this)) {
            LogManager.info(this, "Auto update is disabled, skipping check")
            return
        }
        
        if (hasCheckedUpdate) return
        
        lifecycleScope.launch {
            try {
                val releaseInfo = UpdateChecker.checkForUpdates(this@MainActivity)
                if (releaseInfo != null) {
                    hasCheckedUpdate = true
                    UpdateChecker.showUpdateDialog(
                        this@MainActivity,
                        releaseInfo,
                        onDownload = {
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(releaseInfo.apkDownloadUrl))
                            startActivity(intent)
                        }
                    )
                }
            } catch (e: Exception) {
                LogManager.warning(this@MainActivity, "Auto update check failed: ${e.message}")
            }
        }
    }

    private fun loadDefaultPage() {
        val defaultPage = PreferencesManager.getDefaultPage(this)
        val fragment = when (defaultPage) {
            "apps" -> AppsFragment()
            "permissions" -> PermissionsFragment()
            "activities" -> ActivitiesFragment()
            "settings" -> SettingsFragment()
            else -> AppsFragment()
        }
        val navId = when (defaultPage) {
            "apps" -> R.id.nav_apps
            "permissions" -> R.id.nav_permissions
            "activities" -> R.id.nav_activities
            "settings" -> R.id.nav_settings
            else -> R.id.nav_apps
        }
        binding.bottomNav.selectedItemId = navId
        loadFragment(fragment)
        LogManager.info(this, "App started, default page: $defaultPage")
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            view.setPadding(
                view.paddingLeft,
                view.paddingTop + systemBars.top,
                view.paddingRight,
                view.paddingBottom + systemBars.bottom
            )
            
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentFragment is ActivitiesFragment) {
            (currentFragment as? ActivitiesFragment)?.refresh()
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_apps -> {
                    loadFragment(AppsFragment())
                    supportActionBar?.title = getString(R.string.nav_apps)
                    true
                }
                R.id.nav_permissions -> {
                    loadFragment(PermissionsFragment())
                    supportActionBar?.title = getString(R.string.nav_permissions)
                    true
                }
                R.id.nav_activities -> {
                    loadFragment(ActivitiesFragment())
                    supportActionBar?.title = getString(R.string.nav_activities)
                    true
                }
                R.id.nav_settings -> {
                    loadFragment(SettingsFragment())
                    supportActionBar?.title = getString(R.string.nav_settings)
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        currentFragment = fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
    }
}