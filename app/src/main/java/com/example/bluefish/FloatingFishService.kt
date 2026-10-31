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
    private var sensorX = 0f // 手机左右倾斜的重力
    private var sensorY = 0f // 手机上下倾斜的重力
    
    private var lastShakeTime = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f

    // 物理参数 (轻盈版)
    private var velocityX = 0f
    private var velocityY = 0f
    private var isDragging = false
    private var gravityBase = 2.5f // 🌟 基础重力极小，像气球一样轻盈
    private var bounceFactor = -0.85f // 🌟 弹力超强
    private var friction = 0.99f // 几乎无摩擦，能在屏幕上滑很远
    private var screenWidth = 0
    private var screenHeight = 0

    private val idleList = mutableListOf<String>()
    private val walkList = mutableListOf<String>()
    private val sleepList = mutableListOf<String>()
    private val playList = mutableListOf<String>()
    private val clickList = mutableListOf<String>()

    // 🌟 台词库全面扩充
    private val clickLines = listOf(
        "我亲爱的黎子少爷", "今天还爱我", "我好想你", 
        "少爷，你终于来看我啦！", "一直盯着你看，都不好意思了...", "最喜欢少爷摸我头了~",
        "黎子少爷今天也很帅气呢！", "只要能在少爷身边，大肥鱼就很开心了。", "少爷，你要去哪儿呀？带上我好不好？",
        "偷偷亲少爷一下，嘿嘿~", "今天也想和少爷贴贴！", "少爷，你摸摸我的心跳，好快呀。",
        "外面风好大，少爷快躲到我身后！", "哼，怎么现在才来找我？", "别拽我尾巴啦，要秃了！",
        "哎呀！你弄乱我的头发了！", "少爷，你点得我头好晕呀...", "再戳我，大肥鱼可要生气了哦！",
        "笨蛋少爷，我才没有想你！", "走开走开，我要睡觉啦。", "少爷，你是不是又忘记喂我了？",
        "不理你了，除非你拿小鱼干来哄我！", "大肥鱼也是有脾气的！", "我是大肥鱼，会吐泡泡的那种哦！"
    )
    private val shakeLines = listOf("哎呀！地震了吗？", "晃得我头晕眼花...", "少爷别摇啦！", "救命呀！我要飞出去了！", "哇哦——飞高高！")
    private val idleLines = listOf("好无聊呀...", "今天天气真好。", "少爷在忙什么呢？", "肚子好像有点饿了...", "想出去玩~")
    private val sleepLines = listOf("呼...呼...", "Zzz...", "梦里也有少爷呢...", "别吵，我在睡觉...")

    private var currentState = "IDLE"
    private var lastClickTime = 0L
    private var clickCount = 0

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // 注册加速度传感器（同时用于检测倾斜和摇晃）
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
            lp.bottomMargin = -10 
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
        handler.postDelayed(autoChatRunnable, 15000) // 15秒自动说一次话
    }

    // 🌟 核心：同时处理“倾斜重力”和“摇晃检测”
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // 1. 倾斜重力：手机往哪边歪，她就往哪边滚
            // 乘以一个系数调整重力感应的灵敏度
            sensorX = -x * 1.5f 
            sensorY = y * 1.5f

            // 2. 摇晃检测：加速度突变时弹跳
            val currentTime = System.currentTimeMillis()
            if ((currentTime - lastShakeTime) > 500) {
                val deltaX = abs(x - lastX)
                val deltaY = abs(y - lastY)
                val deltaZ = abs(z - lastZ)
                val speed = sqrt((deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ).toDouble()).toFloat()

                // 阈值调低到 12f，稍微晃一下就能感应到
                if (speed > 12f && !isDragging) {
                    lastShakeTime = currentTime
                    handler.post {
                        playRandomAnimation(playList)
                        showDialogue(shakeLines[Random.nextInt(shakeLines.size)])
                        // 给一个随机的猛力弹跳
                        velocityY = -Random.nextInt(20, 35).toFloat()
                        velocityX = Random.nextInt(-20, 20).toFloat()
                    }
                }
            }
            lastX = x; lastY = y; lastZ = z
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

    // 自动状态机
    private val stateMachineRunnable = object : Runnable {
        override fun run() {
            if (!isDragging) {
                currentState = when (Random.nextInt(100)) {
                    in 0..30 -> "IDLE"   
                    in 31..60 -> "WALK"  
                    in 61..85 -> "PLAY"  // 增加自己弹跳的概率
                    else -> "SLEEP"      
                }
                when (currentState) {
                    "IDLE" -> { playRandomAnimation(idleList) }
                    "WALK" -> { 
                        playRandomAnimation(walkList)
                        // 给她一个自己溜达的速度
                        velocityX += if (Random.nextBoolean()) Random.nextInt(5, 10).toFloat() else -Random.nextInt(5, 10).toFloat()
                    }
                    "PLAY" -> { 
                        playRandomAnimation(playList)
                        // 自己到处乱蹦
                        velocityY = -Random.nextInt(15, 30).toFloat()
                        velocityX = Random.nextInt(-15, 15).toFloat()
                    }
                    "SLEEP" -> { 
                        playRandomAnimation(sleepList)
                        velocityX = 0f
                        showDialogue(sleepLines[Random.nextInt(sleepLines.size)])
                    }
                }
            }
            handler.postDelayed(this, 5000) 
        }
    }

    // 🌟 自动自言自语
    private val autoChatRunnable = object : Runnable {
        override fun run() {
            if (!isDragging && tvDialogue.visibility != View.VISIBLE && currentState == "IDLE") {
                showDialogue(idleLines[Random.nextInt(idleLines.size)])
            }
            handler.postDelayed(this, Random.nextLong(15000, 30000)) // 15到30秒随机说一次
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
                        if (System.currentTimeMillis() - downTime < 200 && abs(event.rawX - initialTouchX) < 10) {
                            triggerClickInteraction()
                        } else {
                            // 甩飞惯性：乘 2.5 让她飞得更远
                            velocityX = (event.rawX - lastTouchX) * 2.5f
                            velocityY = (event.rawY - lastTouchY) * 2.5f
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    // 🌟 轻盈的物理引擎
    private val physicsRunnable = object : Runnable {
        override fun run() {
            if (!isDragging) {
                // 融合手机倾斜的重力 (sensorX, sensorY) 和基础向下重力
                velocityX += sensorX
                velocityY += sensorY + gravityBase
                
                // 限制最大速度，防止手机猛晃时飞出屏幕
                val maxSpeed = 40f
                if (velocityX > maxSpeed) velocityX = maxSpeed
                if (velocityX < -maxSpeed) velocityX = -maxSpeed
                if (velocityY > maxSpeed) velocityY = maxSpeed
                if (velocityY < -maxSpeed) velocityY = -maxSpeed

                params.x += velocityX.toInt()
                params.y += velocityY.toInt()

                // 碰壁反弹逻辑
                if (params.x <= 0) {
                    params.x = 0
                    velocityX = -velocityX * bounceFactor
                } else if (params.x + floatingView.width >= screenWidth) {
                    params.x = screenWidth - floatingView.width
                    velocityX = -velocityX * bounceFactor
                }

                if (params.y + floatingView.height >= screenHeight) {
                    params.y = screenHeight - floatingView.height
                    if (abs(velocityY) > 10f) {
                        velocityY = -velocityY * bounceFactor
                        cuteSquash() 
                    } else {
                        // 速度快时才弹，速度慢时停在底部，但保持左右滑动
                        velocityY = 0f
                        velocityX *= friction
                        if (abs(velocityX) < 1f) velocityX = 0f 
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

    // 轻微Q弹
    private fun cuteSquash() {
        maidFish.animate().cancel()
        maidFish.scaleX = 1.1f
        maidFish.scaleY = 0.9f
        maidFish.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
    }

    private fun triggerClickInteraction() {
        maidFish.animate().cancel()
        maidFish.scaleX = 0.85f
        maidFish.scaleY = 1.15f
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
            velocityY = -20f
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
        sensorManager.unregisterListener(this)
        if (floatingView.isAttachedToWindow) windowManager.removeView(floatingView)
    }
}
