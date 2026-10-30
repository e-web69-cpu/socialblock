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

    // Óriás, vékony vonalas SociaLock-motívum (buborék + lakat), a logó geometriájából (108 egységes tér)
    private val bubblePath = android.graphics.Path().apply {
        moveTo(40f, 32f); lineTo(68f, 32f)
        arcTo(android.graphics.RectF(56f, 32f, 80f, 56f), -90f, 90f)
        lineTo(80f, 58f)
        arcTo(android.graphics.RectF(56f, 46f, 80f, 70f), 0f, 90f)
        lineTo(46f, 70f); lineTo(32f, 82f); lineTo(34.4f, 69.4f)
        quadTo(28f, 66f, 28f, 58f)
        lineTo(28f, 44f)
        arcTo(android.graphics.RectF(28f, 32f, 52f, 56f), 180f, 90f)
        close()
        addRoundRect(android.graphics.RectF(36f, 41f, 70f, 45.2f), 2.1f, 2.1f, android.graphics.Path.Direction.CW)
        addRoundRect(android.graphics.RectF(36f, 49f, 62f, 53.2f), 2.1f, 2.1f, android.graphics.Path.Direction.CW)
        addRoundRect(android.graphics.RectF(36f, 57f, 54f, 61.2f), 2.1f, 2.1f, android.graphics.Path.Direction.CW)
        addCircle(54f, 48f, 4.1f, android.graphics.Path.Direction.CW)
    }
    private val lockPath = android.graphics.Path().apply {
        // a logóban: translate(67.2 72.4) scale(0.8) translate(-71 -74)
        val m = android.graphics.Matrix().apply { setTranslate(-71f, -74f); postScale(0.8f, 0.8f); postTranslate(67.2f, 72.4f) }
        val shackle = android.graphics.Path().apply {
            moveTo(61f, 64f); lineTo(61f, 59f)
            arcTo(android.graphics.RectF(61f, 50f, 79f, 68f), 180f, 180f)
            lineTo(79f, 64f)
        }
        val body = android.graphics.Path().apply {
            addRoundRect(android.graphics.RectF(55f, 62f, 87f, 86f), 5f, 5f, android.graphics.Path.Direction.CW)
        }
        addPath(shackle, m); addPath(body, m)
    }
    private val motifPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }

    /** Egy motívum-elhelyezés: középpont (arány), méret (képszélesség arányában), dőlés, tükrözés. */
    private class Placement(val cx: Float, val cy: Float, val size: Float, val rot: Float, val mirror: Boolean, val phase: Float, val alpha: Float = 1f)

    // Főképernyő (tag="main"): fent a fejléc és az első kártya mögött tükrözve, lent a sötét részen másik dőléssel.
    private val mainPlacements = listOf(
        Placement(0.62f, 0.17f, 1.15f, 16f, true, 0f, 0.7f),
        Placement(0.40f, 0.82f, 1.45f, -12f, false, 2.1f)
    )
    // Többi képernyő: jobbra középen, nagyban.
    private val defaultPlacements = listOf(Placement(0.62f, 0.42f, 1.7f, -18f, false, 0f))

    private fun drawMotif(canvas: Canvas, w: Float, h: Float, t: Float) {
        val d = resources.displayMetrics.density
        val list = if (tag == "main") mainPlacements else defaultPlacements
        for (pl in list) {
            val tt = t + pl.phase
            val scale = (w * pl.size) / 108f * (1f + 0.025f * sin(tt * 0.35f))
            canvas.save()
            canvas.translate(w * pl.cx + 10f * d * sin(tt * 0.21f), h * pl.cy + 14f * d * cos(tt * 0.17f))
            canvas.rotate(pl.rot + 4f * sin(tt * 0.12f))
            if (pl.mirror) canvas.scale(-1f, 1f)
            canvas.scale(scale, scale)
            canvas.translate(-54f, -57f)
            motifPaint.strokeWidth = 2f * d / scale
            motifPaint.color = Color.argb((160 * pl.alpha).toInt(), 0x9D, 0x90, 0xFF)
            canvas.drawPath(bubblePath, motifPaint)
            motifPaint.color = Color.argb((170 * pl.alpha).toInt(), 0x6E, 0xE7, 0xB7)
            canvas.drawPath(lockPath, motifPaint)
            canvas.restore()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        basePaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(Color.parseColor("#252962"), Color.parseColor("#191C41"), Color.parseColor("#12132E")),
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

        // Óriás vonalas buborék + lakat
        drawMotif(canvas, w, h, t)

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
