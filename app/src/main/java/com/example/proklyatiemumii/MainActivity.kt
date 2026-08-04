package com.example.proklyatiemumii

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.CheckBox
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
    val id: Int, val name: String, val description: String,
    val type: String, val value: Double, val baseCost: Long
)

data class SkillDef(val emoji: String, val name: String, val desc: String, val cost: Int, val duration: Long)
data class PerkDef(val emoji: String, val name: String, val desc: String)

class MainActivity : AppCompatActivity() {

    private lateinit var tvHeadCoins: TextView
    private lateinit var tvHeadClicks: TextView
    private lateinit var tvHeadArts: TextView
    private lateinit var btnSettings: TextView
    private lateinit var btnRebirth: TextView
    private lateinit var tvStats: TextView
    private lateinit var tvLog: TextView
    private lateinit var ivMummy: ImageView
    private lateinit var flMummy: FrameLayout
    private lateinit var tvMana: TextView
    private lateinit var btnUpgradeTap: Button
    private lateinit var btnUpgradeAuto: Button
    private lateinit var btnUpgradeLuck: Button
    private lateinit var tvArtifactStats: TextView
    private lateinit var layoutArtifacts: LinearLayout
    private lateinit var particleView: ParticleView
    private lateinit var parallax: ParallaxView
    private lateinit var layoutSettings: LinearLayout
    private lateinit var cbSound: CheckBox
    private lateinit var cbVibration: CheckBox
    private lateinit var layoutRebirth: LinearLayout
    private lateinit var tvRebirthText: TextView
    private val skillViews = mutableListOf<TextView>()

    private val sound = SoundManager(this)
    private var effectsEnabled = true
    private var muted = false
    private var vibrationEnabled = true

    private var coins = 0L
    private var tapLevel = 0
    private var autoLevel = 0
    private var luckLevel = 0
    private var totalClicks = 0L
    private var rebirths = 0
    private var mana = 0.0
    private val perks = mutableSetOf<Int>()

    private val artifactsCollected = mutableSetOf<Int>()
    private val artifactsOwned = mutableSetOf<Int>()
    private val artifactLevels = mutableMapOf<Int, Int>()
    private val effectUntil = mutableMapOf<String, Long>()
    private var rebirthOffered = false

    private val random = Random.Default
    private val handler = Handler(Looper.getMainLooper())

