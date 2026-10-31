package com.example.bluefish

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

class FloatingFishService : Service(), SensorEventListener {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: LinearLayout
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var maidFish: ImageView
    private lateinit var tvDialogue: TextView
    private val handler = Handler(Looper.getMainLooper())

    // 传感器
    private lateinit var sensorManager: SensorManager
    private var lastShakeTime = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f

    // 🌟 核心物理变量 (px/s)
    private var posX = 0.0f
    private var posY = 0.0f
    private var velX = 0.0f
    private var velY = 0.0f
    
    private var isDragging = false
    private var isHidden = false 
    private var lastInteractionTime = 0L 
    
    // 拖拽目标点
    private var targetX = 0.0f
    private var targetY = 0.0f

    // 🌟 提取自大神 Physics.kt 的参数
    private val GRAVITY = 1400.0f
    private val RESTITUTION = 0.78f
    private val GROUND_FRICTION = 2.5f
    private val SPRING_K = 200.0f
    private val SPRING_C = 30.0f
    
    private val REST_VY = 40.0f
    private val REST_VX = 15.0f
    
    // 轨迹采样（用于估算抛掷速度）
    private val trail = ArrayList<Pair<Long, Pair<Float, Float>>>()
    private val MAX_TRAIL_MS = 200L
    
    private var screenWidth = 0
    private var screenHeight = 0
    private var fishSize = 800

    private val idleList = mutableListOf<String>()
    private val walkList = mutableListOf<String>()
    private val playList = mutableListOf<String>()
    private val clickList = mutableListOf<String>()

