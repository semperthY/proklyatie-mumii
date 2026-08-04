package com.example.proklyatiemumii

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.random.Random

data class ArtifactDef(
    val id: Int,
    val name: String,
    val description: String,
    val baseBonus: Long
)

class MainActivity : AppCompatActivity() {

    private lateinit var tvCoins: TextView
    private lateinit var tvStats: TextView
    private lateinit var tvLog: TextView
    private lateinit var btnTap: Button
    private lateinit var btnUpgradeTap: Button
    private lateinit var btnUpgradeAuto: Button
    private lateinit var btnUpgradeLuck: Button
    private lateinit var tvArtifactStats: TextView
    private lateinit var layoutArtifacts: LinearLayout

    private var coins = 0L
    private var tapLevel = 0
    private var autoLevel = 0
    private var luckLevel = 0

    private val artifactsCollected = mutableSetOf<Int>()
    private val artifactsOwned = mutableSetOf<Int>()
    private val artifactLevels = mutableMapOf<Int, Int>()

    private val random = Random.Default
    private val handler = Handler(Looper.getMainLooper())

    private val prefs by lazy {
        getSharedPreferences("proklyatie_mumii_save", MODE_PRIVATE)
    }

    private val artifacts = listOf(
        ArtifactDef(0, "Песчаная роза", "Редкий цветок пустыни", 5L),
        ArtifactDef(1, "Скарабей удачи", "Приносит удачу владельцу", 7L),
        ArtifactDef(2, "Осколок саркофага", "Хранит древнюю силу", 10L),
        ArtifactDef(3, "Амулет Анубиса", "Защита от проклятий", 12L),
        ArtifactDef(4, "Золотая маска", "Маска фараона", 15L),
        ArtifactDef(5, "Камень солнца", "Сияет в темноте гробницы", 20L),
        ArtifactDef(6, "Посох жреца", "Магический посох", 25L),
        ArtifactDef(7, "Лунный кристалл", "Хранит лунный свет", 30L),
        ArtifactDef(8, "Око Гора", "Всевидящее око", 40L),
        ArtifactDef(9, "Корона фараона", "Символ власти", 50L),
        ArtifactDef(10, "Сердце пирамиды", "Сердце древней силы", 70L),
        ArtifactDef(11, "Печать проклятия", "Запечатывает зло", 100L)
    )

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
        "КРИТ! Мумия сделала сальто, но зато +%s!"
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
        tvArtifactStats = findViewById(R.id.tvArtifactStats)
        layoutArtifacts = findViewById(R.id.layoutArtifacts)

        loadProgress()
        setupClicks()
        updateUi()
        updateArtifactsTable()

        tvLog.text = "Тапай по проклятой мумии и собирай 12 артефактов!"
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

    override fun onStop() {
        saveProgress()
        super.onStop()
    }

    private fun setupClicks() {
        btnTap.setOnClickListener { view ->
            onMummyTap()
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }

        btnUpgradeTap.setOnClickListener { buyTapUpgrade() }
        btnUpgradeAuto.setOnClickListener { buyAutoUpgrade() }
        btnUpgradeLuck.setOnClickListener { buyLuckUpgrade() }
    }

    private fun onMummyTap() {
        animateTap()

        val foundArtifact = tryFindArtifact()

        if (random.nextDouble() < badChance()) {
            val penalty = calculatePenalty()
            if (penalty > 0) {
                coins = max(0L, coins - penalty)
                val lostId = loseRandomArtifact()
                val baseMsg = badMessages[random.nextInt(badMessages.size)].format(format(penalty))
                tvLog.text = if (lostId != null) {
                    "$baseMsg\n💀 Потерян артефакт: ${artifacts[lostId].name}!"
                } else {
                    baseMsg
                }
            } else {
                tvLog.text = "Неудача! Но монет и так нет. Мумия плачет."
            }
        } else {
            var gain = tapPower()
            gain = applyCombo(gain)

            val isCrit = random.nextDouble() < critChance()
            if (isCrit) gain *= 5L

            coins += gain

            tvLog.text = if (isCrit) {
                critMessages[random.nextInt(critMessages.size)].format(format(gain))
            } else {
                goodMessages[random.nextInt(goodMessages.size)].format(format(gain))
            }

            if (foundArtifact != null) {
                tvLog.text = tvLog.text.toString() + "\n🏺 Найден артефакт: ${artifacts[foundArtifact].name}!"
            }
        }

        saveProgress()
        updateUi()
        updateArtifactsTable()
    }

