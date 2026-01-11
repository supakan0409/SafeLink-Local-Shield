package com.example.safelink

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // ตั้งเวลา 2 วินาที (2000ms) แล้วค่อยกระโดดไปหน้าหลัก
        Handler(Looper.getMainLooper()).postDelayed({
            // สร้าง Intent เพื่อเปิด MainActivity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)

            finish()
        }, 2000)
    }
}