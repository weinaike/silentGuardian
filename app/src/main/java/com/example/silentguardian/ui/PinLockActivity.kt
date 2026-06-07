package com.example.silentguardian.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.example.silentguardian.MainActivity
import com.example.silentguardian.R
import com.example.silentguardian.manager.DataManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PinLockActivity : Activity() {

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
            tvPinTitle.text = "请输入原解锁密码"
            tvPinSubtitle.text = "验证身份以修改密码"
        } else if (isSettingMode) {
            tvPinTitle.text = if (isModifyMode) "设置新解锁密码" else "设置解锁密码"
            tvPinSubtitle.text = "请设置一个4位数的密码以保护应用"
        } else {
            tvPinTitle.text = "请输入解锁密码"
            tvPinSubtitle.text = "验证身份以访问应用面板"
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
        CoroutineScope(Dispatchers.Main).launch {
            delay(100) // Small delay so user sees the 4th dot fill up
            
            if (isSettingMode) {
                if (firstPinForSetting.isEmpty()) {
                    firstPinForSetting = currentPin
                    currentPin = ""
                    updateDots()
                    tvPinTitle.text = "请再次输入密码"
                    tvPinSubtitle.text = "请确认您刚才设置的4位数密码"
                } else {
                    if (currentPin == firstPinForSetting) {
                        DataManager.appPinCode = currentPin
                        DataManager.isAppUnlocked = true
                        
                        if (isModifyMode) {
                            android.widget.Toast.makeText(this@PinLockActivity, "密码修改成功", android.widget.Toast.LENGTH_SHORT).show()
                            finish()
                        } else {
                            startMainActivityAndFinish()
                        }
                    } else {
                        showError("两次密码不一致，请重试")
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
                    showError("密码错误，请重试")
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
