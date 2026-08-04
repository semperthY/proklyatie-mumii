package com.example.proklyatiemumii

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
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
    val type: String,
    val value: Double,
    val baseCost: Long
)

class MainActivity : AppCompatActivity() {

    private lateinit var tvCoins: TextView
    private lateinit var tvStats: TextView
    private lateinit var tvLog: TextView
    private lateinit var ivMummy: ImageView
    private lateinit var flMummy: FrameLayout
    private lateinit var btnUpgradeTap: Button
    private lateinit var btnUpgradeAuto: Button
    private lateinit var btnUpgradeLuck: Button
    private lateinit var tvArtifactStats: TextView
    private lateinit var layoutArtifacts: LinearLayout
    private lateinit var particleView: ParticleView
    private lateinit var parallax: ParallaxView
    private lateinit var btnMute: ImageView

    private val sound = SoundManager(this)
    private var effectsEnabled = true
    private var muted = false

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

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BATTERY_LOW -> setEffects(false)
                Intent.ACTION_BATTERY_OKAY -> setEffects(true)
            }
        }
    }

    private val artifacts = listOf(
        ArtifactDef(0, "Песчаная роза", "Редкий цветок пустыни", "LUCK", 2.0, 50L),
        ArtifactDef(1, "Скарабей удачи", "Приносит удачу владельцу", "FIND", 2.0, 70L),
        ArtifactDef(2, "Осколок саркофага", "Хранит древнюю силу", "TAP", 5.0, 100L),
        ArtifactDef(3, "Амулет Анубиса", "Защита от проклятий", "SHIELD", 5.0, 120L),
        ArtifactDef(4, "Золотая маска", "Маска фараона", "GOLD", 10.0, 150L),
        ArtifactDef(5, "Камень солнца", "Сияет в темноте гробницы", "CRIT", 5.0, 200L),
        ArtifactDef(6, "Посох жреца", "Магический посох", "AUTO", 50.0, 250L),
        ArtifactDef(7, "Лунный кристалл", "Хранит лунный свет", "CRITMULT", 0.5, 300L),
        ArtifactDef(8, "Око Гора", "Всевидящее око", "LUCK", 3.0, 400L),
        ArtifactDef(9, "Корона фараона", "Символ власти", "GOLD", 15.0, 500L),
        ArtifactDef(10, "Сердце пирамиды", "Сердце древней силы", "TAP", 15.0, 700L),
        ArtifactDef(11, "Печать проклятия", "Запечатывает зло", "SHIELD", 10.0, 1000L)
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
                coins += autoIncome()
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
        ivMummy = findViewById(R.id.ivMummy)
        flMummy = findViewById(R.id.flMummy)
        btnUpgradeTap = findViewById(R.id.btnUpgradeTap)
        btnUpgradeAuto = findViewById(R.id.btnUpgradeAuto)
        btnUpgradeLuck = findViewById(R.id.btnUpgradeLuck)
        tvArtifactStats = findViewById(R.id.tvArtifactStats)
        layoutArtifacts = findViewById(R.id.layoutArtifacts)
        particleView = findViewById(R.id.particleView)
        parallax = findViewById(R.id.parallax)
        btnMute = findViewById(R.id.btnMute)

        muted = prefs.getBoolean("muted", false)
        sound.muted = muted
        updateMuteIcon()
        sound.init()

        btnMute.setOnClickListener {
            muted = !muted
            sound.muted = muted
            prefs.edit().putBoolean("muted", muted).apply()
            updateMuteIcon()
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
        }
        registerReceiver(batteryReceiver, filter)

        loadProgress()
        setupClicks()
        updateUi()
        updateArtifactsTable()

        tvLog.text = "Собери все 12 артефактов ОДНОВРЕМЕННО!"
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(autoTick)
        handler.post(autoTick)
        if (effectsEnabled) {
            ivMummy.startAnimation(AnimationUtils.loadAnimation(this, R.anim.wobble))
        }
    }

    override fun onPause() {
        handler.removeCallbacks(autoTick)
        ivMummy.clearAnimation()
        saveProgress()
        super.onPause()
    }

    override fun onStop() {
        saveProgress()
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        sound.release()
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val w = parallax.width
        if (w > 0) parallax.setShift((ev.x / w) * 2f - 1f)
        return super.dispatchTouchEvent(ev)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        saveProgress()
        val intent = Intent(this, ExitActivity::class.java)
        intent.putExtra("coins", coins)
        intent.putExtra("found", artifactsCollected.size)
        intent.putExtra("owned", artifactsOwned.size)
        startActivity(intent)
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    private fun setupClicks() {
        ivMummy.setOnClickListener { view ->
            onMummyTap()
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }

        btnUpgradeTap.setOnClickListener { buyTapUpgrade() }
        btnUpgradeAuto.setOnClickListener { buyAutoUpgrade() }
        btnUpgradeLuck.setOnClickListener { buyLuckUpgrade() }
    }

    private fun onMummyTap() {
        mummySquash()

        val foundArtifact = tryFindArtifact()

        if (random.nextDouble() < badChance()) {
            val penalty = calculatePenalty()
            sound.play("bad")
            burstAtMummy(Color.parseColor("#FF5252"), 10)
            if (penalty > 0) {
                coins = max(0L, coins - penalty)
                spawnFloatingText("-" + format(penalty), "#FF5252")
                val lostId = loseRandomArtifact()
                val baseMsg = badMessages[random.nextInt(badMessages.size)].format(format(penalty))
                tvLog.text = if (lostId != null) {
                    "$baseMsg\n💀 Потерян артефакт: ${artifacts[lostId].name}! Он ещё вернётся..."
                } else {
                    baseMsg
                }
            } else {
                tvLog.text = "Неудача! Но монет и так нет. Мумия плачет."
            }
        } else {
            var gain = tapPower()
            gain = (gain * goldMultiplier()).roundToLong()
            gain = (gain * comboMultiplier()).roundToLong()

            val isCrit = random.nextDouble() < critChance()
            if (isCrit) gain = (gain * critMultiplier()).roundToLong()

            coins += gain

            sound.play(if (isCrit) "crit" else "tap", if (isCrit) 0.6f else 0.3f)
            burstAtMummy(Color.parseColor("#FFD54F"), if (isCrit) 16 else 6)
            spawnFloatingText("+" + format(gain), if (isCrit) "#FFEB3B" else "#FFD54F")

            tvLog.text = if (isCrit) {
                critMessages[random.nextInt(critMessages.size)].format(format(gain))
            } else {
                goodMessages[random.nextInt(goodMessages.size)].format(format(gain))
            }

            if (foundArtifact != null) {
                sound.play("artifact")
                spawnFloatingText("🏺 АРТЕФАКТ!", "#FFAB40")
                tvLog.text = tvLog.text.toString() + "\n🏺 Найден артефакт: ${artifacts[foundArtifact].name}!"
            }
        }

        saveProgress()
        updateUi()
        updateArtifactsTable()
    }

    private fun tryFindArtifact(): Int? {
        if (random.nextDouble() >= artifactFindChance()) return null

        val pool = artifacts.filter { !artifactsOwned.contains(it.id) }
        if (pool.isEmpty()) return null

        val a = pool[random.nextInt(pool.size)]
        if (!artifactsCollected.contains(a.id)) {
            artifactsCollected.add(a.id)
            artifactLevels[a.id] = 1
        }
        artifactsOwned.add(a.id)
        return a.id
    }

    private fun loseRandomArtifact(): Int? {
        if (artifactsOwned.isEmpty()) return null
        val list = artifactsOwned.toList()
        val lost = list[random.nextInt(list.size)]
        artifactsOwned.remove(lost)
        return lost
    }

    private fun sumOwned(type: String): Double {
        var s = 0.0
        for (a in artifacts) {
            if (a.type == type && artifactsOwned.contains(a.id)) {
                s += a.value * (artifactLevels[a.id] ?: 1)
            }
        }
        return s
    }

    private fun tapPower(): Long {
        return 1L + tapLevel + sumOwned("TAP").roundToLong()
    }

    private fun badChance(): Double {
        return max(0.02, 0.25 - 0.01 * luckLevel - sumOwned("LUCK") / 100.0)
    }

    private fun critChance(): Double {
        return min(0.85, 0.05 + 0.015 * luckLevel + sumOwned("CRIT") / 100.0)
    }

    private fun critMultiplier(): Double {
        return 5.0 + sumOwned("CRITMULT")
    }

    private fun artifactFindChance(): Double {
        return min(0.50, 0.08 + 0.01 * luckLevel + sumOwned("FIND") / 100.0)
    }

    private fun goldMultiplier(): Double {
        return 1.0 + sumOwned("GOLD") / 100.0
    }

    private fun autoBonus(): Double {
        return 1.0 + sumOwned("AUTO") / 100.0
    }

    private fun comboMultiplier(): Double {
        return max(1.0, artifactsOwned.size.toDouble())
    }

    private fun autoIncome(): Long {
        return (autoLevel * autoBonus() * goldMultiplier()).roundToLong()
    }

    private fun calculatePenalty(): Long {
        if (coins <= 0) return 0L
        val shield = sumOwned("SHIELD") / 100.0
        val percent = max(0.05, 0.10 + 0.05 * artifactsOwned.size - shield)
        return max(1L, (coins * percent).roundToLong())
    }

    private fun buyTapUpgrade() {
        val cost = tapCost()
        if (coins >= cost) {
            coins -= cost
            tapLevel++
            sound.play("coin")
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
            sound.play("coin")
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
            sound.play("coin")
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
            sound.play("artifact")
            tvLog.text = "✨ ${artifacts[id].name} улучшен до ур. ${artifactLevels[id]}!"
        } else {
            tvLog.text = "Не хватает монет на улучшение артефакта."
        }
        saveProgress()
        updateUi()
        updateArtifactsTable()
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
        return (artifacts[id].baseCost * 1.5.pow((artifactLevels[id] ?: 0).toDouble())).toLong()
    }

    private fun updateUi() {
        tvCoins.text = format(coins)

        val stats = "За тап: ${format(tapPower())} | Крит: ${formatPercent(critChance())} " +
                "(x${fmt1(critMultiplier())}) | Неудача: ${formatPercent(badChance())}\n" +
                "Авто: ${format(autoIncome())}/сек | Комбо: x${artifactsOwned.size.coerceAtLeast(1)} | " +
                "Монеты: +${formatPercent(sumOwned("GOLD"))}"

        tvStats.text = stats

        val tapCost = tapCost()
        val autoCost = autoCost()
        val luckCost = luckCost()

        btnUpgradeTap.text = " Улучшить тап (ур. $tapLevel) — ${format(tapCost)}"
        btnUpgradeAuto.text = "🐈 Нанять кота (ур. $autoLevel) — ${format(autoCost)}"
        btnUpgradeLuck.text = "🏺 Амулет удачи (ур. $luckLevel) — ${format(luckCost)}"

        btnUpgradeTap.isEnabled = coins >= tapCost
        btnUpgradeAuto.isEnabled = coins >= autoCost
        btnUpgradeLuck.isEnabled = coins >= luckCost
    }

    private fun passiveValueText(def: ArtifactDef, level: Int): String {
        val v = def.value * level
        return when (def.type) {
            "TAP" -> "+${v.roundToLong()} к тапу"
            "CRITMULT" -> "+${fmt1(v)}x к силе крита"
            "LUCK" -> "+${fmt1(v)}% удачи"
            "FIND" -> "+${fmt1(v)}% к поиску артефактов"
            "SHIELD" -> "+${fmt1(v)}% защиты от потерь"
            "GOLD" -> "+${fmt1(v)}% к монетам"
            "CRIT" -> "+${fmt1(v)}% к шансу крита"
            "AUTO" -> "+${fmt1(v)}% к автодоходу"
            else -> ""
        }
    }

    private fun setEffects(on: Boolean) {
        effectsEnabled = on
        particleView.setEnabledEffects(on)
        if (on) {
            ivMummy.startAnimation(AnimationUtils.loadAnimation(this, R.anim.wobble))
        } else {
            ivMummy.clearAnimation()
            tvLog.text = "🔋 Низкий заряд: эффекты выключены для экономии батареи"
        }
    }

    private fun updateMuteIcon() {
        btnMute.setImageResource(if (muted) R.drawable.ic_sound_off else R.drawable.ic_sound_on)
    }

    private fun burstAtMummy(color: Int, count: Int) {
        if (!effectsEnabled) return
        val a = IntArray(2)
        val b = IntArray(2)
        ivMummy.getLocationOnScreen(a)
        particleView.getLocationOnScreen(b)
        val cx = a[0] + ivMummy.width / 2f - b[0]
        val cy = a[1] + ivMummy.height / 2f - b[1]
        particleView.burst(cx, cy, color, count)
    }

    private fun mummySquash() {
        ivMummy.animate().cancel()
        ivMummy.scaleX = 0.90f
        ivMummy.scaleY = 0.94f
        ivMummy.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(120)
            .start()
    }

    private fun spawnFloatingText(text: String, color: String) {
        val tv = TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor(color))
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
        }
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
        flMummy.addView(tv, params)
        tv.y = flMummy.height / 2f
        tv.x = (flMummy.width / 2f) - 40 + random.nextInt(80)
        tv.animate()
            .translationYBy(-260f)
            .alpha(0f)
            .setDuration(900)
            .withEndAction { flMummy.removeView(tv) }
            .start()
    }

    private fun updateArtifactsTable() {
        layoutArtifacts.removeAllViews()

        val foundCount = artifactsCollected.size
        val ownedCount = artifactsOwned.size
        tvArtifactStats.text =
            "На руках: $ownedCount / 12 | Комбо: x${ownedCount.coerceAtLeast(1)} | Найдено всего: $foundCount"

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
                setBackgroundResource(R.drawable.bg_panel)
                val p = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 8 }
                layoutParams = p
            }

            val tvName = TextView(this).apply {
                val icon = if (isOwned) "✅" else "💀"
                text = "$icon ${def.name} (ур. $level)"
                setTextColor(Color.parseColor(if (isOwned) "#FFD54F" else "#B0BEC5"))
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
            }
            container.addView(tvName)

            val tvDesc = TextView(this).apply {
                text = def.description
                setTextColor(Color.parseColor("#B0BEC5"))
                textSize = 12f
                setPadding(0, 4, 0, 8)
            }
            container.addView(tvDesc)

            val actionRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val tvInfo = TextView(this).apply {
                text = if (isOwned) passiveValueText(def, level) else "Потерян — выпадет снова"
                setTextColor(Color.parseColor(if (isOwned) "#A5D6A7" else "#90A4AE"))
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            actionRow.addView(tvInfo)

            val btnUpgrade = Button(this).apply {
                text = if (isOwned) "✨ ${format(cost)}" else "Ждём..."
                textSize = 12f
                setBackgroundResource(R.drawable.btn_gold)
                setTextColor(Color.parseColor("#3E2B00"))
                isEnabled = isOwned && coins >= cost
                setOnClickListener { if (isOwned) upgradeArtifact(id) }
                minimumWidth = 0
                minWidth = 0
                setPadding(24, 0, 24, 0)
            }
            actionRow.addView(btnUpgrade)

            container.addView(actionRow)
            layoutArtifacts.addView(container)
        }

        if (ownedCount == 12) {
            val tv = TextView(this).apply {
                text = "🏆 ВСЕ 12 АРТЕФАКТОВ ОДНОВРЕМЕННО! ПРОКЛЯТИЕ СНЯТО!"
                setTextColor(Color.parseColor("#FFEB3B"))
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(0, 16, 0, 0)
            }
            layoutArtifacts.addView(tv)
        }
    }

    private fun fmt1(v: Double): String {
        return String.format(Locale.getDefault(), "%.1f", v)
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
