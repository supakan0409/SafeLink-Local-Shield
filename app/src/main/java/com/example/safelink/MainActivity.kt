package com.example.safelink

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.telephony.SmsManager
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var txtUrlAddress: TextView
    private lateinit var privacyCurtain: View
    private lateinit var btnSettings: ImageButton
    private lateinit var btnHistory: ImageButton

    private val liveBlocklist = HashSet<String>()
    private var guardianPhone: String? = null
    private var currentLang: String = "th"

    private val SMS_PERMISSION_CODE = 101

    private val trustedDomains = listOf(
        "google.com", "facebook.com", "instagram.com", "twitter.com", "youtube.com", "pantip.com",
        "kasikornbank.com", "scb.co.th", "krungthai.com", "bangkokbank.com", "ttbbank.com",
        "shopee.co.th", "lazada.co.th", "netflix.com", "sanook.com", "kapook.com"
    )

    private val brandProtectionMap = mapOf(
        "kbank" to "kasikornbank.com", "scb" to "scb.co.th", "krungthai" to "krungthai.com",
        "thailandpost" to "thailandpost.co.th", "flash" to "flashexpress.co.th", "kerry" to "th.kerryexpress.com",
        "paypal" to "paypal.com", "apple" to "apple.com", "microsoft" to "microsoft.com"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.myWebView)
        progressBar = findViewById(R.id.progressBar)
        txtUrlAddress = findViewById(R.id.txtUrlAddress)
        privacyCurtain = findViewById(R.id.privacyCurtain)
        btnSettings = findViewById(R.id.btnSettings)
        btnHistory = findViewById(R.id.btnHistory)

        val prefs = getSharedPreferences("SafeLinkPrefs", Context.MODE_PRIVATE)
        guardianPhone = prefs.getString("guardian_phone", "")
        currentLang = prefs.getString("language", "th") ?: "th"

        btnSettings.setOnClickListener {
            authenticateUser {
                showSettingsDialog()
            }
        }
        btnHistory.setOnClickListener { showHistoryDialog() }

        checkAndRequestSmsPermission()

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        downloadBlocklist()

        webView.setDownloadListener { url, _, _, mimetype, _ ->
            handleDownload(url, mimetype)
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
            }
            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                updateUrlBar(view?.url, title)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                txtUrlAddress.text = getTxt("analyzing")

                val status = checkUrlSafetyDetail(url)

                when (status.action) {
                    Action.BLOCK -> {
                        sendAutoSmsToGuardian(status.reason)
                        showBlockDialog(url, status.reason)
                        saveThreatToLog(url, status.reason)
                        return true
                    }
                    Action.WARN -> {
                        saveThreatToLog(url, status.reason + " (Warning)")
                        showWarningDialog(url, status.reason, view)
                        return true
                    }
                    Action.SAFE -> {
                        if (!url.startsWith("http")) {
                            Toast.makeText(this@MainActivity, getTxt("redirect_blocked"), Toast.LENGTH_SHORT).show()
                            return true
                        }
                        return false
                    }
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                val status = checkUrlSafetyDetail(url ?: "")
                if (status.action == Action.BLOCK) {
                    view?.stopLoading()
                    sendAutoSmsToGuardian(status.reason)
                    showBlockDialog(url ?: "", status.reason)
                    saveThreatToLog(url ?: "", status.reason)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!isTrustedDomain(url)) {
                    scanPageContent(view)
                }
            }
        }

        val data: Uri? = intent.data
        if (data != null) webView.loadUrl(data.toString()) else webView.loadUrl("https://www.google.com")
    }

    // Biometric Authentication
    private fun authenticateUser(onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS) {
                        Toast.makeText(applicationContext, getTxt("biometric_bypass"), Toast.LENGTH_SHORT).show()
                        onSuccess()
                    } else {
                        Toast.makeText(applicationContext, "Authentication Error: $errString", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(applicationContext, getTxt("biometric_fail"), Toast.LENGTH_SHORT).show()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getTxt("title_biometric"))
            .setSubtitle(getTxt("msg_biometric"))
            .setNegativeButtonText(getTxt("btn_cancel"))
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    // Threat History System
    private fun saveThreatToLog(url: String, reason: String) {
        val prefs = getSharedPreferences("SafeLinkHistory", Context.MODE_PRIVATE)
        val historySet = prefs.getStringSet("history", HashSet<String>())?.toMutableSet() ?: HashSet<String>()
        val timeStamp = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())
        val logEntry = "$timeStamp|$reason|$url"
        historySet.add(logEntry)
        prefs.edit().putStringSet("history", historySet).apply()
    }

    private fun showHistoryDialog() {
        val prefs = getSharedPreferences("SafeLinkHistory", Context.MODE_PRIVATE)
        val historySet = prefs.getStringSet("history", HashSet<String>()) ?: HashSet<String>()
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getTxt("title_history"))
        if (historySet.isEmpty()) {
            builder.setMessage(getTxt("msg_no_history"))
        } else {
            val listItems = historySet.toList().sortedDescending().map { entry ->
                val parts = entry.split("|")
                if (parts.size >= 3) "🕒 ${parts[0]}\n🚨 ${parts[1]}\n🔗 ${parts[2]}" else entry
            }.toTypedArray()
            builder.setItems(listItems, null)
            builder.setNeutralButton(getTxt("btn_clear_history")) { _, _ ->
                prefs.edit().clear().apply()
                Toast.makeText(this, "History Cleared!", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setPositiveButton("OK", null)
        builder.show()
    }

    // Localization
    private fun getTxt(key: String, vararg args: Any): String {
        val text = strings[currentLang]?.get(key) ?: strings["en"]?.get(key) ?: key
        return try { String.format(text, *args) } catch (e: Exception) { text }
    }

    private val strings = mapOf(
        "th" to mapOf(
            "title_biometric" to "ยืนยันตัวตน",
            "msg_biometric" to "สแกนลายนิ้วมือเพื่อเข้าสู่การตั้งค่า",
            "biometric_fail" to "สแกนไม่ผ่าน กรุณาลองใหม่",
            "biometric_bypass" to "ไม่พบเซ็นเซอร์ (ข้ามการตรวจสอบ)",
            "title_history" to "📜 ประวัติความเสี่ยง",
            "msg_no_history" to "✅ ปลอดภัย! ยังไม่พบประวัติภัยคุกคาม",
            "btn_clear_history" to "ล้างประวัติ",
            "analyzing" to "⏳ กำลังวิเคราะห์...",
            "redirect_blocked" to "⚠️ บล็อกการเปิดแอปภายนอก",
            "download_blocked" to "⛔ บล็อกไฟล์อันตราย (.apk/.exe)",
            "download_ok" to "⬇️ กำลังเปิดไฟล์...",
            "download_fail" to "ไม่รองรับไฟล์ประเภทนี้",
            "guardian_alert_msg" to "[SafeLink Alert] 🚨 พบภัยคุกคามบนเครื่องพ่อแม่: %s",
            "title_block" to "⛔ ระงับการเข้าถึง",
            "msg_block" to "SafeLink ปกป้องคุณจากภัยคุกคาม\nเหตุผล: %s\n\n✅ ระบบได้ส่ง SMS แจ้งเตือนผู้ดูแลเรียบร้อยแล้ว",
            "title_warn" to "⚠️ คำเตือนความปลอดภัย",
            "msg_warn" to "เว็บไซต์นี้อาจไม่ปลอดภัย\nเหตุผล: %s\n\nคุณต้องการดำเนินการต่อหรือไม่?",
            "btn_back_safety" to "กลับสู่ความปลอดภัย",
            "btn_notify" to "โทรหาผู้ดูแล",
            "btn_go_back" to "ย้อนกลับ (แนะนำ)",
            "btn_proceed" to "ยอมรับความเสี่ยง",
            "title_settings" to "⚙️ ตั้งค่า",
            "hint_phone" to "เบอร์โทรผู้ดูแล",
            "lbl_lang" to "ภาษา",
            "btn_save" to "บันทึก",
            "btn_cancel" to "ยกเลิก",
            "toast_saved" to "บันทึกเรียบร้อย!",
            "reason_malware" to "ไฟล์ติดตั้งอันตราย (.apk)",
            "reason_ip" to "URL เป็นหมายเลข IP น่าสงสัย",
            "reason_blacklist" to "เว็บไซต์อยู่ในบัญชีดำสากล",
            "reason_brand" to "แอบอ้างชื่อแบรนด์ (%s)",
            "reason_gamble" to "เนื้อหาการพนัน (%s)",
            "warn_suspicious" to "⚠️ พบคำต้องสงสัย: '%s'",
            "warn_insecure" to "⚠️ ห้ามกรอกรหัสผ่าน (เว็บไม่ปลอดภัย)"
        ),
        "en" to mapOf(
            "title_biometric" to "Authentication Required",
            "msg_biometric" to "Scan fingerprint to access settings",
            "biometric_fail" to "Authentication failed",
            "biometric_bypass" to "Biometrics not set (Skipping)",
            "title_history" to "📜 Threat History Log",
            "msg_no_history" to "✅ Safe! No threats recorded yet.",
            "btn_clear_history" to "Clear History",
            "analyzing" to "⏳ Analyzing...",
            "redirect_blocked" to "⚠️ External app redirect blocked",
            "download_blocked" to "⛔ Blocked dangerous file (.apk/.exe)",
            "download_ok" to "⬇️ Opening file...",
            "download_fail" to "Cannot download this file type",
            "guardian_alert_msg" to "[SafeLink Alert] 🚨 Threat blocked on device: %s",
            "title_block" to "⛔ Access Denied",
            "msg_block" to "SafeLink protected you from a threat.\nReason: %s\n\n✅ Guardian has been notified via SMS.",
            "title_warn" to "⚠️ Security Warning",
            "msg_warn" to "This site may be unsafe.\nReason: %s\n\nDo you want to proceed?",
            "btn_back_safety" to "Back to Safety",
            "btn_notify" to "Call Guardian",
            "btn_go_back" to "Go Back (Recommended)",
            "btn_proceed" to "Proceed Anyway",
            "title_settings" to "⚙️ Settings",
            "hint_phone" to "Guardian Phone Number",
            "lbl_lang" to "Language",
            "btn_save" to "Save",
            "btn_cancel" to "Cancel",
            "toast_saved" to "Settings saved!",
            "reason_malware" to "Malicious file download (.apk)",
            "reason_ip" to "Suspicious IP Address URL",
            "reason_blacklist" to "Global Blacklisted Site",
            "reason_brand" to "Brand Impersonation (%s)",
            "reason_gamble" to "Gambling content detected (%s)",
            "warn_suspicious" to "⚠️ Suspicious content found: '%s'",
            "warn_insecure" to "⚠️ Unsafe Connection! Do not enter password."
        )
    )

    private fun handleDownload(url: String, mimetype: String?) {
        val lowerUrl = url.lowercase()
        if (lowerUrl.endsWith(".apk") || lowerUrl.endsWith(".exe") || lowerUrl.endsWith(".bat") || lowerUrl.endsWith(".sh")) {
            val reason = getTxt("reason_malware")
            sendAutoSmsToGuardian(reason)
            showBlockDialog(url, reason)
            saveThreatToLog(url, reason)
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
            Toast.makeText(this, getTxt("download_ok"), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getTxt("download_fail"), Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndRequestSmsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), SMS_PERMISSION_CODE)
        }
    }

    private fun sendAutoSmsToGuardian(reason: String) {
        if (guardianPhone.isNullOrEmpty()) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            try {
                val smsManager = SmsManager.getDefault()
                val message = getTxt("guardian_alert_msg", reason)
                smsManager.sendTextMessage(guardianPhone, null, message, null, null)
                runOnUiThread { Toast.makeText(this, "📨 Guardian Notified!", Toast.LENGTH_LONG).show() }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun checkUrlSafetyDetail(url: String): SafetyStatus {
        val lowerUrl = url.lowercase()
        val uri = Uri.parse(url)
        val host = uri.host?.lowercase() ?: ""

        if (isTrustedDomain(url)) return SafetyStatus(Action.SAFE, "")

        if (lowerUrl.endsWith(".apk") || lowerUrl.endsWith(".exe")) return SafetyStatus(Action.BLOCK, getTxt("reason_malware"))
        val ipRegex = Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$""")
        if (ipRegex.matches(host)) return SafetyStatus(Action.BLOCK, getTxt("reason_ip"))

        val cleanHost = host.removePrefix("www.")
        if (liveBlocklist.contains(host) || liveBlocklist.contains(cleanHost)) return SafetyStatus(Action.BLOCK, getTxt("reason_blacklist"))

        for ((keyword, realDomain) in brandProtectionMap) {
            if (host.contains(keyword) && !host.endsWith(realDomain)) return SafetyStatus(Action.BLOCK, getTxt("reason_brand", keyword))
        }

        val gamblingKeywords = listOf("bet", "slot", "casino", "baccarat", "ufa", "spin")
        for (keyword in gamblingKeywords) {
            if (host.contains(keyword) || lowerUrl.contains(keyword)) return SafetyStatus(Action.WARN, getTxt("reason_gamble", keyword))
        }
        return SafetyStatus(Action.SAFE, "")
    }

    private fun isTrustedDomain(url: String?): Boolean {
        val host = Uri.parse(url ?: "").host?.lowercase() ?: return false
        return trustedDomains.any { host.endsWith(it) }
    }

    // Dom Scanner
    // --- 🕵️ SMART DOM SCANNER (อัปเกรดใหม่!) ---
    private fun scanPageContent(view: WebView?) {
        val jsCode = """
            (function() {
                var bodyText = document.body.innerText.toLowerCase();
                var htmlContent = document.body.innerHTML.toLowerCase(); // ใช้เช็ค input name
                
                // IDENTITY 
                var identityKeys = [
                    "เลขบัตรประชาชน", "รหัสประจำตัวประชาชน", "เลขหลังบัตร", "วันเดือนปีเกิด", 
                    "ssn", "social security", "passport number", "driver license", "mother's maiden name"
                ];
                
                // FINANCIAL 
                var financeKeys = [
                    "รหัส atm", "pin 6 หลัก", "รหัสผ่าน", "เลขหน้าบัตร", "วันหมดอายุ", "รหัสยืนยัน", 
                    "credit card", "debit card", "cvv", "cvc", "card number", "expiration date", "otp code", "bank account password"
                ];
                
                // SCAM/URGENCY
                var scamKeys = [
                    "อายัดบัญชี", "ฟอกเงิน", "หมายศาล", "พัสดุตกค้าง", "โอนเงินเพื่อปลดล็อค", "รับเงินคืน", 
                    "verify account", "account suspended", "urgent action", "unusual activity", "tax refund", "irs", "prize winner", "lottery"
                ];

                // TECH SUPPORT SCAM
                var techKeys = [
                    "virus detected", "computer infected", "call microsoft", "toll free", "มัลแวร์", "ไวรัส", "โทรหาเจ้าหน้าที่"
                ];

                function check(keywords, category) {
                    for (var i = 0; i < keywords.length; i++) {
                        if (bodyText.includes(keywords[i])) return category + ":" + keywords[i];
                    }
                    return null;
                }

                var result = check(identityKeys, "IDENTITY") || 
                             check(financeKeys, "FINANCE") || 
                             check(scamKeys, "SCAM") ||
                             check(techKeys, "TECH_SCAM");
                             
                if (result) return "WARNING:" + result;

                // INPUT FIELD ANALYSIS
                var isHttps = window.location.protocol === 'https:';
                if (!isHttps) {
                    if (document.querySelector('input[type="password"]')) return "INSECURE:PASSWORD";
                    if (document.querySelector('input[name*="card"]') || 
                        document.querySelector('input[name*="cvv"]') || 
                        document.querySelector('input[id*="cc_number"]')) {
                        return "INSECURE:SENSITIVE_INPUT";
                    }
                }
                
                return "OK";
            })();
        """

        view?.evaluateJavascript(jsCode) { result ->
            val cleanResult = result.replace("\"", "")

            if (cleanResult.startsWith("WARNING")) {
                val parts = cleanResult.split(":")
                if (parts.size >= 3) {
                    val category = parts[1]
                    val keyword = parts[2]
                    var msgKey = "warn_suspicious"
                    when (category) {
                        "IDENTITY" -> msgKey = "warn_identity"
                        "FINANCE" -> msgKey = "warn_finance"
                        "SCAM" -> msgKey = "warn_scam"
                        "TECH_SCAM" -> msgKey = "warn_tech_scam"
                    }
                    val msg = getTxt(msgKey, keyword)
                    showPhishingWarning(msg)
                    saveThreatToLog(view?.url ?: "", "Content Scan: $category ($keyword)")
                }
            }
            else if (cleanResult.startsWith("INSECURE")) {
                val msg = getTxt("warn_insecure")
                showPhishingWarning(msg)
                saveThreatToLog(view?.url ?: "", "Insecure HTTP Input")
            }
        }
    }

    enum class Action { SAFE, WARN, BLOCK }
    data class SafetyStatus(val action: Action, val reason: String)

    private fun showBlockDialog(url: String, reason: String) {
        if (!isFinishing) {
            AlertDialog.Builder(this)
                .setTitle(getTxt("title_block"))
                .setMessage(getTxt("msg_block", reason))
                .setCancelable(false)
                //.setIcon(android.R.drawable.ic_delete)
                .setPositiveButton(getTxt("btn_back_safety")) { _, _ -> webView.loadUrl("https://www.google.com") }
                .setNeutralButton(getTxt("btn_notify")) { _, _ -> try { val i = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$guardianPhone")); startActivity(i) } catch(e:Exception){} }
                .show()
        }
    }

    private fun showWarningDialog(url: String, reason: String, view: WebView?) {
        if (!isFinishing) {
            AlertDialog.Builder(this)
                .setTitle(getTxt("title_warn"))
                .setMessage(getTxt("msg_warn", reason))
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(getTxt("btn_go_back")) { _, _ -> webView.loadUrl("https://www.google.com") }
                .setNegativeButton(getTxt("btn_proceed")) { _, _ -> view?.loadUrl(url) }
                .show()
        }
    }

    private fun showSettingsDialog() {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(60, 40, 60, 20)

        val lblPhone = TextView(this)
        lblPhone.text = "📞 " + getTxt("hint_phone")
        lblPhone.textSize = 16f
        lblPhone.setTextColor(Color.DKGRAY)
        layout.addView(lblPhone)

        val inputPhone = EditText(this)
        inputPhone.setText(guardianPhone)
        inputPhone.setPadding(0, 20, 0, 20)
        layout.addView(inputPhone)

        val lblLang = TextView(this)
        lblLang.text = "\n🌐 " + getTxt("lbl_lang")
        lblLang.textSize = 18f
        lblLang.setTypeface(null, Typeface.BOLD)
        lblLang.setTextColor(Color.parseColor("#2E7D32"))
        lblLang.setPadding(0, 30, 0, 10)
        layout.addView(lblLang)

        val radioGroup = RadioGroup(this)
        radioGroup.orientation = RadioGroup.HORIZONTAL
        radioGroup.setPadding(0, 10, 0, 0)

        val rbThai = RadioButton(this)
        rbThai.text = "🇹🇭 ไทย"
        rbThai.textSize = 16f
        val rbEng = RadioButton(this)
        rbEng.text = "🇺🇸 English"
        rbEng.textSize = 16f
        rbEng.setPadding(40, 0, 0, 0)

        radioGroup.addView(rbThai)
        radioGroup.addView(rbEng)

        if (currentLang == "en") rbEng.isChecked = true else rbThai.isChecked = true
        layout.addView(radioGroup)

        AlertDialog.Builder(this)
            .setTitle(getTxt("title_settings"))
            .setView(layout)
            .setPositiveButton(getTxt("btn_save")) { _, _ ->
                guardianPhone = inputPhone.text.toString()
                currentLang = if (rbEng.isChecked) "en" else "th"
                getSharedPreferences("SafeLinkPrefs", Context.MODE_PRIVATE).edit().putString("guardian_phone", guardianPhone).putString("language", currentLang).apply()
                Toast.makeText(this, getTxt("toast_saved"), Toast.LENGTH_SHORT).show()
                checkAndRequestSmsPermission()
            }
            .setNegativeButton(getTxt("btn_cancel"), null)
            .show()
    }

    private fun showPhishingWarning(msg: String) {
        if (!isFinishing) AlertDialog.Builder(this).setTitle(getTxt("title_warn")).setMessage(msg).setPositiveButton("OK", null).show()
    }
    private fun downloadBlocklist() {
        thread {
            try {
                val url = URL("https://raw.githubusercontent.com/mitchellkrogza/Phishing.Database/master/phishing-domains-ACTIVE.txt")
                val list = url.readText().lines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.trim() }
                runOnUiThread { liveBlocklist.addAll(list) }
            } catch (e: Exception) {}
        }
    }
    private fun updateUrlBar(url: String?, title: String?) {
        try { txtUrlAddress.text = "🔒 " + (Uri.parse(url ?: "").host ?: title ?: "SafeLink") } catch (e: Exception) {}
    }
    override fun onUserLeaveHint() { super.onUserLeaveHint(); privacyCurtain.visibility = View.VISIBLE }
    override fun onResume() { super.onResume(); privacyCurtain.postDelayed({ privacyCurtain.visibility = View.GONE }, 300) }
}