package com.example.safelink

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var txtUrlAddress: TextView
    private lateinit var privacyCurtain: View

    private val liveBlocklist = HashSet<String>()

    // อัปเกรดฐานข้อมูล Blocklist
    private val brandProtectionMap = mapOf(
        // ธนาคาร
        "kbank" to "kasikornbank.com",
        "kasikorn" to "kasikornbank.com",
        "scb" to "scb.co.th",
        "krungthai" to "krungthai.com",
        "ktb" to "krungthai.com",
        "bangkokbank" to "bangkokbank.com",
        "bualuang" to "bangkokbank.com",
        "gsb" to "gsb.or.th",
        "ttb" to "ttbbank.com",

        // หน่วยงานรัฐ
        "rd.go.th" to "rd.go.th",
        "revenue" to "rd.go.th",
        "sso" to "sso.go.th",
        "police" to "royalthaipolice.go.th",
        "thaipoliceonline" to "thaipoliceonline.com",
        "dsi" to "dsi.go.th",
        "cyib" to "cyib.police.go.th",

        // ขนส่ง
        "thailandpost" to "thailandpost.co.th",
        "kex express" to "th.kex-express.com",
        "flash" to "flashexpress.co.th",
        "j&t" to "jtexpress.co.th",
        "dhl" to "dhl.com",

        // สาธารณูปโภค
        "pea" to "pea.co.th",
        "mea" to "mea.or.th",
        "mwa" to "mwa.co.th",
        "ais" to "ais.th",
        "true" to "true.th",
        "dtac" to "dtac.co.th",

        // E-commerce & Social
        "facebook" to "facebook.com",
        "line" to "line.me",
        "shopee" to "shopee.co.th",
        "lazada" to "lazada.co.th",
        "tiktok" to "tiktok.com"
    )

    // Suspicious TLDs
    private val suspiciousTlds = listOf(".cc", ".xyz", ".top", ".info", ".club", ".vip", ".pro", ".br")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Anti-Remote & Screenshot
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.myWebView)
        progressBar = findViewById(R.id.progressBar)
        txtUrlAddress = findViewById(R.id.txtUrlAddress)
        privacyCurtain = findViewById(R.id.privacyCurtain)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false

        settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        downloadBlocklist()

        webView.setDownloadListener { _, _, _, _, _ ->
            Toast.makeText(this, "⛔ BLOCKED: ไม่อนุญาตให้ดาวน์โหลดไฟล์!", Toast.LENGTH_LONG).show()
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
                txtUrlAddress.text = "⏳ Checking..."

                val (isSafe, reason) = checkUrlSafetyDetail(url)
                if (!isSafe) {
                    showDangerAlert(url, reason)
                    return true
                }

                if (url.startsWith("market://") || url.contains("play.google.com")) {
                    Toast.makeText(this@MainActivity, "🛡️ BLOCKED: ระบบป้องกันการเปิด Play Store", Toast.LENGTH_LONG).show()
                    return true
                }
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    Toast.makeText(this@MainActivity, "🛡️ BLOCKED: ระบบป้องกันการเปิดแอปภายนอก", Toast.LENGTH_LONG).show()
                    return true
                }
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                val (isSafe, reason) = checkUrlSafetyDetail(url ?: "")
                if (!isSafe) {
                    view?.stopLoading()
                    showDangerAlert(url ?: "", reason)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                scanPageContent(view)
            }
        }

        val data: Uri? = intent.data
        if (data != null) {
            webView.loadUrl(data.toString())
        } else {
            webView.loadUrl("https://www.google.com")
        }
    }


    // DOM SCANNER: สแกนเนื้อหา
    private fun scanPageContent(view: WebView?) {
        val jsCode = """
            (function() {
                var bodyText = document.body.innerText;
                
                // (Identity)
                var identityKeys = ["เลขบัตรประชาชน", "รหัสประจำตัวประชาชน", "วันเดือนปีเกิด", "เลขหลังบัตร", "Laser ID", "ถ่ายรูปหน้าบัตร"];
                
                // (Financial)
                var financeKeys = ["รหัส ATM", "PIN 6 หลัก", "รหัสผ่าน", "CVV", "CVC", "เลขหน้าบัตร", "วันหมดอายุ", "OTP", "รหัสยืนยัน"];
                
                // (Scam/Threats)
                var scamKeys = ["อายัดบัญชี", "ฟอกเงิน", "หมายศาล", "พัสดุตกค้าง", "โอนเงินเพื่อปลดล็อค", "รับเงินคืน", "คืนภาษี", "ค่าปรับ", "กู้เงินด่วน"];

                // ฟังก์ชันช่วยเช็คคำ
                function checkKeys(keys, category) {
                    for (var i = 0; i < keys.length; i++) {
                        if (bodyText.includes(keys[i])) {
                            return category + ":" + keys[i];
                        }
                    }
                    return null;
                }

                var found = checkKeys(identityKeys, "IDENTITY") || 
                           checkKeys(financeKeys, "FINANCE") || 
                           checkKeys(scamKeys, "SCAM");
                           
                if (found) return "WARNING:" + found;

                // เช็ค Input Password ในเว็บ HTTP
                var hasPasswordField = document.querySelector('input[type="password"]') !== null;
                var isHttps = window.location.protocol === 'https:';
                
                if (hasPasswordField && !isHttps) {
                    return "INSECURE_PASSWORD";
                }
                
                return "OK";
            })();
        """

        view?.evaluateJavascript(jsCode) { result ->
            val cleanResult = result.replace("\"", "")

            if (cleanResult.startsWith("WARNING")) {
                val parts = cleanResult.split(":")
                val category = parts[1] // IDENTITY, FINANCE, SCAM
                val keyword = parts[2]

                var msg = ""
                when (category) {
                    "IDENTITY" -> msg = "⚠️ ระวัง! เว็บนี้ขอข้อมูลส่วนตัวลึกซึ้ง ($keyword)\nเช็คให้ชัวร์ว่าเป็นเว็บราชการ/ธนาคารจริง"
                    "FINANCE" -> msg = "🚨 อันตราย! เว็บนี้ขอข้อมูลทางการเงิน ($keyword)\nห้ามกรอกรหัส OTP หรือ PIN เด็ดขาด!"
                    "SCAM" -> msg = "⚠️ ตรวจพบเนื้อหาต้องสงสัย ($keyword)\nอาจเป็นอุบายของมิจฉาชีพ (บัญชีม้า/พัสดุ/ภาษี)"
                }
                showPhishingWarning(msg)
            }
            else if (cleanResult == "INSECURE_PASSWORD") {
                showPhishingWarning("อันตราย! เว็บนี้ให้กรอกรหัสผ่าน แต่ไม่มีระบบเข้ารหัส (Not HTTPS)")
            }
        }
    }

    // LOGIC: ตรวจสอบความปลอดภัย
    private fun checkUrlSafetyDetail(url: String): Pair<Boolean, String> {
        val lowerUrl = url.lowercase()
        val uri = Uri.parse(url)
        val host = uri.host?.lowercase() ?: ""

        // ดักจับ Keyword เว็บพนัน
        val gamblingKeywords = listOf("bet", "slot", "casino", "baccarat", "ufa", "spin", "pgslot", "xo")
        for (keyword in gamblingKeywords) {
            // เช็คทั้งในชื่อเว็บ(Host) และในลิงก์(URL)
            if (host.contains(keyword) || lowerUrl.contains(keyword)) {
                return Pair(false, "⛔ ตรวจพบเนื้อหาการพนันออนไลน์ ($keyword)\nผิดกฎหมายและมีความเสี่ยงสูง")
            }
        }

        // ตรวจไฟล์อันตราย
        if (lowerUrl.endsWith(".apk") || lowerUrl.endsWith(".exe") || lowerUrl.endsWith(".ipa"))
            return Pair(false, "ตรวจพบไฟล์ติดตั้งแอปพลิเคชัน (.apk)")

        // ตรวจ IP Address
        val ipRegex = Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$""")
        if (ipRegex.matches(host)) return Pair(false, "เว็บไซต์ระบุด้วย IP Address (น่าสงสัย)")

        // ตรวจ GitHub Blocklist
        val cleanHost = host.removePrefix("www.")

        if (liveBlocklist.contains(host) || liveBlocklist.contains(cleanHost) || liveBlocklist.contains("www.$cleanHost")) {
            return Pair(false, "🚨 เว็บไซต์นี้อยู่ในบัญชีดำสากล (Global Blacklist)")
        }

        // ตรวจการแอบอ้าง
        for ((keyword, realDomain) in brandProtectionMap) {
            if (host.contains(keyword)) {
                if (!host.endsWith(realDomain)) {
                    return Pair(false, "⚠️ ตรวจพบการแอบอ้างชื่อหน่วยงาน '$keyword'\n(เว็บจริงต้องเป็น $realDomain เท่านั้น)")
                }
            }
        }

        return Pair(true, "")
    }

    private fun downloadBlocklist() {
        thread {
            try {
                val githubUrl = URL("https://raw.githubusercontent.com/mitchellkrogza/Phishing.Database/master/phishing-domains-ACTIVE.txt")
                val content = githubUrl.readText()
                val domains = content.lines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.trim() }
                runOnUiThread {
                    liveBlocklist.clear()
                    liveBlocklist.addAll(domains)
                    Toast.makeText(this, "✅ Database Updated: ${domains.size} รายการ", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) { }
        }
    }

    private fun updateUrlBar(url: String?, title: String?) {
        val u = url ?: ""
        try {
            val host = Uri.parse(u).host ?: title ?: "SafeLink"
            txtUrlAddress.text = "🔒 $host"
        } catch (e: Exception) {
            txtUrlAddress.text = title
        }
    }

    private fun showDangerAlert(url: String, reason: String) {
        if (!isFinishing) {
            AlertDialog.Builder(this)
                .setTitle("⚠️ ตรวจพบความเสี่ยง!")
                .setMessage("ระบบระงับการเข้าถึงเว็บไซต์นี้\n\nเหตุผล: $reason")
                .setCancelable(false)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("กลับหน้าหลัก") { _, _ ->
                    webView.loadUrl("https://www.google.com")
                }
                .show()
        }
    }

    private fun showPhishingWarning(message: String) {
        if (!isFinishing) {
            AlertDialog.Builder(this)
                .setTitle("⚠️ โปรดระวัง!")
                .setMessage(message)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setPositiveButton("ทราบแล้ว") { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        privacyCurtain.visibility = View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        privacyCurtain.postDelayed({
            privacyCurtain.visibility = View.GONE
        }, 300)
    }
}