package com.example.socialblock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * Élő, „high-tech” háttér: mélykék színátmenet, lassan sodródó fényfoltok (lila, kék, zöld),
 * finom pöttyrács és egy halvány, lefelé pásztázó fénycsík. Akkuspórolás: csak látható állapotban fut.
 */
class AnimatedBackgroundView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val basePaint = Paint()
    private val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(28, 255, 255, 255) }
    private val scanPaint = Paint()
    private val startTime = System.nanoTime()
    private var running = false

    private data class Orb(val color: Int, val radiusFactor: Float, val cx: Float, val cy: Float,
                           val ax: Float, val ay: Float, val speed: Float, val phase: Float)

    private val orbs = listOf(
        Orb(Color.argb(170, 0x58, 0x46, 0xE6), 0.95f, 0.50f, 0.12f, 0.30f, 0.08f, 0.050f, 0f),
        Orb(Color.argb(120, 0x2F, 0x7C, 0xFF), 0.70f, 0.15f, 0.55f, 0.18f, 0.20f, 0.037f, 1.7f),
        Orb(Color.argb(100, 0x33, 0xE1, 0xB4), 0.65f, 0.90f, 0.90f, 0.15f, 0.10f, 0.043f, 3.1f),
        Orb(Color.argb(110, 0xC0, 0x4C, 0xFF), 0.55f, 0.85f, 0.40f, 0.12f, 0.18f, 0.031f, 4.4f)
    )

    // Lebegő fénypontok, a közeliek között vékony vonalak („hálózat” hatás)
    private class Particle(var x: Float, var y: Float, val vx: Float, val vy: Float, val r: Float, val phase: Float)
    private val particles = mutableListOf<Particle>()
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.2f }
    private var lastFrame = 0L

    private fun seedParticles(w: Int, h: Int) {
        particles.clear()
        val rnd = java.util.Random(42)
        val d = resources.displayMetrics.density
        repeat(34) {
            particles += Particle(
                rnd.nextFloat() * w, rnd.nextFloat() * h,
                (rnd.nextFloat() - 0.5f) * 14f * d, -(4f + rnd.nextFloat() * 12f) * d,
                (1.2f + rnd.nextFloat() * 1.8f) * d, rnd.nextFloat() * 6.28f
            )
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        basePaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(Color.parseColor("#1A1D45"), Color.parseColor("#10122A"), Color.parseColor("#0A0B1A")),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        seedParticles(w, h)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        running = false
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        running = visibility == VISIBLE && isAttachedToWindow
        if (running) postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val t = (System.nanoTime() - startTime) / 1_000_000_000f

        canvas.drawRect(0f, 0f, w, h, basePaint)

        // Sodródó fényfoltok
        val unit = minOf(w, h)
        for (o in orbs) {
            val x = (o.cx + o.ax * sin(t * o.speed * 6.283f + o.phase)) * w
            val y = (o.cy + o.ay * cos(t * o.speed * 5.1f + o.phase)) * h
            val r = unit * o.radiusFactor * (1f + 0.08f * sin(t * 0.6f + o.phase))
            orbPaint.shader = RadialGradient(x, y, r, o.color, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawCircle(x, y, r, orbPaint)
        }

        // Pöttyrács
        val step = 18f * resources.displayMetrics.density
        val dotR = 0.9f * resources.displayMetrics.density
        var yy = step / 2
        while (yy < h) {
            var xx = step / 2
            while (xx < w) {
                canvas.drawCircle(xx, yy, dotR, dotPaint)
                xx += step
            }
            yy += step
        }

        // Fénypontok mozgatása és rajzolása
        val now = System.nanoTime()
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1_000_000_000f).coerceAtMost(0.05f)
        lastFrame = now
        val linkDist = 110f * resources.displayMetrics.density
        for (p in particles) {
            p.x += p.vx * dt; p.y += p.vy * dt
            if (p.y < -20f) { p.y = h + 20f }
            if (p.x < -20f) p.x = w + 20f else if (p.x > w + 20f) p.x = -20f
        }
        for (i in particles.indices) {
            val a = particles[i]
            for (j in i + 1 until particles.size) {
                val b = particles[j]
                val dx = a.x - b.x; val dy = a.y - b.y
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                if (dist < linkDist) {
                    linePaint.color = Color.argb((70 * (1f - dist / linkDist)).toInt(), 0x8A, 0x7B, 0xFF)
                    canvas.drawLine(a.x, a.y, b.x, b.y, linePaint)
                }
            }
        }
        for (p in particles) {
            val tw = 0.55f + 0.45f * sin(t * 2f + p.phase)
            particlePaint.shader = RadialGradient(p.x, p.y, p.r * 5f,
                Color.argb((150 * tw).toInt(), 0x6E, 0xE7, 0xB7), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawCircle(p.x, p.y, p.r * 5f, particlePaint)
            particlePaint.shader = null
            particlePaint.color = Color.argb((230 * tw).toInt(), 0xE8, 0xFF, 0xF6)
            canvas.drawCircle(p.x, p.y, p.r, particlePaint)
        }

        // Lassan lefelé pásztázó fénycsík (8 mp-es ciklus)
        val scanY = ((t % 8f) / 8f) * (h + 300f) - 150f
        scanPaint.shader = LinearGradient(
            0f, scanY - 150f, 0f, scanY + 150f,
            intArrayOf(Color.TRANSPARENT, Color.argb(46, 0x8A, 0x7B, 0xFF), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, scanY - 150f, w, scanY + 150f, scanPaint)

        if (running) postInvalidateOnAnimation()
    }
}
