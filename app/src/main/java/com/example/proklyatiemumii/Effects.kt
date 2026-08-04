package com.example.proklyatiemumii

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.AttributeSet
import android.view.View
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class SoundManager(private val context: Context) {

    private var soundPool: SoundPool? = null
    private val soundIds = mutableMapOf<String, Int>()
    var muted = false

    fun init() {
        val sp = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        soundPool = sp

        soundIds["tap"] = loadSynth("tap", 0.06f, 440f, 660f)
        soundIds["crit"] = loadSynth("crit", 0.18f, 660f, 1320f)
        soundIds["bad"] = loadSynth("bad", 0.2f, 220f, 110f)
        soundIds["coin"] = loadSynth("coin", 0.12f, 988f, 1319f)
        soundIds["artifact"] = loadSynth("artifact", 0.35f, 523f, 1046f)
    }

    private fun loadSynth(name: String, seconds: Float, f0: Float, f1: Float): Int {
        val file = File(context.cacheDir, "snd_$name.wav")
        if (!file.exists()) {
            val sampleRate = 22050
            val n = (sampleRate * seconds).toInt()
            val data = ByteArray(n * 2)
            for (i in 0 until n) {
                val t = i.toFloat() / sampleRate
                val progress = i.toFloat() / n
                val freq = f0 + (f1 - f0) * progress
                val fade = 1f - progress
                val value = (sin(2.0 * Math.PI * freq * t) * fade * 0.6).toFloat()
                val s = (value * Short.MAX_VALUE).toInt().toShort()
                data[i * 2] = (s.toInt() and 0xFF).toByte()
                data[i * 2 + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
            }
            FileOutputStream(file).use { out ->
                out.write(wavHeader(n * 2, sampleRate))
                out.write(data)
            }
        }
        return soundPool?.load(file.absolutePath, 1) ?: 0
    }

    private fun wavHeader(dataLen: Int, sampleRate: Int): ByteArray {
        val h = ByteArray(44)
        fun putStr(off: Int, s: String) {
            s.forEachIndexed { i, c -> h[off + i] = c.code.toByte() }
        }
        fun putInt(off: Int, v: Int) {
            h[off] = (v and 0xFF).toByte()
            h[off + 1] = ((v shr 8) and 0xFF).toByte()
            h[off + 2] = ((v shr 16) and 0xFF).toByte()
            h[off + 3] = ((v shr 24) and 0xFF).toByte()
        }
        fun putShort(off: Int, v: Int) {
            h[off] = (v and 0xFF).toByte()
            h[off + 1] = ((v shr 8) and 0xFF).toByte()
        }
        putStr(0, "RIFF"); putInt(4, 36 + dataLen); putStr(8, "WAVEfmt ")
        putInt(16, 16); putShort(20, 1); putShort(22, 1)
        putInt(24, sampleRate); putInt(28, sampleRate * 2)
        putShort(32, 2); putShort(34, 16)
        putStr(36, "data"); putInt(40, dataLen)
        return h
    }

    fun play(name: String, volume: Float = 0.5f) {
        if (muted) return
        val id = soundIds[name] ?: return
        soundPool?.play(id, volume, volume, 1, 0, 1f)
    }

    fun release() {
        soundPool?.release()
        soundPool = null
    }
}

class ParticleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class P(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var life: Float,
        val size: Float, val color: Int
    )

    private val particles = mutableListOf<P>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val random = Random.Default
    private var enabled = true
    private var running = false
    private val maxParticles = 40

    fun setEnabledEffects(on: Boolean) {
        enabled = on
        if (!on) {
            particles.clear()
            running = false
            invalidate()
        }
    }

    fun burst(x: Float, y: Float, color: Int, count: Int = 8) {
        if (!enabled) return
        for (i in 0 until count) {
            if (particles.size >= maxParticles) particles.removeAt(0)
            val angle = random.nextDouble() * Math.PI * 2
            val speed = 2f + random.nextFloat() * 5f
            particles.add(
                P(
                    x, y,
                    (Math.cos(angle) * speed).toFloat(),
                    (Math.sin(angle) * speed).toFloat() - 3f,
                    1f,
                    3f + random.nextFloat() * 5f,
                    color
                )
            )
        }
        startLoop()
    }

    private val tick = object : Runnable {
        override fun run() {
            if (particles.isEmpty()) {
                running = false
                return
            }
            var i = particles.size - 1
            while (i >= 0) {
                val p = particles[i]
                p.x += p.vx
                p.y += p.vy
                p.vy += 0.25f
                p.life -= 0.03f
                if (p.life <= 0) particles.removeAt(i)
                i--
            }
            invalidate()
            if (particles.isNotEmpty()) postOnAnimation(this) else running = false
        }
    }

    private fun startLoop() {
        if (!running) {
            running = true
            postOnAnimation(tick)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (p in particles) {
            paint.color = p.color
            paint.alpha = (p.life * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(p.x, p.y, p.size * p.life, paint)
        }
    }
}

class ParallaxView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val skyPaint = Paint()
    private val starPaint = Paint().apply { color = Color.parseColor("#FFE082") }
    private val farPaint = Paint().apply { color = Color.parseColor("#241608") }
    private val nearPaint = Paint().apply { color = Color.parseColor("#170E05") }
    private val moonPaint = Paint().apply { color = Color.parseColor("#F5E6C4") }

    private var shift = 0f
    private val stars = List(40) { i ->
        val r = Random(i.toLong() + 7)
        Pair(r.nextFloat(), r.nextFloat() * 0.5f)
    }

    fun setShift(s: Float) {
        val clamped = s.coerceIn(-1f, 1f)
        if (abs(clamped - shift) > 0.02f) {
            shift = clamped
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (h > 0) {
            skyPaint.shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                intArrayOf(
                    Color.parseColor("#1A1030"),
                    Color.parseColor("#2B1B0E"),
                    Color.parseColor("#0D0805")
                ),
                null, Shader.TileMode.CLAMP
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        canvas.drawRect(0f, 0f, w, h, skyPaint)

        for ((sx, sy) in stars) {
            canvas.drawCircle(sx * w + shift * 6, sy * h, 2f, starPaint)
        }

        canvas.drawCircle(w * 0.78f + shift * 10, h * 0.16f, 46f, moonPaint)

        val far = Path().apply {
            moveTo(-40f + shift * 18, h)
            lineTo(w * 0.25f + shift * 18, h * 0.45f)
            lineTo(w * 0.55f + shift * 18, h)
            close()
            moveTo(w * 0.45f + shift * 18, h)
            lineTo(w * 0.75f + shift * 18, h * 0.52f)
            lineTo(w * 1.05f + shift * 18, h)
            close()
        }
        canvas.drawPath(far, farPaint)

        val near = Path().apply {
            moveTo(-60f + shift * 34, h)
            lineTo(w * 0.3f + shift * 34, h * 0.8f)
            lineTo(w * 0.7f + shift * 34, h * 0.92f)
            lineTo(w + 60f + shift * 34, h * 0.78f)
            lineTo(w + 60f, h)
            close()
        }
        canvas.drawPath(near, nearPaint)
    }
}
