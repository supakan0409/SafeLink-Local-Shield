# <img width="50" alt="Image" src="https://github.com/user-attachments/assets/91b8cc50-5e90-43ee-be0e-34f58f9991b4" /> SafeLink Local Shield 

### The Zero-Trust Sandbox Browser

SafeLink Local Shield is an advanced Android security browser designed to protect users—especially the elderly and vulnerable groups—from mobile banking malware, phishing links, and remote access scams. It operates on a **"Zero-Trust"** principle, analyzing every URL and web content in real-time before it can harm the device.

## ✨ Key Features

### Core Protection

* **🚫 Zero-Download Sandbox:** Automatically blocks malicious file downloads (e.g., .apk, .exe, .bat) to prevent malware installation, while allowing safe files (PDF, Images).

* **🌍 Real-time Threat Intelligence:** Syncs with global phishing databases (800,000+ active entries) to block known malicious sites instantly.

* **⚡ Local-First Heuristics:** Detects typosquatting (fake bank URLs like kbarnk.com), IP-based URLs, and gambling sites using on-device logic.

### Advanced Detection 

* **🧠 DOM Content Inspector:** Scans webpage content for suspicious keywords (e.g., "OTP", "Transfer Money", "รหัส ATM", "พัสดุตกค้าง") and detects insecure password fields on HTTP sites.

* **🔐 Biometric Security Lock:** Secures critical settings (Guardian Phone/Language) with Fingerprint/Face Unlock to prevent unauthorized changes.

### Privacy Care

* **🚨 Guardian Alert System:** Automatically sends an SMS notification to a trusted contact (child/guardian) immediately when a threat is detected.

* **📜 Threat History Log:** detailed logs of blocked threats for forensic review.

* **🛡️ Privacy Curtain & Anti-Spy:** Prevents screen capturing (screenshots) and hides app content from remote control tools or when switching apps.

## 📱 Screenshots

### Splash Screen

<img src="https://github.com/user-attachments/assets/7beccb2f-4ec1-475b-9ab5-4c540113e0fe" width="200">

### Security Alert

<img width="200" alt="Image" src="https://github.com/user-attachments/assets/56675df8-e10b-49c4-9d97-8088436d6b62" /> <img width="200" alt="Image" src="https://github.com/user-attachments/assets/deb27e7b-8a76-41fb-8abf-87e990f7d0c4" /> <img width="200" alt="Image" src="https://github.com/user-attachments/assets/41297575-cd10-41ab-9e6f-7041e4631931" />
### Privacy Curtain

<img width="200" alt="Image" src="https://github.com/user-attachments/assets/1b35a26d-cf55-457a-a57b-b8f6558b1140" />


## 🛠️ Tech Stack

* **Language:** Kotlin

* **Platform:** Native Android (MVVM pattern elements)

* **Security:**
  * WebViewClient Hardening
  * WindowManager.LayoutParams.FLAG_SECURE
  * Android Biometric API
  * Runtime Permission Handling (SMS)

* **Data Source:** GitHub Phishing Database (Open Source Intelligence)

## 📥 Installation

1. Download the latest APK from the Releases section.

2. Install on your Android device.

3. Set SafeLink as your Default Browser for maximum protection.

4. Grant SMS Permission to enable the Guardian Alert feature.

### ⚠️ Disclaimer

This project is a Proof of Concept (PoC) developed for educational purposes and as a technical demonstration of Android security hardening.

---
**Developed by Supakan** | © 2026 All Rights Reserved.
