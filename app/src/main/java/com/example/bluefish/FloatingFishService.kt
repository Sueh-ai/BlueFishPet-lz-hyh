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
import kotlin.math.sqrt
import kotlin.random.Random

class FloatingFishService : Service(), SensorEventListener {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: LinearLayout
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var maidFish: ImageView
    private lateinit var tvDialogue: TextView
    private val handler = Handler(Looper.getMainLooper())

    // 传感器（仅用于摇晃检测，不再用于翻转重力）
    private lateinit var sensorManager: SensorManager
    private var lastShakeTime = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f

    // 物理参数 (向下重力，干脆的弹跳)
    private var velocityX = 0f
    private var velocityY = 0f
    private var isDragging = false
    private var isHidden = false // 是否正在贴边隐藏
    private var lastInteractionTime = 0L // 记录最后互动时间

    private val gravityBase = 1.5f // 🌟 下落速度稍微快一点
    private val airFriction = 0.98f 
    private val restitution = 0.5f // 🌟 弹性降低，弹两下就停
    private val minBounceSpeedY = 3.0f // 🌟 阈值调高，弹幅小就不弹了
    
    private var screenWidth = 0
    private var screenHeight = 0
    private var fishSize = 800

    private val idleList = mutableListOf<String>()
    private val walkList = mutableListOf<String>()
    private val sleepList = mutableListOf<String>()
    private val playList = mutableListOf<String>()
    private val clickList = mutableListOf<String>()

    private val clickLines = listOf(
        "我亲爱的黎子少爷", "今天还爱我吗", "我好想你呀", 
        "少爷，你终于来看我啦！", "一直盯着你看，都不好意思了...", "最喜欢少爷摸我头了~",
        "黎子少爷今天也很帅气呢！", "只要能在少爷身边，大肥鱼就很开心了。", "少爷，你要去哪儿呀？带上我好不好？",
        "偷偷亲少爷一下，嘿嘿~", "今天也想和少爷贴贴！", "少爷，你摸摸我的心跳，好快呀。",
        "外面风好大，少爷快躲到我身后！", "少爷，我给你捶捶腿吧？", "想变成少爷口袋里的小鱼干，一直跟着你。",
        "少爷，今天中午吃什么呀？我饿了...", "少爷，你昨晚是不是又熬夜了？黑眼圈都出来了！",
        "我刚刚差点从屏幕上掉下去了，吓死我了！", "少爷，你的手机屏幕有点脏了，帮我擦擦呗~",
        "今天天气真好，好想和少爷一起出去玩。", "如果少爷每天都能陪我玩就好了。",
        "哎呀，少爷你点得太快啦，我都眼花了！", "少爷，你是不是又忘记喂我了？",
        "我是大肥鱼，会吐泡泡的那种哦！", "别拽我尾巴啦，要秃了！", "哎呀！你弄乱我的头发了！",
        "再戳我，大肥鱼可要生气了哦！", "哼，怎么现在才来找我？", "笨蛋少爷，我才没有想你！",
        "走开走开，我要睡觉啦。", "不理你了，除非你拿小鱼干来哄我！"
    )

    private val idleLines = listOf("好无聊呀...", "今天天气真好。", "少爷在忙什么呢？", "飘来飘去~")
    private val sleepLines = listOf("呼...呼...", "Zzz...", "梦里也有少爷呢...")

    private var currentState = "IDLE"
    private var lastClickTime = 0L
    private var clickCount = 0

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // 🌟 启动前台服务保活
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

        windowManager.addView(floatingView, params)
        loadAssetsFiles()
        playRandomAnimation(idleList)
        setupTouchListener()
        
