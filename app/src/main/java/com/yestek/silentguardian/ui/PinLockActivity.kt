package com.yestek.silentguardian.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yestek.silentguardian.MainActivity
import com.yestek.silentguardian.R
import com.yestek.silentguardian.manager.DataManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PinLockActivity : AppCompatActivity() {

    private var currentPin = ""
    private var isSettingMode = false
    private var isModifyMode = false
    private var firstPinForSetting = ""

    private lateinit var tvPinTitle: TextView
    private lateinit var tvPinSubtitle: TextView
    private lateinit var tvErrorMsg: TextView
    private lateinit var dots: List<View>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin_lock)

        tvPinTitle = findViewById(R.id.tvPinTitle)
        tvPinSubtitle = findViewById(R.id.tvPinSubtitle)
        tvErrorMsg = findViewById(R.id.tvErrorMsg)

        dots = listOf(
            findViewById(R.id.dot1),
            findViewById(R.id.dot2),
            findViewById(R.id.dot3),
            findViewById(R.id.dot4)
        )

        isModifyMode = intent.getBooleanExtra("MODIFY_MODE", false)
        isSettingMode = DataManager.appPinCode.isEmpty()

        setupModeUI()
        setupKeypad()
    }

    private fun setupModeUI() {
        if (isModifyMode && !isSettingMode) {
            tvPinTitle.text = getString(R.string.pin_enter_original)
            tvPinSubtitle.text = getString(R.string.pin_verify_to_modify)
        } else if (isSettingMode) {
            tvPinTitle.text = getString(if (isModifyMode) R.string.pin_set_new_password else R.string.pin_set_password)
            tvPinSubtitle.text = getString(R.string.pin_set_4digit)
        } else {
            tvPinTitle.text = getString(R.string.pin_enter_password)
            tvPinSubtitle.text = getString(R.string.pin_verify_to_access)
        }
    }

    private fun setupKeypad() {
        for (i in 0..9) {
            val btn = window.decorView.findViewWithTag<Button>(i.toString())
            btn?.setOnClickListener {
                if (currentPin.length < 4) {
                    currentPin += i.toString()
                    updateDots()
                    tvErrorMsg.visibility = View.INVISIBLE

                    if (currentPin.length == 4) {
                        handlePinComplete()
                    }
                }
            }
        }

        findViewById<View>(R.id.btnDelete).setOnClickListener {
            if (currentPin.isNotEmpty()) {
                currentPin = currentPin.dropLast(1)
                updateDots()
            }
        }
    }

    private fun updateDots() {
        for (i in 0..3) {
            if (i < currentPin.length) {
                dots[i].setBackgroundResource(R.drawable.bg_pin_dot_filled)
            } else {
                dots[i].setBackgroundResource(R.drawable.bg_pin_dot_empty)
            }
        }
    }

    private fun handlePinComplete() {
        lifecycleScope.launch {
            delay(100) // Small delay so user sees the 4th dot fill up

            if (isSettingMode) {
                if (firstPinForSetting.isEmpty()) {
                    firstPinForSetting = currentPin
                    currentPin = ""
                    updateDots()
                    tvPinTitle.text = getString(R.string.pin_enter_again)
                    tvPinSubtitle.text = getString(R.string.pin_confirm_password)
                } else {
                    if (currentPin == firstPinForSetting) {
                        DataManager.appPinCode = currentPin
                        DataManager.isAppUnlocked = true

                        if (isModifyMode) {
                            android.widget.Toast.makeText(this@PinLockActivity, R.string.pin_modified_success, android.widget.Toast.LENGTH_SHORT).show()
                            finish()
                        } else {
                            startMainActivityAndFinish()
                        }
                    } else {
                        showError(getString(R.string.pin_mismatch))
                        firstPinForSetting = ""
                        currentPin = ""
                        updateDots()
                        setupModeUI()
                    }
                }
            } else {
                if (currentPin == DataManager.appPinCode) {
                    if (isModifyMode) {
                        // 进入设置新密码模式
                        isSettingMode = true
                        currentPin = ""
                        firstPinForSetting = ""
                        updateDots()
                        setupModeUI()
                    } else {
                        DataManager.isAppUnlocked = true
                        startMainActivityAndFinish()
                    }
                } else {
                    showError(getString(R.string.pin_wrong_password))
                    currentPin = ""
                    updateDots()
                }
            }
        }
    }

    private fun showError(msg: String) {
        tvErrorMsg.text = msg
        tvErrorMsg.visibility = View.VISIBLE
    }

    private fun startMainActivityAndFinish() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onBackPressed() {
        // Prevent back button from bypassing lock
        // Move app to background instead
        moveTaskToBack(true)
    }
}
