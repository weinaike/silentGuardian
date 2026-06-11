package com.yestek.silentguardian.ui

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.yestek.silentguardian.R
import com.yestek.silentguardian.manager.DataManager

class AppSelectActivity : androidx.appcompat.app.AppCompatActivity() {

    private lateinit var llBack: LinearLayout
    private lateinit var btnSave: Button
    private lateinit var llAppList: LinearLayout

    private val allCandidateApps = listOf("豆包", "腾讯元宝", "Kimi", "文心一言", "通义千问", "ChatGPT", "Gemini")
    private var currentSelectedApps = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_select)

        llBack = findViewById(R.id.llBack)
        btnSave = findViewById(R.id.btnSave)
        llAppList = findViewById(R.id.llAppList)

        llBack.setOnClickListener { finish() }

        // Load data
        currentSelectedApps.addAll(DataManager.managedApps)

        // Load real apps from device using Launcher Intent
        val pm = packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null)
        intent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        
        // Remove duplicates and sort
        val allCandidateApps = resolveInfos
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .sortedBy { pm.getApplicationLabel(it).toString() }

        val etSearch = findViewById<android.widget.EditText>(R.id.etSearch)
        
        fun renderApps(query: String) {
            llAppList.removeAllViews()
            allCandidateApps.forEach { appInfo ->
                val appName = pm.getApplicationLabel(appInfo).toString()
                if (query.isNotEmpty() && !appName.contains(query, ignoreCase = true)) {
                    return@forEach
                }
                
                val pkgName = appInfo.packageName
                val view = layoutInflater.inflate(R.layout.item_selectable_app, llAppList, false)
                view.findViewById<TextView>(R.id.tvAppName).text = appName
                
                try {
                    val icon = pm.getApplicationIcon(appInfo)
                    view.findViewById<android.widget.ImageView>(R.id.ivAppIcon).setImageDrawable(icon)
                } catch (e: Exception) {
                    // Keep default or handle error
                }
                
                val cb = view.findViewById<CheckBox>(R.id.cbSelect)
                cb.isChecked = currentSelectedApps.contains(pkgName)
                
                view.setOnClickListener {
                    cb.isChecked = !cb.isChecked
                }
                cb.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) currentSelectedApps.add(pkgName)
                    else currentSelectedApps.remove(pkgName)
                }
                llAppList.addView(view)
            }
        }
        
        renderApps("")
        
        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderApps(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        btnSave.setOnClickListener {
            DataManager.managedApps = currentSelectedApps
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (com.yestek.silentguardian.manager.DataManager.appPinCode.isNotEmpty() && !com.yestek.silentguardian.manager.DataManager.isAppUnlocked) {
            val intent = android.content.Intent(this, com.yestek.silentguardian.ui.PinLockActivity::class.java)
            startActivity(intent)
            return
        }
    }
}
