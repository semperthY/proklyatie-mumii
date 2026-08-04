package com.example.proklyatiemumii

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        findViewById<View>(R.id.splashRoot)
            .startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in))

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MenuActivity::class.java))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            finish()
        }, 2200)
    }
}

class MenuActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)

        findViewById<ImageView>(R.id.ivMenuMummy)
            .startAnimation(AnimationUtils.loadAnimation(this, R.anim.wobble))

        findViewById<TextView>(R.id.tvMenuTitle)
            .startAnimation(AnimationUtils.loadAnimation(this, R.anim.pop_in))

        findViewById<Button>(R.id.btnPlay).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }
    }
}

class ExitActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exit)

        val coins = intent.getLongExtra("coins", 0L)
        val found = intent.getIntExtra("found", 0)
        val owned = intent.getIntExtra("owned", 0)

        findViewById<TextView>(R.id.tvExitStats).text =
            "💰 Монеты: $coins\n🏺 Найдено артефактов: $found / 12\n✅ На руках: $owned"

        findViewById<Button>(R.id.btnContinue).setOnClickListener { finish() }

        findViewById<Button>(R.id.btnExit).setOnClickListener {
            finishAffinity()
        }
    }
}