        handler.post(physicsRunnable)
        handler.postDelayed(stateMachineRunnable, 30000) // 🌟 动作间隔改为 30 秒
        handler.post(hideRunnable) // 🌟 启动贴边隐藏检测
    }

    // 🌟 前台服务保活通知
    private fun setupForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "pet_service_channel"
            val channelName = "桌宠保活服务"
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)

            val notification: Notification = Notification.Builder(this, channelId)
                .setContentTitle("大肥鱼正在陪伴你")
                .setContentText("她在你的屏幕上玩耍呢~")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .build()
            
            // 启动前台服务
            startForeground(1, notification)
        }
    }

    // 🌟 状态机：每 30 秒换一次动作
    private val stateMachineRunnable = object : Runnable {
        override fun run() {
            if (!isDragging && !isHidden) {
                val r = Random.nextInt(100)
                currentState = when {
                    r < 40 -> "IDLE"   
                    r < 80 -> "WALK"   // 🌟 增加漫步概率，满屏幕跑
                    else -> "PLAY"     // 🌟 只有跳跃和漫步
                }
                
                when (currentState) {
                    "IDLE" -> { 
                        playRandomAnimation(idleList) 
                        velocityX = 0f
                    }
                    "WALK" -> { 
                        playRandomAnimation(walkList)
                        // 🌟 随机方向漫游，不会局限在一个地方
                        velocityX = if (Random.nextBoolean()) Random.nextInt(10, 20).toFloat() else -Random.nextInt(10, 20).toFloat()
                    }
                    "PLAY" -> { 
                        playRandomAnimation(playList)
                        velocityY = -Random.nextInt(15, 25).toFloat()
                        velocityX = Random.nextInt(-20, 20).toFloat()
                    }
                }
            }
            handler.postDelayed(this, 30000) 
        }
    }

    // 🌟 贴边躲藏检测（每秒钟检查一次）
    private val hideRunnable = object : Runnable {
        override fun run() {
            if (!isDragging && !isHidden) {
                if (System.currentTimeMillis() - lastInteractionTime >= 12000) {
                    if (params.x <= 20) { // 贴左边
                        maidFish.animate().translationX(-fishSize / 2f).setDuration(300).start()
                        isHidden = true
                    } else if (params.x + fishSize >= screenWidth - 20) { // 贴右边
                        maidFish.animate().translationX(fishSize / 2f).setDuration(300).start()
                        isHidden = true
                    }
                }
            }
            handler.postDelayed(this, 1000)
        }
    }

    // 露出隐藏的身子
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
                    reveal() // 摇晃时如果隐藏了，也会弹出来
                    handler.post {
                        playRandomAnimation(playList)
                        velocityY = -Random.nextInt(15, 25).toFloat()
                        velocityX = Random.nextInt(-15, 15).toFloat()
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
                        reveal() // 碰一下就探出来
                        isDragging = true
                        initialX = params.x; initialY = params.y
                        initialTouchX = event.rawX; initialTouchY = event.rawY
                        lastTouchX = event.rawX; lastTouchY = event.rawY
                        downTime = System.currentTimeMillis()
                        hideDialogue()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        
                        params.x = (initialX + dx).coerceIn(0, screenWidth - floatingView.width)
                        params.y = (initialY + dy).coerceIn(0, screenHeight - floatingView.height)
                        
                        windowManager.updateViewLayout(floatingView, params)
                        
                        velocityX = (event.rawX - lastTouchX) * 0.8f
                        velocityY = (event.rawY - lastTouchY) * 0.8f
                        lastTouchX = event.rawX; lastTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        isDragging = false
                        if (System.currentTimeMillis() - downTime < 200 && abs(event.rawX - initialTouchX) < 10) {
                            triggerClickInteraction()
                        } else {
                            velocityX = (event.rawX - lastTouchX) * 1.5f
                            velocityY = (event.rawY - lastTouchY) * 1.5f
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
            if (!isDragging && !isHidden) {
                // 🌟 始终向下的重力
                velocityY += gravityBase
                
                velocityX *= airFriction
                velocityY *= airFriction

                if (abs(velocityX) < 0.05f) velocityX = 0f

                params.x += velocityX.toInt()
                params.y += velocityY.toInt()

                var hitEdge = false

                // 左右碰壁反弹
                if (params.x <= 0) {
                    params.x = 0
                    velocityX = abs(velocityX) * 0.8f + 5f 
                    hitEdge = true
                } else if (params.x + floatingView.width >= screenWidth) {
                    params.x = screenWidth - floatingView.width
                    velocityX = -(abs(velocityX) * 0.8f + 5f)
                    hitEdge = true
                }

                // 🌟 落地干脆的反弹
                if (params.y <= 0) {
                    params.y = 0
                    if (abs(velocityY) > minBounceSpeedY) {
                        velocityY = abs(velocityY) * restitution
                        hitEdge = true
                    } else {
                        velocityY = 0f
                    }
                } else if (params.y + floatingView.height >= screenHeight) {
                
                    params.y = screenHeight - floatingView.height
                    if (abs(velocityY) > minBounceSpeedY) {
                        velocityY = -abs(velocityY) * restitution
                        hitEdge = true
                    } else {
                        velocityY = 0f // 停下
                        velocityX = 0f 
                    }
                }

                if (hitEdge) {
                    jellySquash()
                }

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
            velocityY = -15f
            velocityX = Random.nextInt(-15, 15).toFloat()
            clickCount = 0
        } else {
            playRandomAnimation(if (clickList.isNotEmpty()) clickList else idleList)
            showDialogue(clickLines[Random.nextInt(clickLines.size)])
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
