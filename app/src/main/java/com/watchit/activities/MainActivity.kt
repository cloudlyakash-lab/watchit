package com.watchit.activities

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GravityCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.watchit.PreferenceManager
import com.watchit.R
import com.watchit.databinding.ActivityMainBinding
import com.watchit.repository.DataRepository

class MainActivity : AppCompatActivity(),
    NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var toggle: ActionBarDrawerToggle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dark = PreferenceManager.isDarkMode(this)
        AppCompatDelegate.setDefaultNightMode(
            if (dark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupDrawer()
        setupNavigation()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.ivSearch.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }
    }

    private fun setupDrawer() {
        toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        binding.navView.setNavigationItemSelectedListener(this)
        updateServerMenuChecks()

        try {
            val headerView = binding.navView.getHeaderView(0)
            val darkModeSwitch = headerView.findViewById<SwitchMaterial>(R.id.switchDarkMode)
            darkModeSwitch?.isChecked = PreferenceManager.isDarkMode(this)
            darkModeSwitch?.setOnCheckedChangeListener { _, isChecked ->
                PreferenceManager.setDarkMode(this, isChecked)
                AppCompatDelegate.setDefaultNightMode(
                    if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                    else AppCompatDelegate.MODE_NIGHT_NO
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateServerMenuChecks() {
        val menu = binding.navView.menu
        val currentServer = PreferenceManager.getSelectedServer(this)
        menu.findItem(R.id.nav_server_1)?.isChecked = (currentServer == 1)
        menu.findItem(R.id.nav_server_2)?.isChecked = (currentServer == 2)
        menu.findItem(R.id.nav_server_3)?.isChecked = (currentServer == 3)
    }

    private fun setupNavigation() {
        try {
            val navHostFragment =
                supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
            if (navHostFragment is NavHostFragment) {
                navController = navHostFragment.navController
                binding.bottomNav.setupWithNavController(navController)
                navController.addOnDestinationChangedListener { _, destination, _ ->
                    binding.tvAppBarTitle.text = when (destination.id) {
                        R.id.homeFragment     -> getString(R.string.app_name)
                        R.id.moviesFragment   -> "Movies"
                        R.id.liveTvFragment   -> "Live TV"
                        R.id.seriesFragment   -> "Series"
                        R.id.favoriteFragment -> "Favorites"
                        else                  -> getString(R.string.app_name)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        try {
            when (item.itemId) {
                R.id.nav_home     -> navController.navigate(R.id.homeFragment)
                R.id.nav_movies   -> navController.navigate(R.id.moviesFragment)
                R.id.nav_series   -> navController.navigate(R.id.seriesFragment)
                R.id.nav_live_tv  -> navController.navigate(R.id.liveTvFragment)
                R.id.nav_settings -> startActivity(Intent(this, SettingsActivity::class.java))

                R.id.nav_server_1 -> switchServer(1)
                R.id.nav_server_2 -> switchServer(2)
                R.id.nav_server_3 -> switchServer(3)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    /** Server পরিবর্তন করো এবং app restart করে নতুন data load করো */
    private fun switchServer(serverNumber: Int) {
        val current = PreferenceManager.getSelectedServer(this)
        if (current == serverNumber) {
            Toast.makeText(this, "Already on Server $serverNumber", Toast.LENGTH_SHORT).show()
            return
        }
        PreferenceManager.setSelectedServer(this, serverNumber)

        // ✅ FIX: DataRepository singleton হওয়ায় app restart এও cachedData
        // মেমোরিতে থাকে — server change হলে অবশ্যই cache clear করতে হবে
        DataRepository.getInstance(this).clearCache()

        Toast.makeText(this, "Switched to Server $serverNumber. Reloading…", Toast.LENGTH_SHORT).show()
        updateServerMenuChecks()

        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}