    private fun tryFindArtifact(): Int? {
        val chance = artifactFindChance()
        if (random.nextDouble() >= chance) return null

        val unfound = artifacts.filter { !artifactsCollected.contains(it.id) }
        if (unfound.isNotEmpty()) {
            val artifact = unfound[random.nextInt(unfound.size)]
            artifactsCollected.add(artifact.id)
            artifactsOwned.add(artifact.id)
            artifactLevels[artifact.id] = 1
            return artifact.id
        }

        val lost = artifacts.filter {
            artifactsCollected.contains(it.id) && !artifactsOwned.contains(it.id)
        }
        if (lost.isNotEmpty()) {
            val artifact = lost[random.nextInt(lost.size)]
            artifactsOwned.add(artifact.id)
            return artifact.id
        }

        return null
    }

    private fun loseRandomArtifact(): Int? {
        if (artifactsOwned.isEmpty()) return null
        val list = artifactsOwned.toList()
        val lost = list[random.nextInt(list.size)]
        artifactsOwned.remove(lost)
        return lost
    }

    private fun artifactFindChance(): Double {
        return min(0.40, 0.08 + 0.01 * luckLevel)
    }

    private fun calculatePenalty(): Long {
        if (coins <= 0) return 0L
        val percent = 0.10 + 0.05 * artifactsOwned.size
        val penalty = (coins * percent).roundToLong()
        return max(1L, penalty)
    }

    private fun tapPower(): Long {
        var power = 1L + tapLevel
        for ((_, lvl) in artifactLevels) {
            power += lvl
        }
        return power
    }

    private fun applyCombo(gain: Long): Long {
        val multiplier = 1.0 + 0.10 * artifactsOwned.size
        return (gain * multiplier).roundToLong()
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
        updateArtifactsTable()
    }

    private fun upgradeArtifact(id: Int) {
        val cost = artifactUpgradeCost(id)
        if (coins >= cost) {
            coins -= cost
            artifactLevels[id] = (artifactLevels[id] ?: 0) + 1
            tvLog.text = "✨ Артефакт ${artifacts[id].name} улучшен до ур. ${artifactLevels[id]}!"
        } else {
            tvLog.text = "Не хватает монет на улучшение артефакта."
        }
        saveProgress()
        updateUi()
        updateArtifactsTable()
    }

    private fun badChance(): Double {
        return max(0.02, 0.25 - 0.01 * luckLevel)
    }

    private fun critChance(): Double {
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

    private fun artifactUpgradeCost(id: Int): Long {
        val base = artifacts[id].baseBonus * 10
        val lvl = artifactLevels[id] ?: 0
        return (base * 1.5.pow(lvl.toDouble())).toLong()
    }

    private fun updateUi() {
        tvCoins.text = "Монеты: ${format(coins)}"

        val comboPercent = artifactsOwned.size * 10
        val penaltyBonus = artifactsOwned.size * 5

        val stats = "За тап: ${format(tapPower())} | " +
                "Крит: ${formatPercent(critChance())} | " +
                "Неудача: ${formatPercent(badChance())}\n" +
                "Авто: ${format(autoIncomePerSecond())}/сек | " +
                "Комбо: +${comboPercent}% | Проклятие: +${penaltyBonus}%"

        tvStats.text = stats

        val tapCost = tapCost()
        val autoCost = autoCost()
        val luckCost = luckCost()

        btnUpgradeTap.text = "Улучшить тап\nУр. $tapLevel | Цена: ${format(tapCost)}"
        btnUpgradeAuto.text = "Нанять кота\nУр. $autoLevel | Цена: ${format(autoCost)}"
        btnUpgradeLuck.text = "Амулет удачи\nУр. $luckLevel | Цена: ${format(luckCost)}"

        btnUpgradeTap.isEnabled = coins >= tapCost
        btnUpgradeAuto.isEnabled = coins >= autoCost
        btnUpgradeLuck.isEnabled = coins >= luckCost
    }

    private fun updateArtifactsTable() {
        layoutArtifacts.removeAllViews()

        val foundCount = artifactsCollected.size
        val ownedCount = artifactsOwned.size
        tvArtifactStats.text = "Найдено: $foundCount / 12 | На руках: $ownedCount"

        if (artifactsCollected.isEmpty()) {
            val tv = TextView(this).apply {
                text = "Артефакты ещё не найдены. Тапай и надейся на удачу!"
                setTextColor(Color.parseColor("#90A4AE"))
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(0, 16, 0, 16)
            }
            layoutArtifacts.addView(tv)
            return
        }

        val sortedIds = artifactsCollected.sorted()
        for (id in sortedIds) {
            val def = artifacts[id]
            val isOwned = artifactsOwned.contains(id)
            val level = artifactLevels[id] ?: 1
            val cost = artifactUpgradeCost(id)

            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 12, 16, 12)
                setBackgroundColor(
                    if (isOwned) Color.parseColor("#2D1F0F") else Color.parseColor("#1A1108")
                )
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 8 }
                layoutParams = params
            }