    private val clickLines = listOf(
        "我亲爱的黎子少爷", "今天还爱我吗", "我好想你呀", 
        "少爷，你终于来看我啦！", "一直盯着你看，都不好意思了...", "最喜欢少爷摸我头了~",
        "黎子少爷今天也很帅气呢！", "只要能在少爷身边，大肥鱼就很开心了。",
        "偷偷亲少爷一下，嘿嘿~", "少爷，你摸摸我的心跳，好快呀。",
        "少爷，今天中午吃什么呀？我饿了...", "我刚刚差点从屏幕上掉下去了！",
        "少爷，你的手机屏幕有点脏了，帮我擦擦呗~", "别拽我尾巴啦，要秃了！"
    )

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        setupForegroundService()

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME)

        val prefs = getSharedPreferences("fish_prefs", MODE_PRIVATE)
        fishSize = prefs.getInt("fish_size", 800)

        floatingView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }

        tvDialogue = TextView(this).apply {
            val bubbleBg = GradientDrawable().apply {
                setColor(Color.parseColor("#E6192124"))
                cornerRadius = 30f
                setStroke(2, Color.parseColor("#3A3A3A"))
            }
            background = bubbleBg
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(30, 20, 30, 20)
            visibility = View.INVISIBLE
            alpha = 0f 
            gravity = Gravity.CENTER
            maxWidth = fishSize - 40 
            isSingleLine = false
        }
        floatingView.addView(tvDialogue)

        maidFish = ImageView(this).apply {
            val lp = LinearLayout.LayoutParams(fishSize, fishSize)
            lp.bottomMargin = -20
            layoutParams = lp
            scaleType = ImageView.ScaleType.FIT_CENTER
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
            y = screenHeight / 2 - (fishSize / 2) 
        }

        posX = params.x.toFloat()
        posY = params.y.toFloat()

        windowManager.addView(floatingView, params)
        loadAssetsFiles()
        playRandomAnimation(idleList)
        setupTouchListener()
        
        // 启动物理引擎与状态机
        handler.post(physicsRunnable)
        handler.postDelayed(stateMachineRunnable, 20000) 
        handler.post(hideRunnable) 
    }

    private fun setupForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "pet_service_channel"
            val channel = NotificationChannel(channelId, "桌宠保活服务", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            val notification: Notification = Notification.Builder(this, channelId)
                .setContentTitle("大肥鱼正在陪伴你")
                .setContentText("她在你的屏幕上玩耍呢~")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .build()
            startForeground(1, notification)
        }
    }

    // 状态机
    private val stateMachineRunnable = object : Runnable {
        override fun run() {
            if (!isDragging && !isHidden) {
                val r = Random.nextInt(100)
                val state = when {
                    r < 30 -> "IDLE"   
                    r < 70 -> "WALK"   
                    else -> "PLAY"     
                }
                
                when (state) {
                    "IDLE" -> { playRandomAnimation(idleList) }
                    "WALK" -> { 
                        playRandomAnimation(walkList)
                        // 给一个随机的横向冲刺速度
                        velX = if (Random.nextBoolean()) Random.nextInt(200, 400).toFloat() else -Random.nextInt(200, 400).toFloat()
                    }
                    "PLAY" -> { 
                        playRandomAnimation(playList)
                        velY = -Random.nextInt(300, 500).toFloat()
                        velX = Random.nextInt(-300, 300).toFloat()
                    }
                }
            }
            handler.postDelayed(this, 20000) 
        }
    }

    // 贴边躲藏
    private val hideRunnable = object : Runnable {
        override fun run() {
            if (!isDragging && !isHidden) {
                if (System.currentTimeMillis() - lastInteractionTime >= 12000) {
                    if (posX <= 20) { 
                        maidFish.animate().translationX(-fishSize / 2f).setDuration(300).start()
                        isHidden = true
                    } else if (posX + fishSize >= screenWidth - 20) { 
                        maidFish.animate().translationX(fishSize / 2f).setDuration(300).start()
                        isHidden = true
                    }
                }
            }
            handler.postDelayed(this, 1000)
        }
    }

    private fun reveal() {
        if (isHidden) {
            maidFish.animate().translationX(0f).setDuration(200).start()
            isHidden = false
        }
        lastInteractionTime = System.currentTimeMillis()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val currentTime = System.currentTimeMillis()
            if ((currentTime - lastShakeTime) > 400) {
                val deltaX = abs(x - lastX)
                val deltaY = abs(y - lastY)
                val deltaZ = abs(z - lastZ)
                val speed = sqrt((deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ).toDouble()).toFloat()

                if (speed > 12f && !isDragging) { 
                    lastShakeTime = currentTime
                    reveal()
                    handler.post {
                        playRandomAnimation(playList)
                        velY = -Random.nextInt(400, 700).toFloat()
                        velX = Random.nextInt(-400, 400).toFloat()
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

    private fun setupTouchListener() {
        floatingView.setOnTouchListener(object : View.OnTouchListener {
            private var downTime = 0L
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        reveal()
                        isDragging = true
                        downTime = System.currentTimeMillis()
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        
                        targetX = posX
                        targetY = posY
                        
                        trail.clear()
                        trail.add(Pair(downTime, Pair(event.rawX, event.rawY)))
                        hideDialogue()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val now = System.currentTimeMillis()
                        trail.add(Pair(now, Pair(event.rawX, event.rawY)))
                        // 保持轨迹队列不超时
                        trail.removeAll { now - it.first > MAX_TRAIL_MS }

                        // 将手指移动映射到目标位置
                        targetX += (event.rawX - initialTouchX) * 0.3f
                        targetY += (event.rawY - initialTouchY) * 0.3f
                        
                        // 边界限制（防止拖出屏幕）
                        targetX = targetX.coerceIn(0f, (screenWidth - floatingView.width).toFloat())
                        targetY = targetY.coerceIn(0f, (screenHeight - floatingView.height).toFloat())

                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        isDragging = false
                        
                        if (System.currentTimeMillis() - downTime < 200 && abs(event.rawX - initialTouchX) < 10) {
                            // 点击
                            triggerClickInteraction()
                            velX = 0f
                            velY = 0f
                        } else {
                            // 抛掷：推算速度
                            val now = System.currentTimeMillis()
                            val validTrail = trail.filter { now - it.first <= 150L }
                            if (validTrail.size >= 2) {
                                val t0 = validTrail.first().first
                                val t1 = validTrail.last().first
                                val x0 = validTrail.first().second.first
                                val x1 = validTrail.last().second.first
                                val y0 = validTrail.first().second.second
                                val y1 = validTrail.last().second.second
                                
                                val dt = (t1 - t0).toFloat()
                                if (dt > 20f) {
                                    velX = ((x1 - x0) / dt * 1000f).coerceIn(-3000f, 3000f)
                                    velY = ((y1 - y0) / dt * 1000f).coerceIn(-3000f, 3000f)
                                } else {
                                    velX = 0f; velY = 0f
                                }
                            } else {
                                velX = 0f; velY = 0f
                            }
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    // 🌟 基于真实时间 (dt) 的物理引擎
    private val physicsRunnable = object : Runnable {
        override fun run() {
            val dt = 0.016f // 约 60 FPS 的时间步长 (秒)
            
            if (isDragging) {
                // 过阻尼弹簧跟手 (Spring K=200, C=30)
                val forceX = (targetX - posX) * SPRING_K - velX * SPRING_C
                val forceY = (targetY - posY) * SPRING_K - velY * SPRING_C
                
                velX += forceX * dt
                velY += forceY * dt
                
                posX += velX * dt
                posY += velY * dt
            } else if (!isHidden) {
                // 纯正向下重力
                velY += GRAVITY * dt
                
                posX += velX * dt
                posY += velY * dt

                // 碰到左右边缘：反弹
                if (posX <= 0) {
                    posX = 0f
                    velX = -velX * RESTITUTION
                    jellySquash()
                } else if (posX + floatingView.width >= screenWidth) {
                    posX = (screenWidth - floatingView.width).toFloat()
                    velX = -velX * RESTITUTION
                    jellySquash()
                }

                // 碰到上下边缘
                if (posY <= 0) {
                    posY = 0f
                    velY = -velY * RESTITUTION
                    jellySquash()
                } else if (posY + floatingView.height >= screenHeight) {
                    posY = (screenHeight - floatingView.height).toFloat()
                    
                    // 如果下落速度足够快，就弹起
                    if (abs(velY) > REST_VY) {
                        velY = -velY * RESTITUTION
                        jellySquash()
                    } else {
                        // 停在地面
                        velY = 0f
                    }
                    
                    // 地面摩擦力作用在水平速度上
                    velX -= velX * GROUND_FRICTION * dt
                    if (abs(velX) < REST_VX) velX = 0f
                }
            }

            // 同步到系统悬浮窗坐标
            if (!isHidden) {
                params.x = posX.toInt()
                params.y = posY.toInt()
                windowManager.updateViewLayout(floatingView, params)
            }
            
            handler.postDelayed(this, 16)
        }
    }

    private fun jellySquash() {
        maidFish.animate().cancel()
        maidFish.scaleX = 1.25f
        maidFish.scaleY = 0.75f
        maidFish.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setInterpolator(OvershootInterpolator(3.5f)) 
            .setDuration(350) 
            .start()
    }

    private fun triggerClickInteraction() {
        jellySquash()
        playRandomAnimation(if (clickList.isNotEmpty()) clickList else idleList)
        showDialogue(clickLines[Random.nextInt(clickLines.size)])
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
