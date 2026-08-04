package com.example.proklyatiemumii

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var tvCoins: TextView
    private lateinit var tvStats: TextView
    private lateinit var tvLog: TextView
    private lateinit var btnTap: Button
    private lateinit var btnUpgradeTap: Button
    private lateinit var btnUpgradeAuto: Button
    private lateinit var btnUpgradeLuck: Button

    private var coins = 0L
    private var tapLevel = 0
    private var autoLevel = 0
    private var luckLevel = 0

    private val random = Random.Default
    private val handler = Handler(Looper.getMainLooper())

    private val prefs by lazy {
        getSharedPreferences("proklyatie_mumii_save", MODE_PRIVATE)
    }

    private val goodMessages = arrayOf(
        "Мумия споткнулась, но нашла %s монет!",
        "Из саркофага посыпались монеты: +%s!",
        "Проклятие превратилось в золото: +%s!",
        "Мумия удачно ударилась о стену: +%s!",
        "Скарабеи принесли сокровища: +%s!"
    )

    private val critMessages = arrayOf(
        "КРИТ! Фараон расщедрился: +%s!",
        "КРИТ! Саркофаг лопнул от золота: +%s!",
        "КРИТ! Мумия сделала неудачное сальто, но зато +%s!"
    )

    private val badMessages = arrayOf(
        "Неудача! Мумия упала в ловушку: -%s монет.",
        "Неудача! Саркофаг прищемил монеты: -%s.",
        "Неудача! Проклятый кот украл: -%s.",
        "Неудача! Песчаная буря унесла: -%s.",
        "Неудача! Мумия перепутала гробницу: -%s."
    )

    private val autoTick = object : Runnable {
        override fun run() {
            if (autoLevel > 0) {
                coins += autoLevel.toLong()
                updateUi()
            }
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvCoins = findViewById(R.id.tvCoins)
        tvStats = findViewById(R.id.tvStats)
        tvLog = findViewById(R.id.tvLog)
        btnTap = findViewById(R.id.btnTap)
        btnUpgradeTap = findViewById(R.id.btnUpgradeTap)
        btnUpgradeAuto = findViewById(R.id.btnUpgradeAuto)
        btnUpgradeLuck = findViewById(R.id.btnUpgradeLuck)

        loadProgress()
        setupClicks()
        updateUi()

        tvLog.text = "Тапай по проклятой мумии и прокачивай удачу!"
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(autoTick)
        handler.post(autoTick)
    }

    override fun onPause() {
        handler.removeCallbacks(autoTick)
        saveProgress()
        super.onPause()
    }

    private fun setupClicks() {
        btnTap.setOnClickListener { view ->
            onMummyTap()
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }

        btnUpgradeTap.setOnClickListener {
            buyTapUpgrade()
        }

        btnUpgradeAuto.setOnClickListener {
            buyAutoUpgrade()
        }

        btnUpgradeLuck.setOnClickListener {
            buyLuckUpgrade()
        }
    }

    private fun onMummyTap() {
        animateTap()

        if (random.nextDouble() < badChance()) {
            val penalty = calculatePenalty()

            if (penalty > 0) {
                coins = max(0L, coins - penalty)
                tvLog.text = badMessages[random.nextInt(badMessages.size)]
                    .format(format(penalty))
            } else {
                tvLog.text = "Неудача! Но монет и так нет. Мумия плачет."
            }
        } else {
            var gain = tapPower()
            val isCrit = random.nextDouble() < critChance()

            if (isCrit) {
                gain *= 5L
            }

            coins += gain

            tvLog.text = if (isCrit) {
                critMessages[random.nextInt(critMessages.size)]
                    .format(format(gain))
            } else {
                goodMessages[random.nextInt(goodMessages.size)]
                    .format(format(gain))
            }
        }

        saveProgress()
        updateUi()
    }

    private fun calculatePenalty(): Long {
        if (coins <= 0) return 0L
        val base = coins / 10L
        return max(1L, base)
    }

    private fun buyTapUpgrade() {
        val cost = tapCost()

        if (coins >= cost) {
            coins -= cost
            tapLevel++
            tvLog.text = "Бинты укреплены! Теперь тап сильнее."
        } else {
            tvLog.text = "Не хватает монет на улучшение тапа."
        }

        saveProgress()
        updateUi()
    }

    private fun buyAutoUpgrade() {
        val cost = autoCost()

        if (coins >= cost) {
            coins -= cost
            autoLevel++
            tvLog.text = "Проклятый кот нанят! Монеты капают каждую секунду."
        } else {
            tvLog.text = "Не хватает монет на кота."
        }

        saveProgress()
        updateUi()
    }

    private fun buyLuckUpgrade() {
        val cost = luckCost()

        if (coins >= cost) {
            coins -= cost
            luckLevel++
            tvLog.text = "Амулет удачи сияет! Мумия стала чуть менее проклятой."
        } else {
            tvLog.text = "Не хватает монет на амулет."
        }

        saveProgress()
        updateUi()
    }

    private fun tapPower(): Long {
        return 1L + tapLevel
    }

    private fun badChance(): Double {
        // Базово 25% неудачи.
        // Каждый уровень удачи снижает шанс неудачи на 1%.
        // Минимальный шанс неудачи — 2%.
        return max(0.02, 0.25 - 0.01 * luckLevel)
    }

    private fun critChance(): Double {
        // Базово 5% крита.
        // Удача повышает шанс крита.
        return min(0.60, 0.05 + 0.015 * luckLevel)
    }

    private fun autoIncomePerSecond(): Long {
        return autoLevel.toLong()
    }

    private fun tapCost(): Long {
        return (25.0 * 1.6.pow(tapLevel.toDouble())).toLong()
    }

    private fun autoCost(): Long {
        return (50.0 * 1.7.pow(autoLevel.toDouble())).toLong()
    }

    private fun luckCost(): Long {
        return (100.0 * 2.0.pow(luckLevel.toDouble())).toLong()
    }

    private fun updateUi() {
        tvCoins.text = "Монеты: ${format(coins)}"

        val stats = "За тап: ${format(tapPower())} | " +
                "Крит: ${formatPercent(critChance())} | " +
                "Неудача: ${formatPercent(badChance())} | " +
                "Авто: ${format(autoIncomePerSecond())}/сек"

        tvStats.text = stats

        val tapCost = tapCost()
        val autoCost = autoCost()
        val luckCost = luckCost()

        btnUpgradeTap.text = "Улучшить тап\nУр. $tapLevel\nЦена: ${format(tapCost)}"
        btnUpgradeAuto.text = "Нанять кота\nУр. $autoLevel\nЦена: ${format(autoCost)}"
        btnUpgradeLuck.text = "Амулет удачи\nУр. $luckLevel\nЦена: ${format(luckCost)}"

        btnUpgradeTap.isEnabled = coins >= tapCost
        btnUpgradeAuto.isEnabled = coins >= autoCost
        btnUpgradeLuck.isEnabled = coins >= luckCost
    }

    private fun animateTap() {
        btnTap.animate().cancel()

        btnTap.scaleX = 0.94f
        btnTap.scaleY = 0.94f

        btnTap.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(80)
            .start()
    }

    private fun format(value: Long): String {
        return NumberFormat.getNumberInstance(Locale.getDefault()).format(value)
    }

    private fun formatPercent(value: Double): String {
        return String.format(Locale.getDefault(), "%.0f%%", value * 100)
    }

    private fun saveProgress() {
        prefs.edit()
            .putLong("coins", coins)
            .putInt("tapLevel", tapLevel)
            .putInt("autoLevel", autoLevel)
            .putInt("luckLevel", luckLevel)
            .apply()
    }

    private fun loadProgress() {
        coins = prefs.getLong("coins", 0L)
        tapLevel = prefs.getInt("tapLevel", 0)
        autoLevel = prefs.getInt("autoLevel", 0)
        luckLevel = prefs.getInt("luckLevel", 0)
    }
}
