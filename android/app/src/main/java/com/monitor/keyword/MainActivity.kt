package com.monitor.keyword

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var apiClient: ApiClient
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        prefs = getSharedPreferences("keyword_monitor", Context.MODE_PRIVATE)
        apiClient = ApiClient(prefs)
        
        // 设置固定服务器地址
        prefs.edit().putString("server_url", "http://156.239.15.14:3000").apply()
        
        // 每次打开APP都需要验证密码
        showPasswordDialog()
    }
    
    private fun showPasswordDialog() {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }
        
        val titleText = TextView(this).apply {
            text = "🔐 验证密码"
            textSize = 20f
            setTextColor(android.graphics.Color.parseColor("#333333"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 30)
        }
        
        val hintText = TextView(this).apply {
            text = "请输入管理员提供的密码"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#666666"))
            setPadding(0, 0, 0, 20)
        }
        
        val passwordInput = EditText(this).apply {
            hint = "请输入密码"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(40, 30, 40, 30)
            background = resources.getDrawable(android.R.drawable.edit_text, null)
        }
        
        dialogView.addView(titleText)
        dialogView.addView(hintText)
        dialogView.addView(passwordInput)
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("确认") { _, _ ->
                val password = passwordInput.text.toString()
                if (password.isEmpty()) {
                    Toast.makeText(this, "请输入密码", Toast.LENGTH_SHORT).show()
                    showPasswordDialog()
                } else {
                    verifyPassword(password)
                }
            }
            .setNegativeButton("退出") { _, _ ->
                finish()
            }
            .create()
        
        dialog.show()
    }
    
    private fun verifyPassword(password: String) {
        val loadingDialog = AlertDialog.Builder(this)
            .setMessage("验证中...")
            .setCancelable(false)
            .create()
        loadingDialog.show()
        
        scope.launch {
            val isValid = apiClient.verifyPassword(password)
            loadingDialog.dismiss()
            
            if (isValid) {
                // 不再保存密码验证状态，每次都需要验证
                Toast.makeText(this@MainActivity, "✅ 密码正确", Toast.LENGTH_SHORT).show()
                
                // 每次都重新设置设备备注
                showRemarkDialog()
            } else {
                Toast.makeText(this@MainActivity, "❌ 密码错误，请重试", Toast.LENGTH_SHORT).show()
                showPasswordDialog()
            }
        }
    }
    
    private fun showRemarkDialog() {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }
        
        val titleText = TextView(this).apply {
            text = "📱 设置设备备注"
            textSize = 20f
            setTextColor(android.graphics.Color.parseColor("#333333"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 30)
        }
        
        val hintText = TextView(this).apply {
            text = "请为这台设备设置一个备注名称\n方便在多台设备中进行区分"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#666666"))
            setPadding(0, 0, 0, 20)
        }
        
        val remarkInput = EditText(this).apply {
            hint = "例如：张三的手机、办公室设备1"
            setPadding(40, 30, 40, 30)
            background = resources.getDrawable(android.R.drawable.edit_text, null)
        }
        
        dialogView.addView(titleText)
        dialogView.addView(hintText)
        dialogView.addView(remarkInput)
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("确认") { _, _ ->
                val remark = remarkInput.text.toString().trim()
                if (remark.isEmpty()) {
                    Toast.makeText(this, "请输入设备备注", Toast.LENGTH_SHORT).show()
                    showRemarkDialog()
                } else {
                    prefs.edit().putString("device_remark", remark).apply()
                    Toast.makeText(this, "✅ 设备备注已设置：$remark", Toast.LENGTH_SHORT).show()
                    showMainInterface()
                }
            }
            .create()
        
        dialog.show()
    }
    
    private fun showMainInterface() {
        setContentView(R.layout.activity_main)
        
        val statusText = findViewById<TextView>(R.id.status_text)
        val enableButton = findViewById<Button>(R.id.enable_button)
        
        // 启用辅助功能
        enableButton.setOnClickListener {
            openAccessibilitySettings()
        }
        
        // 更新状态
        updateStatus(statusText)
        
        // 定期更新状态
        statusText.postDelayed(object : Runnable {
            override fun run() {
                updateStatus(statusText)
                statusText.postDelayed(this, 1000)
            }
        }, 1000)
        
        // 发送心跳
        sendHeartbeat()
        
        // 请求电池优化豁免
        requestIgnoreBatteryOptimization()
    }
    
    private fun updateStatus(statusText: TextView) {
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        val batteryOptimized = !isBatteryOptimizationIgnored()
        val deviceRemark = prefs.getString("device_remark", "未设置")
        
        val status = StringBuilder()
        status.append("📱 设备备注：$deviceRemark\n\n")
        
        if (accessibilityEnabled) {
            status.append("✅ 辅助功能：已启用\n")
        } else {
            status.append("❌ 辅助功能：未启用\n")
        }
        
        if (!batteryOptimized) {
            status.append("✅ 电池优化：已豁免\n")
        } else {
            status.append("⚠️ 电池优化：未豁免\n")
        }
        
        if (accessibilityEnabled && !batteryOptimized) {
            status.append("\n🎉 所有权限已就绪！")
        } else {
            status.append("\n⚠️ 请启用所有权限")
        }
        
        statusText.text = status.toString()
    }
    
    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "请找到「极速计算器」并启用", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开设置", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "无法请求电池优化豁免", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = ComponentName(this, KeywordMonitorService::class.java)
        val enabledServicesSetting = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledComponent = ComponentName.unflattenFromString(componentNameString)
            if (enabledComponent != null && enabledComponent == expectedComponentName) {
                return true
            }
        }
        return false
    }
    
    private fun isBatteryOptimizationIgnored(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(packageName)
        }
        return true
    }
    
    private fun sendHeartbeat() {
        scope.launch {
            val deviceId = prefs.getString("device_id", null) ?: run {
                val newId = java.util.UUID.randomUUID().toString()
                prefs.edit().putString("device_id", newId).apply()
                newId
            }
            
            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
            val deviceRemark = prefs.getString("device_remark", "") ?: ""
            
            apiClient.sendHeartbeat(deviceId, deviceName, Build.MODEL, deviceRemark)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

