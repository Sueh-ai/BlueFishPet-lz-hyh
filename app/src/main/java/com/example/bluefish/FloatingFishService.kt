package com.example.bluefish

import android.app.Service
import android.content.Intent
import android.content.res.AssetManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

class FloatingFishService : Service(), SensorEventListener {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: LinearLayout
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var maidFish: ImageView
    private lateinit var tvDialogue: TextView
    private val handler = Handler(Looper.getMainLooper())

    // 🌟 传感器相关
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var sensorX = 0f
    private var sensorY = 0f

    // 物理参数
    private var velocityX = 0f
    private var velocityY = 0f
    private var isDragging = false
    private var gravity = 25f
    private var bounceFactor = -0.65f
    private var friction = 0.96f
    private var screenWidth = 0
    private var screenHeight = 0

    private val idleList = mutableListOf<String>()
    private val walkList = mutableListOf<String>()
    private val sleepList = mutableListOf<String>()
    private val playList = mutableListOf<String>()
    private val clickList = mutableListOf<String>()

    private val clickLines = listOf(
        "我亲爱的黎子少爷", "今天还爱我", "我好想你", 
        "少爷，你终于来看我啦！", "一直盯着你看，都不好意思了...", "最喜欢少爷摸我头了~",
        "黎子少爷今天也很帅气呢！", "只要能在少爷身边，大肥鱼就很开心了。", "少爷，你要去哪儿呀？带上我好不好？",
        "偷偷亲少爷一下，嘿嘿~", "今天也想和少爷贴贴！", "少爷，你摸摸我的心跳，好快呀。", "别拽我尾巴啦，要秃了！", "哼，怎么现在才来找我？"
    )
    private val idleLines = listOf("好无聊呀...", "今天天气真好。")
    private val sleepLines = listOf("呼...呼...", "Zzz...")

    private var currentState = "IDLE"
    private var lastClickTime = 0L
    private var clickCount = 0

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // 初始化重力传感器
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        val prefs = getSharedPreferences("fish_prefs", MODE_PRIVATE)
        val fishSize = prefs.getInt("fish_size", 600)

