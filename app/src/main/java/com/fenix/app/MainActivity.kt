package com.fenix.app

import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.fenix.app.databinding.ActivityMainBinding
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRestart.setOnClickListener {
            binding.gameView.resetGame()
        }
    }
}

/* ---------- GameView ---------- */
class GameView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val shipPaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.FILL
    }
    private val enemyPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 48f
        typeface = Typeface.DEFAULT_BOLD
    }

    private var shipX = 0f
    private var shipY = 0f
    private val shipSize = 80f

    private data class Enemy(var x: Float, var y: Float, val size: Float, var speed: Float)

    private val enemies = mutableListOf<Enemy>()
    private var score = 0
    private var gameOver = false

    private var gameJob: Job? = null
    private val scope = MainScope()

    init {
        // Start the game loop after the view has been laid out
        post {
            resetGame()
            startLoop()
        }
    }

    fun resetGame() {
        shipX = width / 2f
        shipY = height - shipSize * 2
        enemies.clear()
        score = 0
        gameOver = false
    }

    private fun startLoop() {
        gameJob?.cancel()
        gameJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                if (!gameOver) {
                    update()
                }
                withContext(Dispatchers.Main) {
                    invalidate()
                }
                delay(16L) // ~60 FPS
            }
        }
    }

    private fun update() {
        // Randomly spawn an enemy
        if (Math.random() < 0.02) {
            val size = 60f
            val x = (size..(width - size)).random()
            val speed = (5..15).random().toFloat()
            enemies.add(Enemy(x, -size, size, speed))
        }

        // Move enemies and check collisions
        val iterator = enemies.iterator()
        while (iterator.hasNext()) {
            val e = iterator.next()
            e.y += e.speed
            if (e.y - e.size > height) {
                iterator.remove()
                score += 1
            } else if (checkCollision(e)) {
                gameOver = true
            }
        }
    }

    private fun checkCollision(enemy: Enemy): Boolean {
        val shipRect = RectF(
            shipX - shipSize,
            shipY - shipSize,
            shipX + shipSize,
            shipY + shipSize
        )
        val dx = Math.max(shipRect.left, Math.min(enemy.x, shipRect.right))
        val dy = Math.max(shipRect.top, Math.min(enemy.y, shipRect.bottom))
        val distance = Math.hypot((dx - enemy.x).toDouble(), (dy - enemy.y).toDouble())
        return distance < enemy.size
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)

        // Draw ship (triangle)
        val path = Path()
        path.moveTo(shipX, shipY - shipSize)
        path.lineTo(shipX - shipSize, shipY + shipSize)
        path.lineTo(shipX + shipSize, shipY + shipSize)
        path.close()
        canvas.drawPath(path, shipPaint)

        // Draw enemies
        for (e in enemies) {
            canvas.drawCircle(e.x, e.y, e.size, enemyPaint)
        }

        // Draw score
        canvas.drawText("${resources.getString(R.string.score)}: $score", 20f, 60f, textPaint)

        // Draw Game Over message
        if (gameOver) {
            val msg = resources.getString(R.string.game_over)
            val w = textPaint.measureText(msg)
            canvas.drawText(msg, (width - w) / 2, height / 2f, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gameOver) return true
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
            shipX = event.x.coerceIn(shipSize, width - shipSize)
            invalidate()
        }
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        gameJob?.cancel()
        scope.cancel()
    }
}