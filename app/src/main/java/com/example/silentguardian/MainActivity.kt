package com.example.silentguardian

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.silentguardian.ui.fragment.HomeFragment
import com.example.silentguardian.ui.fragment.OtherFragment
import com.example.silentguardian.ui.fragment.PermissionFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        com.example.silentguardian.utils.UpdateManager.checkUpdate(this)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav_view)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_permission -> {
                    switchFragment(PermissionFragment())
                    true
                }
                R.id.navigation_home -> {
                    switchFragment(HomeFragment())
                    true
                }
                R.id.navigation_other -> {
                    switchFragment(OtherFragment())
                    true
                }
                else -> false
            }
        }

        // 默认选中主页
        bottomNav.selectedItemId = R.id.navigation_home
    }

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
    }

    override fun onResume() {
        super.onResume()
        if (com.example.silentguardian.manager.DataManager.appPinCode.isEmpty() || !com.example.silentguardian.manager.DataManager.isAppUnlocked) {
            val intent = android.content.Intent(this, com.example.silentguardian.ui.PinLockActivity::class.java)
            startActivity(intent)
        }
    }
}
