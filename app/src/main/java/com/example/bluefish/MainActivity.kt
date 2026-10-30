
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
        tts?.stop(); tts?.shutdown(); super.onDestroy()
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
            canvas.drawOval(
                RectF(drawX - r * 1.05f, drawY - r * 0.75f, drawX + r * 1.05f, drawY + r * 0.75f),
                bodyPaint
            )

            paint.color = Color.argb(90, 255, 255, 255)
            canvas.drawOval(
                RectF(drawX - r * 0.5f, drawY, drawX + r * 0.7f, drawY + r * 0.6f),
                paint
            )

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

           