        floatingView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
        }

        val bubbleBg = GradientDrawable().apply {
            setColor(Color.parseColor("#E6192124"))
            cornerRadius = 30f
            setStroke(2, Color.parseColor("#3A3A3A"))
        }
        
        tvDialogue = TextView(this).apply {
            background = bubbleBg
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(30, 20, 30, 20)
            visibility = View.INVISIBLE
            alpha = 0f 
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            // 🌟 修复气泡距离：把 margin 改成负数，让她俩贴贴！
            lp.bottomMargin = -20 
            layoutParams = lp
        }
        floatingView.addView(tvDialogue)

        maidFish = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(fishSize, fishSize)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(20, 20, 20, 20)
        }
        floatingView.addView(maidFish)

        val displayMetrics = resources.displayMetrics
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth / 2 - (fishSize / 2)
            y = 200
        }

        windowManager.addView(floatingView, params)
        loadAssetsFiles()
        playRandomAnimation(idleList)
        setupTouchListener()
        handler.post(physicsRunnable)
        handler.postDelayed(stateMachineRunnable, 5000)
    }

    // 传感器回调：实时获取手机的倾斜角度
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            // 手机倾斜时，改变重力方向
            sensorX = -event.values[0] // 左右倾斜
            sensorY = event.values[1]  // 前后倾斜
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun loadAssetsFiles() {
        val assetManager: AssetManager = assets
        val allFiles = assetManager.list("webp") ?: return
        for (file in allFiles) {
            when {
                file.startsWith("idle") -> idleList.add(file)
                file.startsWith("walk") -> walkList.add(file)
                file.startsWith("sleep") -> sleepList.add(file)
                file.startsWith("play") -> playList.add(file)
                file.startsWith("click") -> clickList.add(file)
            }
        }
    }

    private fun playRandomAnimation(list: List<String>) {
        if (list.isNotEmpty()) {
            val file = list[Random.nextInt(list.size)]
            Glide.with(this).load("file:///android_asset/webp/$file").into(maidFish)
        }
    }

    private val stateMachineRunnable = object : Runnable {
        override fun run() {
            if (!isDragging) {
                currentState = when (Random.nextInt(100)) {
                    in 0..30 -> "IDLE"   
                    in 31..70 -> "WALK"  
                    in 71..85 -> "PLAY"  
                    else -> "SLEEP"      
                }
                when (currentState) {
                    "IDLE" -> { 
                        playRandomAnimation(idleList)
                    }
                    "WALK" -> { 
                        playRandomAnimation(walkList)
                        velocityX = if (Random.nextBoolean()) Random.nextInt(10, 20).toFloat() else -Random.nextInt(10, 20).toFloat()
                    }
                    "PLAY" -> { 
                        playRandomAnimation(playList)
                        if (params.y + floatingView.height >= screenHeight - 10) {
                            velocityY = -35f 
                        }
                    }
                    "SLEEP" -> { 
                        playRandomAnimation(sleepList)
                        velocityX = 0f
                        showDialogue(sleepLines[Random.nextInt(sleepLines.size)])
                    }
                }
            }
            handler.postDelayed(this, 6000) 
        }
    }

    private fun setupTouchListener() {
        floatingView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var lastTouchX = 0f
            private var lastTouchY = 0f
            private var downTime = 0L

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isDragging = true
                        initialX = params.x; initialY = params.y
                        initialTouchX = event.rawX; initialTouchY = event.rawY
                        lastTouchX = event.rawX; lastTouchY = event.rawY
                        downTime = System.currentTimeMillis()
                        hideDialogue()
                        maidFish.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, params)
                        
                        velocityX = (event.rawX - lastTouchX)
                        velocityY = (event.rawY - lastTouchY)
                        lastTouchX = event.rawX; lastTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        isDragging = false
                        maidFish.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                        
                        if (System.currentTimeMillis() - downTime < 200 && abs(event.rawX - initialTouchX) < 10) {
                            triggerClickInteraction()
                        } else {
                            // 🌟 甩出去的惯性：加大力度，指哪飞哪
                            velocityX = (event.rawX - lastTouchX) * 3.0f
                            velocityY = (event.rawY - lastTouchY) * 3.0f
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private val physicsRunnable = object : Runnable {
        override fun run() {
            if (!isDragging) {
                // 🌟 核心：加入重力传感器的影响
                // sensorX (左右倾斜) 会让鱼左右滑动，sensorY (上下倾斜) 会让她加速下落或起飞
                velocityX += sensorX * 0.8f 
                velocityY += sensorY * 0.8f + (gravity / 10f)

                // 速度上限限制（防止手机猛晃导致她飞出屏幕）
                val maxSpeed = 60f
                if (velocityX > maxSpeed) velocityX = maxSpeed
                if (velocityX < -maxSpeed) velocityX = -maxSpeed
                if (velocityY > maxSpeed) velocityY = maxSpeed
                if (velocityY < -maxSpeed) velocityY = -maxSpeed

                params.x += velocityX.toInt()
                params.y += velocityY.toInt()

                // 左右碰撞
                if (params.x <= 0) {
                    params.x = 0
                    velocityX = -velocityX * bounceFactor
                } else if (params.x + floatingView.width >= screenWidth) {
                    params.x = screenWidth - floatingView.width
                    velocityX = -velocityX * bounceFactor
                }

                // 底部碰撞与落地
                if (params.y + floatingView.height >= screenHeight) {
                    params.y = screenHeight - floatingView.height
                    
                    if (abs(velocityY) > 15f) {
                        velocityY = -velocityY * bounceFactor
                        squash() 
                    } else {
                        // 🌟 平稳落地
                        velocityY = 0f
                        velocityX *= friction
                        // 当水平速度很小，并且手机不倾斜时，彻底停下
                        if (abs(velocityX) < 0.5f && abs(sensorX) < 1.0f) velocityX = 0f 
                    }
                } else if (params.y <= 0) {
                    params.y = 0
                    velocityY = -velocityY * bounceFactor
                }

                windowManager.updateViewLayout(floatingView, params)
            }
            handler.postDelayed(this, 16)
        }
    }

    private fun squash() {
        maidFish.animate().cancel()
        maidFish.scaleX = 1.2f
        maidFish.scaleY = 0.8f
        maidFish.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
    }

    private fun triggerClickInteraction() {
        maidFish.animate().cancel()
        maidFish.scaleX = 0.8f
        maidFish.scaleY = 1.2f
        maidFish.animate().scaleX(1f).scaleY(1f).setDuration(300).start()

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime < 1000) {
            clickCount++
        } else {
            clickCount = 1
        }
        lastClickTime = currentTime

        if (clickCount >= 5) {
            showDialogue("哎呀！少爷你点疼我了！")
            playRandomAnimation(clickList)
            velocityY = -30f
            clickCount = 0
        } else {
            playRandomAnimation(if (clickList.isNotEmpty()) clickList else idleList)
            val line = clickLines[Random.nextInt(clickLines.size)]
            showDialogue(line)
        }
    }

    private fun showDialogue(text: String) {
        tvDialogue.text = text
        tvDialogue.animate().cancel() 
        tvDialogue.visibility = View.VISIBLE
        tvDialogue.animate().alpha(1f).setDuration(200).start()
        handler.removeCallbacks(hideDialogueRunnable)
        handler.postDelayed(hideDialogueRunnable, 3000) 
    }

    private fun hideDialogue() {
        tvDialogue.animate().cancel()
        tvDialogue.animate().alpha(0f).setDuration(200).withEndAction {
            tvDialogue.visibility = View.INVISIBLE
        }.start()
    }

    private val hideDialogueRunnable = Runnable { hideDialogue() }

    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        // 注销传感器，节约电量
        sensorManager.unregisterListener(this)
        if (floatingView.isAttachedToWindow) windowManager.removeView(floatingView)
    }
}
