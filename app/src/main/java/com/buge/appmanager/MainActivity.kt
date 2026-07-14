package com.buge.appmanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
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
import com.buge.appmanager.util.ThemeManager
import com.buge.appmanager.util.UpdateChecker
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigationrail.NavigationRailView
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.util.Locale

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentFragment: Fragment? = null
    private var hasCheckedUpdate = false

    private val requestPermissionResultListener = Shizuku.OnRequestPermissionResultListener { _, _ -> }

    private val navLabelsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.buge.appmanager.ACTION_NAV_LABELS_CHANGED") {
                applyHideNavLabels()
            }
        }
    }

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
        ThemeManager.applyColorTheme(this)
        
        val savedTheme = PreferencesManager.getThemeMode(this)
        AppCompatDelegate.setDefaultNightMode(savedTheme)
        
        super.onCreate(savedInstanceState)
        
        LogManager.init(this)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()

        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)

        setupNavigation()
        applyHideNavLabels()

        val filter = IntentFilter("com.buge.appmanager.ACTION_NAV_LABELS_CHANGED")
        registerReceiver(navLabelsReceiver, filter)

        if (savedInstanceState == null) {
            loadDefaultPage()
        }
        
        checkForUpdateOnStart()
    }

    override fun onResume() {
        super.onResume()
        if (currentFragment is ActivitiesFragment) {
            (currentFragment as? ActivitiesFragment)?.refresh()
        }
        applyHideNavLabels()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(navLabelsReceiver)
        } catch (e: Exception) {
            // Ignore
        }
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
    }

    private fun isLargeScreen(): Boolean {
        val displayMetrics = resources.displayMetrics
        val densityDpi = displayMetrics.densityDpi
        val widthPx = displayMetrics.widthPixels
        
        // Use DPI > 480 OR screen width > 600dp (tablet-like)
        val isHighDpi = densityDpi >= 480
        val isWideScreen = widthPx > 600 * displayMetrics.density
        
        return isHighDpi || isWideScreen
    }

    private fun getNavigationRailWidth(): Int {
        // NavigationRail default width is 72dp on most devices
        return (72 * resources.displayMetrics.density).toInt()
    }

    private fun setupNavigation() {
        val useRail = isLargeScreen()
        
        if (useRail) {
            // Use NavigationRail
            binding.navRail.visibility = View.VISIBLE
            binding.bottomNav.visibility = View.GONE
            
            // Add margin to fragment container to make room for nav rail
            val params = binding.fragmentContainerWrapper.layoutParams as ViewGroup.MarginLayoutParams
            params.leftMargin = getNavigationRailWidth()
            binding.fragmentContainerWrapper.layoutParams = params
            
            // Copy selected item from bottom nav to rail
            val selectedId = binding.bottomNav.selectedItemId
            if (selectedId != 0) {
                binding.navRail.selectedItemId = selectedId
            }
            
            binding.navRail.setOnItemSelectedListener { item ->
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
        } else {
            // Use BottomNavigation
            binding.navRail.visibility = View.GONE
            binding.bottomNav.visibility = View.VISIBLE
            
            // Reset fragment container margin
            val params = binding.fragmentContainerWrapper.layoutParams as ViewGroup.MarginLayoutParams
            params.leftMargin = 0
            binding.fragmentContainerWrapper.layoutParams = params
            
            // Copy selected item from rail to bottom nav
            val selectedId = binding.navRail.selectedItemId
            if (selectedId != 0) {
                binding.bottomNav.selectedItemId = selectedId
            }
            
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
        
        applyHideNavLabels()
    }

    private fun applyHideNavLabels() {
        val hideLabels = PreferencesManager.getHideNavLabels(this)
        
        binding.bottomNav.labelVisibilityMode = if (hideLabels) {
            BottomNavigationView.LABEL_VISIBILITY_SELECTED
        } else {
            BottomNavigationView.LABEL_VISIBILITY_LABELED
        }
        
        binding.navRail.labelVisibilityMode = if (hideLabels) {
            NavigationRailView.LABEL_VISIBILITY_SELECTED
        } else {
            NavigationRailView.LABEL_VISIBILITY_LABELED
        }
    }

    private fun checkForUpdateOnStart() {
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
        
        if (isLargeScreen()) {
            binding.navRail.selectedItemId = navId
        } else {
            binding.bottomNav.selectedItemId = navId
        }
        
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

    private fun loadFragment(fragment: Fragment) {
        currentFragment = fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}