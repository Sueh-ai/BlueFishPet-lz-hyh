package com.example.bluefish

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

object FishDialogue {
    private val rnd = Random(SystemClock.uptimeMillis())
    private val prefixes = arrayOf("咕噜～", "嘿嘿，", "哎呀，", "那个……", "告诉你哦，", "本鱼宣布：", "其实吧，", "你听我说，", "")
    private val suffixes = arrayOf("～", "！", "哦。", "呢。", "呀！", "咕噜咕噜……", "")
    fun pick(arr: Array<String>): String {
        val base = arr[rnd.nextInt(arr.size)]
        val p = prefixes[rnd.nextInt(prefixes.size)]
        val s = suffixes[rnd.nextInt(suffixes.size)]
        return "$p$base$s"
    }
    val body = arrayOf(
        "别戳啦，再戳就变成鱼丸了", "今天也要摸鱼吗", "我是一条努力不摸鱼的蓝色大肥鱼", "戳我一下，鱼生快乐一秒",
        "哎哟，痒痒的", "你是不是很闲？巧了，我也是", "我在思考一个哲学问题：先有鱼还是先有水", "报告！大肥鱼随时待命"
    )
    val head = arrayOf("别摸头，会长不高的", "摸头杀对我无效，我只会更圆", "嘿嘿，再摸摸", "头顶的鳞片是刚保养过的")
    val belly = arrayOf("肚子不能戳，会漏气的", "这里是我的粮仓", "别挠肚子，我怕痒", "圆滚滚的肚子，装满了梦想")
    val tail = arrayOf("别拽尾巴，会掉鳞的", "尾巴是方向舵，别乱按", "摇摆摇摆，快乐摇摆", "我的尾巴会写诗，你信吗")
    val idle = arrayOf("今天的水温刚刚好", "有点无聊，你陪我玩会儿", "泡泡，泡泡，好多泡泡", "我在看云，虽然看不见")
    val cruise = arrayOf("巡游时间到", "我要去环游屏幕了", "跟我一起游吧", "前方发现一片新海域")
    val feed = arrayOf("谢谢投喂，真香", "小鱼干！我最爱", "再来一点嘛", "吃饱了，打个嗝")
    val action = arrayOf("看我跳一个", "抖抖更健康", "转个圈给你看", "模式切换成功")
}

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var fishView: FishView
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        tts = TextToSpeech(this, this)
        fishView = FishView()
        setContentView(fishView)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.CHINA
            ttsReady = true
        }
    }

    fun speak(text: String) {
        if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "fish")
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    inner class FishView : View(this@MainActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)

        private var cx = 400f
        private var cy = 800f
        private var baseRadius = 120f
        private var scale = 1f
        private var angle = 0f
        private var swimPhase = 0f

        private var dragging = false
        private var dragOffX = 0f
        private var dragOffY = 0f
        private var lastDist = 0f

        private val handler = Handler(Looper.getMainLooper())
        private var longPressTriggered = false
        private val longPressRunnable = Runnable { showMenu() }

        private var cruiseMode = 0
        private var cruiseTargetX = 400f
        private var cruiseTargetY = 800f
        private var cruiseTimer = 0L
        private var cruisePauseUntil = 0L

        private var bubbleText: String? = null
        private var bubbleUntil = 0L

        private var jumpUntil = 0L
        private var jumpVY = 0f
        private var jumpY = 0f
        private var shakeUntil = 0L
        private var spinUntil = 0L

        private val rand = Random(SystemClock.uptimeMillis())

        init {
            bodyPaint.color = Color.rgb(60, 160, 255)
            bubblePaint.color = Color.argb(220, 255, 255, 255)
            postDelayed(idleRunnable, 8000L)
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (cx == 400f && cy == 800f) {
                cx = w / 2f
                cy = h / 2f
                cruiseTargetX = cx
                cruiseTargetY = cy
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val now = SystemClock.uptimeMillis()
            swimPhase += 0.08f
            updateCruise(now)
            updateActions(now)

            val r = baseRadius * scale
            val drawX = cx
            val drawY = cy + jumpY + (if (now < shakeUntil) sin(now / 30f) * 6f else 0f)
            val drawAngle = angle + (if (now < spinUntil) (now % 360).toFloat() else 0f)

            canvas.save()
            canvas.rotate(drawAngle, drawX, drawY)

            paint.color = Color.rgb(40, 130, 230)
            val tailWag = sin(swimPhase) * r * 0.25f
            val tailPath = Path()
            tailPath.moveTo(drawX - r * 0.8f, drawY)
            tailPath.lineTo(drawX - r * 1.7f, drawY - r * 0.7f + tailWag)
            tailPath.lineTo(drawX - r * 1.7f, drawY + r * 0.7f + tailWag)
            tailPath.close()
            canvas.drawPath(tailPath, paint)

            bodyPaint.color = Color.rgb(60, 160, 255)
            canvas.drawOval(RectF(drawX - r * 1.05f, drawY - r * 0.75f, drawX + r * 1.05f, drawY + r * 0.75f), bodyPaint)

            paint.color = Color.argb(90, 255, 255, 255)
            canvas.drawOval(RectF(drawX - r * 0.5f, drawY, drawX + r * 0.7f, drawY + r * 0.6f), paint)

            paint.color = Color.WHITE
            canvas.drawCircle(drawX + r * 0.55f, drawY - r * 0.2f, r * 0.22f, paint)
            paint.color = Color.BLACK
            canvas.drawCircle(drawX + r * 0.62f, drawY - r * 0.18f, r * 0.11f, paint)

            paint.color = Color.rgb(30, 90, 180)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = r * 0.06f
            val mouth = Path()
            mouth.moveTo(drawX + r * 0.85f, drawY + r * 0.05f)
            mouth.quadTo(drawX + r * 0.95f, drawY + r * 0.18f, drawX + r * 0.75f, drawY + r * 0.22f)
            canvas.drawPath(mouth, paint)
            paint.style = Paint.Style.FILL

            paint.color = Color.rgb(45, 140, 235)
            val fin = Path()
            fin.moveTo(drawX - r * 0.1f, drawY - r * 0.7f)
            fin.lineTo(drawX + r * 0.3f, drawY - r * 1.15f)
            fin.lineTo(drawX + r * 0.4f, drawY - r * 0.6f)
            fin.close()
            canvas.drawPath(fin, paint)

            canvas.restore()

            bubbleText?.let { txt ->
                if (now < bubbleUntil) drawBubble(canvas, txt, drawX, drawY - r * 1.6f)
            }

            invalidate()
        }

        private fun drawBubble(canvas: Canvas, text: String, x: Float, y: Float) {
            paint.textSize = 34f
            val pad = 22f
            val maxW = width * 0.8f
            val lines = wrapText(text, maxW - pad * 2)
            var maxLineW = 0f
            for (l in lines) maxLineW = maxOf(maxLineW, paint.measureText(l))
            val bw = maxLineW + pad * 2
            val bh = lines.size * (paint.textSize + 8f) + pad * 2
            val rect = RectF(x - bw / 2, y - bh, x + bw / 2, y)
            bubblePaint.color = Color.argb(235, 255, 255, 255)
            canvas.drawRoundRect(rect, 24f, 24f, bubblePaint)
            bubblePaint.color = Color.argb(200, 60, 160, 255)
            bubblePaint.style = Paint.Style.STROKE
            bubblePaint.strokeWidth = 3f
            canvas.drawRoundRect(rect, 24f, 24f, bubblePaint)
            bubblePaint.style = Paint.Style.FILL
            paint.color = Color.rgb(30, 60, 90)
            var ty = y - bh + pad + paint.textSize
            for (l in lines) {
                canvas.drawText(l, x - maxLineW / 2, ty, paint)
                ty += paint.textSize + 8f
            }
        }

        private fun wrapText(text: String, maxW: Float): List<String> {
            val out = ArrayList<String>()
            var line = ""
            for (ch in text) {
                val test = line + ch
                if (paint.measureText(test) > maxW && line.isNotEmpty()) {
                    out.add(line)
                    line = ch.toString()
                } else {
                    line = test
                }
            }
            if (line.isNotEmpty()) out.add(line)
            return out
        }

        private fun updateCruise(now: Long) {
            if (dragging || now < jumpUntil || now < cruisePauseUntil) return
            when (cruiseMode) {
                0 -> {
                    if (now - cruiseTimer > 2500 || hypot(cx - cruiseTargetX, cy - cruiseTargetY) < 20f) {
                        cruiseTargetX = rand.nextInt(width.coerceAtLeast(1)).toFloat()
                        cruiseTargetY = rand.nextInt(height.coerceAtLeast(1)).toFloat()
                        cruiseTimer = now
                    }
                    moveToward(cruiseTargetX, cruiseTargetY, 3.5f)
                }
                1 -> {
                    val margin = baseRadius * scale * 1.5f
                    val per = 2f * (width + height)
                    val t = (now / 8f) % per
                    when {
                        t < width -> { cx = t; cy = margin }
                        t < width + height -> { cx = width - margin; cy = t - width }
                        t < 2 * width + height -> { cx = width - (t - width - height); cy = height - margin }
                        else -> { cx = margin; cy = height - (t - 2 * width - height) }
                    }
                }
            }
        }

        private fun moveToward(tx: Float, ty: Float, speed: Float) {
            val dx = tx - cx
            val dy = ty - cy
            val d = hypot(dx, dy)
            if (d < 1f) return
            cx += dx / d * speed
            cy += dy / d * speed
            angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        }

        private fun updateActions(now: Long) {
            if (now < jumpUntil) {
                jumpVY += 1.2f
                jumpY += jumpVY
                if (jumpY > 0f) {
                    jumpY = 0f
                    jumpVY = 0f
                    jumpUntil = 0
                }
            }
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            val now = SystemClock.uptimeMillis()
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val r = baseRadius * scale
                    val dx = e.x - cx
                    val dy = e.y - cy
                    if (dx * dx + dy * dy <= r * r * 1.3f) {
                        dragging = true
                        dragOffX = cx - e.x
                        dragOffY = cy - e.y
                        longPressTriggered = false
                        handler.postDelayed(longPressRunnable, 650)
                    }
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (e.pointerCount >= 2) {
                        handler.removeCallbacks(longPressRunnable)
                        dragging = false
                        lastDist = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (e.pointerCount >= 2) {
                        val d = hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1))
                        if (lastDist > 0f) scale = (scale * (d / lastDist)).coerceIn(0.5f, 3f)
                        lastDist = d
                    } else if (dragging) {
                        if (abs(e.x - (cx - dragOffX)) > 8 || abs(e.y - (cy - dragOffY)) > 8) {
                            handler.removeCallbacks(longPressRunnable)
                        }
                        cx = (e.x + dragOffX).coerceIn(baseRadius * scale, width - baseRadius * scale)
                        cy = (e.y + dragOffY).coerceIn(baseRadius * scale, height - baseRadius * scale)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressRunnable)
                    if (dragging && !longPressTriggered) {
                        val d = hypot(e.x - cx, e.y - cy)
                        if (d < baseRadius * scale * 0.5f) handleClick(e.x, e.y)
                        cruisePauseUntil = now + 4000
                    }
                    dragging = false
                    lastDist = 0f
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    lastDist = 0f
                }
            }
            return true
        }

        private fun handleClick(x: Float, y: Float) {
            val r = baseRadius * scale
            val dx = x - cx
            val dy = y - cy
            if (hypot(dx, dy) > r * 1.3f) {
                showBubble(FishDialogue.pick(FishDialogue.body))
                return
            }
            val relY = dy / r
            val relX = dx / r
            val line = when {
                relY < -0.35f -> FishDialogue.pick(FishDialogue.head)
                relY > 0.3f -> FishDialogue.pick(FishDialogue.belly)
                relX < -0.5f -> FishDialogue.pick(FishDialogue.tail)
                else -> FishDialogue.pick(FishDialogue.body)
            }
            showBubble(line)
            when (rand.nextInt(6)) {
                0 -> jump()
                1 -> shake()
                2 -> spin()
                3 -> {
                    cruiseMode = (cruiseMode + 1) % 3
                    showBubble(FishDialogue.pick(FishDialogue.action))
                }
            }
        }

        private fun showBubble(text: String) {
            bubbleText = text
            bubbleUntil = SystemClock.uptimeMillis() + 2600
            (context as MainActivity).speak(text)
            invalidate()
        }

        private fun jump() {
            jumpUntil = SystemClock.uptimeMillis() + 900
            jumpVY = -14f
            jumpY = 0f
        }

        private fun shake() { shakeUntil = SystemClock.uptimeMillis() + 500 }
        private fun spin() { spinUntil = SystemClock.uptimeMillis() + 700 }

        private fun showMenu() {
            longPressTriggered = true
            val items = arrayOf("喂食", "改名", "巡游模式", "缩放说明", "隐藏鱼")
            AlertDialog.Builder(context)
                .setTitle("和你的大肥鱼说点什么")
                .setItems(items) { _, which ->
                    when (which) {
                        0 -> showBubble(FishDialogue.pick(FishDialogue.feed))
                        1 -> askRename()
                        2 -> {
                            cruiseMode = (cruiseMode + 1) % 3
                            val name = when (cruiseMode) { 0 -> "随机游走"; 1 -> "绕边巡游"; else -> "静止" }
                            Toast.makeText(context, "巡游模式：$name", Toast.LENGTH_SHORT).show()
                        }
                        3 -> Toast.makeText(context, "双指捏合即可缩放", Toast.LENGTH_SHORT).show()
                        4 -> visibility = View.GONE
                    }
                }
                .show()
        }

        private fun askRename() {
            val et = EditText(context)
            et.inputType = InputType.TYPE_CLASS_TEXT
            et.setText("大肥鱼")
            AlertDialog.Builder(context)
                .setTitle("给它起个名字")
                .setView(et)
                .setPositiveButton("确定") { _, _ -> showBubble("以后我就叫${et.text}啦") }
                .setNegativeButton("取消", null)
                .show()
        }

        private val idleRunnable = object : Runnable {
            override fun run() {
                if (SystemClock.uptimeMillis() > bubbleUntil && !dragging) {
                    showBubble(FishDialogue.pick(FishDialogue.idle))
                }
                postDelayed(this, 15000L)
            }
        }
    }
}