            val titleRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val statusIcon = if (isOwned) "✅" else "💀"
            val nameColor = if (isOwned) "#FFD54F" else "#9E9E9E"

            val tvName = TextView(this).apply {
                text = "$statusIcon ${def.name} (ур. $level)"
                setTextColor(Color.parseColor(nameColor))
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            titleRow.addView(tvName)
            container.addView(titleRow)

            val tvDesc = TextView(this).apply {
                text = def.description
                setTextColor(Color.parseColor("#B0BEC5"))
                textSize = 12f
                setPadding(0, 4, 0, 8)
            }
            container.addView(tvDesc)

            val actionRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val tvInfo = TextView(this).apply {
                text = if (isOwned) "Даёт +$level к тапу" else "Потерян"
                setTextColor(Color.parseColor("#B0BEC5"))
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                gravity = Gravity.CENTER_VERTICAL
            }
            actionRow.addView(tvInfo)

            val btnUpgrade = Button(this).apply {
                text = if (isOwned) "+${format(cost)}" else "Найти"
                textSize = 12f
                isEnabled = isOwned && coins >= cost
                setOnClickListener {
                    if (isOwned) upgradeArtifact(id)
                }
                minWidth = 0
                minimumWidth = 0
                setPadding(24, 0, 24, 0)
            }
            actionRow.addView(btnUpgrade)

            container.addView(actionRow)
            layoutArtifacts.addView(container)
        }

        if (foundCount == 12 && ownedCount == 12) {
            val tv = TextView(this).apply {
                text = "🏆 ВСЕ 12 АРТЕФАКТОВ СОБРАНЫ! ПРОКЛЯТИЕ СНЯТО!"
                setTextColor(Color.parseColor("#FFEB3B"))
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(0, 16, 0, 0)
            }
            layoutArtifacts.addView(tv)
        }
    }

    private fun animateTap() {
        btnTap.animate().cancel()
        btnTap.scaleX = 0.94f
        btnTap.scaleY = 0.94f
        btnTap.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
    }

    private fun format(value: Long): String {
        return NumberFormat.getNumberInstance(Locale.getDefault()).format(value)
    }

    private fun formatPercent(value: Double): String {
        return String.format(Locale.getDefault(), "%.0f%%", value * 100)
    }

    private fun saveProgress() {
        val collectedStr = artifactsCollected.joinToString(",")
        val ownedStr = artifactsOwned.joinToString(",")
        val levelsStr = artifactLevels.entries.joinToString(",") { "${it.key}:${it.value}" }

        prefs.edit()
            .putLong("coins", coins)
            .putInt("tapLevel", tapLevel)
            .putInt("autoLevel", autoLevel)
            .putInt("luckLevel", luckLevel)
            .putString("artifactsCollected", collectedStr)
            .putString("artifactsOwned", ownedStr)
            .putString("artifactLevels", levelsStr)
            .apply()
    }

    private fun loadProgress() {
        coins = prefs.getLong("coins", 0L)
        tapLevel = prefs.getInt("tapLevel", 0)
        autoLevel = prefs.getInt("autoLevel", 0)
        luckLevel = prefs.getInt("luckLevel", 0)

        prefs.getString("artifactsCollected", "")?.let {
            if (it.isNotBlank()) {
                artifactsCollected.clear()
                it.split(",").forEach { s -> s.toIntOrNull()?.let { id -> artifactsCollected.add(id) } }
            }
        }
        prefs.getString("artifactsOwned", "")?.let {
            if (it.isNotBlank()) {
                artifactsOwned.clear()
                it.split(",").forEach { s -> s.toIntOrNull()?.let { id -> artifactsOwned.add(id) } }
            }
        }
        prefs.getString("artifactLevels", "")?.let {
            if (it.isNotBlank()) {
                artifactLevels.clear()
                it.split(",").forEach { entry ->
                    val parts = entry.split(":")
                    if (parts.size == 2) {
                        val id = parts[0].toIntOrNull()
                        val lvl = parts[1].toIntOrNull()
                        if (id != null && lvl != null) artifactLevels[id] = lvl
                    }
                }
            }
        }
    }
}