    private val prefs by lazy { getSharedPreferences("proklyatie_mumii_save", MODE_PRIVATE) }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BATTERY_LOW -> setEffects(false)
                Intent.ACTION_BATTERY_OKAY -> setEffects(true)
            }
        }
    }

    private val artifacts = listOf(
        ArtifactDef(0, "Песчаная роза", "Редкий цветок пустыни", "LUCK", 1.0, 50L),
        ArtifactDef(1, "Скарабей удачи", "Приносит удачу владельцу", "FIND", 1.0, 70L),
        ArtifactDef(2, "Осколок саркофага", "Хранит древнюю силу", "TAP", 2.0, 100L),
        ArtifactDef(3, "Амулет Анубиса", "Защита от проклятий", "SHIELD", 2.0, 120L),
        ArtifactDef(4, "Золотая маска", "Маска фараона", "GOLD", 5.0, 150L),
        ArtifactDef(5, "Камень солнца", "Сияет в темноте гробницы", "CRIT", 2.0, 200L),
        ArtifactDef(6, "Посох жреца", "Магический посох", "AUTO", 20.0, 250L),
        ArtifactDef(7, "Лунный кристалл", "Хранит лунный свет", "CRITMULT", 0.25, 300L),
        ArtifactDef(8, "Око Гора", "Всевидящее око", "LUCK", 1.5, 400L),
        ArtifactDef(9, "Корона фараона", "Символ власти", "GOLD", 7.0, 500L),
        ArtifactDef(10, "Сердце пирамиды", "Сердце древней силы", "TAP", 6.0, 700L),
        ArtifactDef(11, "Печать проклятия", "Запечатывает зло", "SHIELD", 4.0, 1000L)
    )

    private val skillKeys = arrayOf("autoclick", "find", "gold", "shield", "crit", "")
    private val skills = listOf(
        SkillDef("⚡", "Автоклик", "Автоматически кликает по мумии 10 раз/сек в течение 30 сек", 50, 30000),
        SkillDef("🏺", "Поиск артефактов", "Шанс найти артефакт x3 в течение 15 сек", 40, 15000),
        SkillDef("💰", "Золотая лихорадка", "Удваивает получение монет на 20 сек", 40, 20000),
        SkillDef("🛡️", "Щит Анубиса", "Защищает от неудач на 20 сек", 60, 20000),
        SkillDef("⚔️", "Ярость фараона", "Каждый тап становится критом на 15 сек", 60, 15000),
        SkillDef("✨", "Благословение", "Мгновенно делает 150 тапов с текущей силой", 30, 0)
    )

    private val perkDefs = listOf(
        PerkDef("💰", "Благословение фараона", "+30% к монетам глобально"),
        PerkDef("⚡", "Быстрые руки", "+50% к силе тапа"),
        PerkDef("🍀", "Фортуна богов", "-30% к шансу неудачи"),
        PerkDef("⚔️", "Око бури", "+25% к шансу крита"),
        PerkDef("💥", "Мощь крита", "+2x к силе крита"),
        PerkDef("🐈", "Кошачий пакт", "+50% к автодоходу"),
        PerkDef("🏺", "Магнит песков", "+50% к поиску артефактов"),
        PerkDef("🛡️", "Бессмертие", "-30% к потерям при неудачах"),
        PerkDef("🔮", "Колодец маны", "+50 к макс. мане, +1 реген/сек"),
        PerkDef("⏳", "Власть времени", "+50% к длительности умений"),
        PerkDef("🎁", "Дар оазиса", "бонусы появляются в 2 раза чаще"),
        PerkDef("💎", "Богатство гробницы", "+500 монет за каждое перерождение"),
        PerkDef("🌙", "Лунная поступь", "x2 маны за тап"),
        PerkDef("☀️", "Солнечный дар", "пассивки артефактов +50%"),
        PerkDef("🌀", "Реинкарнация", "+10% монет за каждое перерождение")
    )

    private val pickupEmojis = arrayOf("⚡", "", "🍀", "🔮", "", "⚔️")

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
            mana = min(manaMax().toDouble(), mana + regenPerSec())
            handler.postDelayed(this, 1000L)
        }
    }

    private var autoLoopRunning = false
    private val autoClickTick = object : Runnable {
        override fun run() {
            if (eff("autoclick")) {
                doTap(true)
                handler.postDelayed(this, 100L)
            } else {
                autoLoopRunning = false
            }
        }
    }

    private val pickupSpawner = object : Runnable {
        override fun run() {
            spawnPickup()
            val base = 20000L + (random.nextDouble() * 25000).toLong()
            val mult = if (pk(10)) 0.5 else 1.0
            handler.postDelayed(this, (base * mult).toLong())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvHeadCoins = findViewById(R.id.tvHeadCoins)
        tvHeadClicks = findViewById(R.id.tvHeadClicks)
        tvHeadArts = findViewById(R.id.tvHeadArts)
        btnSettings = findViewById(R.id.btnSettings)
        btnRebirth = findViewById(R.id.btnRebirth)
        tvStats = findViewById(R.id.tvStats)
        tvLog = findViewById(R.id.tvLog)
        ivMummy = findViewById(R.id.ivMummy)
        flMummy = findViewById(R.id.flMummy)
        tvMana = findViewById(R.id.tvMana)
        btnUpgradeTap = findViewById(R.id.btnUpgradeTap)
        btnUpgradeAuto = findViewById(R.id.btnUpgradeAuto)
        btnUpgradeLuck = findViewById(R.id.btnUpgradeLuck)
        tvArtifactStats = findViewById(R.id.tvArtifactStats)
        layoutArtifacts = findViewById(R.id.layoutArtifacts)
        particleView = findViewById(R.id.particleView)
        parallax = findViewById(R.id.parallax)
        layoutSettings = findViewById(R.id.layoutSettings)
        cbSound = findViewById(R.id.cbSound)
        cbVibration = findViewById(R.id.cbVibration)
        layoutRebirth = findViewById(R.id.layoutRebirth)
        tvRebirthText = findViewById(R.id.tvRebirthText)

        for (id in listOf(R.id.tvSkill0, R.id.tvSkill1, R.id.tvSkill2, R.id.tvSkill3, R.id.tvSkill4, R.id.tvSkill5)) {
            skillViews.add(findViewById(id))
        }

        muted = !prefs.getBoolean("sound", true)
        vibrationEnabled = prefs.getBoolean("vibration", true)
        sound.muted = muted
        sound.init()

        cbSound.isChecked = !muted
        cbVibration.isChecked = vibrationEnabled

        cbSound.setOnCheckedChangeListener { _, on ->
            muted = !on
            sound.muted = muted
            prefs.edit().putBoolean("sound", on).apply()
        }

        cbVibration.setOnCheckedChangeListener { _, on ->
            vibrationEnabled = on
            prefs.edit().putBoolean("vibration", on).apply()
        }

        findViewById<Button>(R.id.btnReset).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Сбросить прогресс?")
                .setMessage("Все монеты, уровни, артефакты и перки будут потеряны. Ты уверен?")
                .setPositiveButton("ДА, СБРОСИТЬ") { _, _ -> resetAll() }
                .setNegativeButton("Отмена", null)
                .show()
        }

        findViewById<Button>(R.id.btnSettingsClose).setOnClickListener {
            layoutSettings.visibility = View.GONE
        }

        btnSettings.setOnClickListener {
            cbSound.isChecked = !muted
            cbVibration.isChecked = vibrationEnabled
            layoutSettings.visibility = View.VISIBLE
        }

        btnRebirth.setOnClickListener { showRebirth() }
        findViewById<Button>(R.id.btnRebirthYes).setOnClickListener { doRebirth() }
        findViewById<Button>(R.id.btnRebirthNo).setOnClickListener { layoutRebirth.visibility = View.GONE }

        skillViews.forEachIndexed { i, v ->
            v.setOnClickListener { castSkill(i) }
            v.setOnLongClickListener {
                val s = skills[i]
                AlertDialog.Builder(this)
                    .setTitle("${s.emoji} ${s.name}")
                    .setMessage("${s.desc}\n\nЦена: ${s.cost} маны" +
                            if (s.duration > 0) "\nДлительность: ${s.duration / 1000} сек" else "")
                    .setPositiveButton("ОК", null)
                    .show()
                true
            }
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

        tvLog.text = "Собери 12 артефактов одновременно — и переродись!"
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(autoTick)
        handler.post(autoTick)
        handler.removeCallbacks(pickupSpawner)
        handler.postDelayed(pickupSpawner, 15000)
        if (effectsEnabled) {
            ivMummy.startAnimation(AnimationUtils.loadAnimation(this, R.anim.wobble))
        }
    }

    override fun onPause() {
        handler.removeCallbacks(autoTick)
        handler.removeCallbacks(pickupSpawner)
        handler.removeCallbacks(autoClickTick)
        autoLoopRunning = false
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
        if (layoutSettings.visibility == View.VISIBLE) {
            layoutSettings.visibility = View.GONE
            return
        }
        if (layoutRebirth.visibility == View.VISIBLE) {
            layoutRebirth.visibility = View.GONE
            return
        }
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
            doTap(false)
            if (vibrationEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        btnUpgradeTap.setOnClickListener { buyTapUpgrade() }
        btnUpgradeAuto.setOnClickListener { buyAutoUpgrade() }
        btnUpgradeLuck.setOnClickListener { buyLuckUpgrade() }
    }

    private fun vibrate(ms: Long) {
        if (!vibrationEnabled) return
        try {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            val v = vm?.defaultVibrator ?: return
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        } catch (_: Exception) {}
    }

    private fun eff(key: String): Boolean {
        return System.currentTimeMillis() < (effectUntil[key] ?: 0L)
    }

    private fun setEff(key: String, ms: Long) {
        effectUntil[key] = System.currentTimeMillis() + (ms * skillDurMult()).toLong()
    }

    private fun pk(id: Int): Boolean = perks.contains(id)

    private fun doTap(auto: Boolean) {
        mummySquash()

        if (!auto) {
            totalClicks++
            mana = min(manaMax().toDouble(), mana + manaPerTap())
        }

        val foundArtifact = tryFindArtifact()
        val isBad = !eff("shield") && random.nextDouble() < badChance()

        if (isBad) {
            val penalty = calculatePenalty()
            sound.play("bad")
            vibrate(80)
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

            val isCrit = eff("crit") || random.nextDouble() < critChance()
            if (isCrit) gain = (gain * critMultiplier()).roundToLong()

            coins += gain

            sound.play(if (isCrit) "crit" else "tap", if (isCrit) 0.6f else 0.3f)
            if (isCrit) vibrate(40)
            burstAtMummy(Color.parseColor("#FFD54F"), if (isCrit) 16 else 6)
            spawnFloatingText("+" + format(gain), if (isCrit) "#FFEB3B" else "#FFD54F")

            tvLog.text = if (isCrit) {
                critMessages[random.nextInt(critMessages.size)].format(format(gain))
            } else {
                goodMessages[random.nextInt(goodMessages.size)].format(format(gain))
            }

            if (foundArtifact != null) {
                sound.play("artifact")
                vibrate(60)
                spawnFloatingText("🏺 АРТЕФАКТ!", "#FFAB40")
                tvLog.text = tvLog.text.toString() + "\n🏺 Найден артефакт: ${artifacts[foundArtifact].name}!"
            }
        }

        if (artifactsOwned.size == 12 && !rebirthOffered) {
            showRebirth()
        }

        saveProgress()
        updateUi()
        updateArtifactsTable()
    }

    private fun castSkill(i: Int) {
        val s = skills[i]
        if (mana < s.cost) {
            tvLog.text = "🔮 Не хватает маны для «${s.name}»"
            return
        }
        mana -= s.cost
        sound.play("artifact")
        vibrate(50)
        when (i) {
            0 -> { setEff("autoclick", s.duration); startAutoLoop() }
            1 -> setEff("find", s.duration)
            2 -> setEff("gold", s.duration)
            3 -> setEff("shield", s.duration)
            4 -> setEff("crit", s.duration)
            5 -> {
                val gain = (150 * tapPower() * goldMultiplier()).roundToLong()
                coins += gain
                spawnFloatingText("+" + format(gain), "#FFEB3B")
            }
        }
        tvLog.text = "${s.emoji} Умение «${s.name}» активировано!"
        saveProgress()
        updateUi()
    }

    private fun startAutoLoop() {
        if (!autoLoopRunning) {
            autoLoopRunning = true
            handler.post(autoClickTick)
        }
    }

    private fun spawnPickup() {
        if (flMummy.width < 100 || flMummy.height < 100) return
        val idx = random.nextInt(pickupEmojis.size)
        val tv = TextView(this).apply {
            text = pickupEmojis[idx]
            textSize = 26f
            setPadding(8, 8, 8, 8)
        }
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        flMummy.addView(tv, params)
        tv.x = random.nextFloat() * (flMummy.width - 60)
        tv.y = random.nextFloat() * (flMummy.height - 60)
        tv.setOnClickListener {
            applyPickup(idx)
            flMummy.removeView(tv)
        }
        handler.postDelayed({ flMummy.removeView(tv) }, 8000)
    }

    private fun applyPickup(idx: Int) {
        sound.play("coin")
        vibrate(30)
        when (idx) {
            0 -> { effectUntil["autoclick"] = System.currentTimeMillis() + 10000; startAutoLoop() }
            1 -> effectUntil["gold"] = System.currentTimeMillis() + 15000
            2 -> effectUntil["luck"] = System.currentTimeMillis() + 20000
            3 -> mana = min(manaMax().toDouble(), mana + 30)
            4 -> effectUntil["find"] = System.currentTimeMillis() + 10000
            5 -> effectUntil["crit"] = System.currentTimeMillis() + 5000
        }
        val names = arrayOf(
            "⚡ Бонус: автоклик 10с!",
            "💰 Бонус: монеты x2 на 15с!",
            "🍀 Бонус: удача на 20с!",
            "🔮 Бонус: +30 маны!",
            "🏺 Бонус: поиск артов x3 на 10с!",
            "⚔️ Бонус: крит 5с!"
        )
        tvLog.text = names[idx]
        spawnFloatingText(names[idx].substring(0, 2), "#80DEEA")
        updateUi()
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
        return s * if (pk(13)) 1.5 else 1.0
    }

    private fun tapPower(): Long {
        val base = 1L + tapLevel + sumOwned("TAP").roundToLong()
        return (base * (if (pk(1)) 1.5 else 1.0)).roundToLong()
    }

    private fun badChance(): Double {
        var base = 0.25 - 0.01 * luckLevel - sumOwned("LUCK") / 100.0
        if (eff("luck")) base -= 0.10
        if (pk(2)) base *= 0.7
        return max(0.02, base)
    }

    private fun critChance(): Double {
        if (eff("crit")) return 1.0
        return min(0.90, 0.05 + 0.015 * luckLevel + sumOwned("CRIT") / 100.0 + if (pk(3)) 0.25 else 0.0)
    }

    private fun critMultiplier(): Double {
        return 5.0 + sumOwned("CRITMULT") + if (pk(4)) 2.0 else 0.0
    }

    private fun artifactFindChance(): Double {
        val base = 0.06 + 0.008 * luckLevel + sumOwned("FIND") / 100.0
        var v = base * if (pk(6)) 1.5 else 1.0
        if (eff("find")) v *= 3.0
        return min(0.50, v)
    }

    private fun goldMultiplier(): Double {
        var v = 1.0 + sumOwned("GOLD") / 100.0
        if (eff("gold")) v *= 2.0
        if (pk(0)) v *= 1.3
        if (pk(14)) v *= 1.0 + 0.10 * rebirths
        return v
    }

    private fun autoIncome(): Long {
        val v = autoLevel * (1.0 + sumOwned("AUTO") / 100.0) *
                (if (pk(5)) 1.5 else 1.0) * goldMultiplier()
        return v.roundToLong()
    }

    private fun comboMultiplier(): Double {
        return max(1.0, artifactsOwned.size.toDouble())
    }

    private fun calculatePenalty(): Long {
        if (coins <= 0) return 0L
        val shield = sumOwned("SHIELD") / 100.0
        var percent = max(0.05, 0.10 + 0.05 * artifactsOwned.size - shield)
        if (pk(7)) percent *= 0.7
        return max(1L, (coins * percent).roundToLong())
    }

    private fun manaMax(): Int = 100 + if (pk(8)) 50 else 0
    private fun regenPerSec(): Double = 1.0 + if (pk(8)) 1.0 else 0.0
    private fun manaPerTap(): Double = 1.0 * if (pk(12)) 2.0 else 1.0
    private fun skillDurMult(): Double = if (pk(9)) 1.5 else 1.0

    private fun buyTapUpgrade() {
        val cost = tapCost()
        if (coins >= cost) {
            coins -= cost; tapLevel++; sound.play("coin")
            tvLog.text = "Бинты укреплены! Теперь тап сильнее."
        } else tvLog.text = "Не хватает монет на улучшение тапа."
        saveProgress(); updateUi()
    }

    private fun buyAutoUpgrade() {
        val cost = autoCost()
        if (coins >= cost) {
            coins -= cost; autoLevel++; sound.play("coin")
            tvLog.text = "Проклятый кот нанят! Монеты капают каждую секунду."
        } else tvLog.text = "Не хватает монет на кота."
        saveProgress(); updateUi()
    }

    private fun buyLuckUpgrade() {
        val cost = luckCost()
        if (coins >= cost) {
            coins -= cost; luckLevel++; sound.play("coin")
            tvLog.text = "Амулет удачи сияет! Мумия стала чуть менее проклятой."
        } else tvLog.text = "Не хватает монет на амулет."
        saveProgress(); updateUi(); updateArtifactsTable()
    }

    private fun upgradeArtifact(id: Int) {
        val cost = artifactUpgradeCost(id)
        if (coins >= cost) {
            coins -= cost
            artifactLevels[id] = (artifactLevels[id] ?: 0) + 1
            sound.play("artifact")
            tvLog.text = "✨ ${artifacts[id].name} улучшен до ур. ${artifactLevels[id]}!"
        } else tvLog.text = "Не хватает монет на улучшение артефакта."
        saveProgress(); updateUi(); updateArtifactsTable()
    }

    private fun resetAll() {
        coins = 0; tapLevel = 0; autoLevel = 0; luckLevel = 0
        totalClicks = 0; rebirths = 0; mana = 0.0
        perks.clear()
        artifactsCollected.clear(); artifactsOwned.clear(); artifactLevels.clear()
        effectUntil.clear(); rebirthOffered = false
        saveProgress(); updateUi(); updateArtifactsTable()
        layoutSettings.visibility = View.GONE
        tvLog.text = "Всё сброшено. Начинаем заново!"
    }

    private fun tapCost(): Long = (25.0 * 1.6.pow(tapLevel.toDouble())).toLong()
    private fun autoCost(): Long = (50.0 * 1.7.pow(autoLevel.toDouble())).toLong()
    private fun luckCost(): Long = (100.0 * 2.0.pow(luckLevel.toDouble())).toLong()

    private fun artifactUpgradeCost(id: Int): Long {
        return (artifacts[id].baseCost * 1.5.pow((artifactLevels[id] ?: 0).toDouble())).toLong()
    }

    private fun showRebirth() {
        rebirthOffered = true
        tvRebirthText.text = "Ты собрал 12 артефактов ОДНОВРЕМЕННО!\n" +
                "Перерождение сбросит прогресс, но даст СЛУЧАЙНЫЙ перк (всего 15).\n" +
                "Перерождений: $rebirths | Перков: ${perks.size}/15"
        layoutRebirth.visibility = View.VISIBLE
    }

    private fun doRebirth() {
        rebirths++
        val missing = perkDefs.indices.filter { !perks.contains(it) }
        var gained: PerkDef? = null
        if (missing.isNotEmpty()) {
            val p = missing[random.nextInt(missing.size)]
            perks.add(p)
            gained = perkDefs[p]
        } else {
            coins += 1000
        }

        coins = max(coins, if (pk(11)) 500L * rebirths else 0L)
        tapLevel = 0; autoLevel = 0; luckLevel = 0
        artifactsCollected.clear(); artifactsOwned.clear(); artifactLevels.clear()
        effectUntil.clear()
        rebirthOffered = false
        layoutRebirth.visibility = View.GONE
        sound.play("artifact")
        tvLog.text = if (gained != null) {
            "🌀 Перерождение #$rebirths! Перк: ${gained.emoji} ${gained.name} — ${gained.desc}"
        } else {
            "🌀 Перерождение #$rebirths! Все перки собраны, +1000 монет"
        }
        saveProgress(); updateUi(); updateArtifactsTable()
    }

    private fun updateUi() {
        tvHeadCoins.text = "💰 " + format(coins)
        tvHeadClicks.text = "👆 " + format(totalClicks)
        tvHeadArts.text = "🏺 ${artifactsOwned.size}/12"
        btnRebirth.visibility = if (artifactsOwned.size == 12) View.VISIBLE else View.GONE

        tvMana.text = "🔮 ${mana.toInt()}/${manaMax()}"

        val stats = "За тап: ${format(tapPower())} | Крит: ${formatPercent(critChance())} " +
                "(x${fmt1(critMultiplier())}) | Неудача: ${formatPercent(badChance())}\n" +
                "Авто: ${format(autoIncome())}/сек | Комбо: x${artifactsOwned.size.coerceAtLeast(1)} | " +
                "Перки: ${perks.size}/15 | 🌀 $rebirths"
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

        skillViews.forEachIndexed { i, v ->
            val s = skills[i]
            val active = skillKeys[i].isNotEmpty() && eff(skillKeys[i])
            v.text = s.emoji
            if (active) {
                val secs = ((effectUntil[skillKeys[i]] ?: 0) - System.currentTimeMillis()) / 1000
                v.setBackgroundResource(R.drawable.bg_panel)
                v.setTextColor(Color.parseColor("#FFEB3B"))
                v.text = "${s.emoji}\n${secs}"
                v.setTextSize(12f)
            } else {
                v.setBackgroundResource(R.drawable.bg_panel)
                v.setTextColor(Color.parseColor("#FFD54F"))
                v.text = s.emoji
                v.setTextSize(20f)
            }
            v.isEnabled = !active && mana >= s.cost
            v.contentDescription = "${s.name}: ${s.desc}. Цена ${s.cost} маны"
        }
    }

    private fun passiveValueText(def: ArtifactDef, level: Int): String {
        val v = def.value * level * if (pk(13)) 1.5 else 1.0
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
        if (on) ivMummy.startAnimation(AnimationUtils.loadAnimation(this, R.anim.wobble))
        else {
            ivMummy.clearAnimation()
            tvLog.text = "🔋 Низкий заряд: эффекты выключены для экономии батареи"
        }
    }

    private fun burstAtMummy(color: Int, count: Int) {
        if (!effectsEnabled) return
        val a = IntArray(2); val b = IntArray(2)
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
        ivMummy.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
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
        ).apply { gravity = Gravity.CENTER_HORIZONTAL }
        flMummy.addView(tv, params)
        tv.y = flMummy.height / 2f
        tv.x = (flMummy.width / 2f) - 40 + random.nextInt(80)
        tv.animate().translationYBy(-260f).alpha(0f).setDuration(900)
            .withEndAction { flMummy.removeView(tv) }.start()
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

        for (id in artifactsCollected.sorted()) {
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
                minimumWidth = 0; minWidth = 0
                setPadding(24, 0, 24, 0)
            }
            actionRow.addView(btnUpgrade)

            container.addView(actionRow)
            layoutArtifacts.addView(container)
        }

        if (ownedCount == 12) {
            val tv = TextView(this).apply {
                text = "🏆 12 АРТЕФАКТОВ ОДНОВРЕМЕННО! Жми 🌀 для перерождения!"
                setTextColor(Color.parseColor("#FFEB3B"))
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(0, 16, 0, 0)
            }
            layoutArtifacts.addView(tv)
        }
    }

    private fun fmt1(v: Double): String = String.format(Locale.getDefault(), "%.1f", v)
    private fun format(value: Long): String = NumberFormat.getNumberInstance(Locale.getDefault()).format(value)
    private fun formatPercent(value: Double): String = String.format(Locale.getDefault(), "%.0f%%", value * 100)

    private fun saveProgress() {
        prefs.edit()
            .putLong("coins", coins)
            .putInt("tapLevel", tapLevel)
            .putInt("autoLevel", autoLevel)
            .putInt("luckLevel", luckLevel)
            .putLong("totalClicks", totalClicks)
            .putInt("rebirths", rebirths)
            .putInt("mana", mana.toInt())
            .putBoolean("sound", !muted)
            .putBoolean("vibration", vibrationEnabled)
            .putString("perks", perks.joinToString(","))
            .putString("artifactsCollected", artifactsCollected.joinToString(","))
            .putString("artifactsOwned", artifactsOwned.joinToString(","))
            .putString("artifactLevels", artifactLevels.entries.joinToString(",") { "${it.key}:${it.value}" })
            .apply()
    }

    private fun loadProgress() {
        coins = prefs.getLong("coins", 0L)
        tapLevel = prefs.getInt("tapLevel", 0)
        autoLevel = prefs.getInt("autoLevel", 0)
        luckLevel = prefs.getInt("luckLevel", 0)
        totalClicks = prefs.getLong("totalClicks", 0L)
        rebirths = prefs.getInt("rebirths", 0)
        mana = prefs.getInt("mana", 0).toDouble()

        prefs.getString("perks", "")?.let {
            if (it.isNotBlank()) {
                perks.clear()
                it.split(",").forEach { s -> s.toIntOrNull()?.let { id -> perks.add(id) } }
            }
        }
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
