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
    
    private var lastShakeTime = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f

    // 物理参数 (绝对轻盈版)
    private var velocityX = 0f
    private var velocityY = 0f
    private var isDragging = false
    private var gravityBase = 0.3f // 🌟 重力几乎为 0，像太空中漂浮的气球，不会总落底
    private var bounceFactor = -0.98f // 🌟 弹力拉满，撞击几乎不损失能量
    private var friction = 1.0f // 🌟 无摩擦，在屏幕上滑翔得极远
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
        "偷偷亲少爷一下，嘿嘿~", "今天也想和少爷贴贴！", "少爷，你摸摸我的心跳，好快呀。",
        "外面风好大，少爷快躲到我身后！", "哼，怎么现在才来找我？", "别拽我尾巴啦，要秃了！",
        "哎呀！你弄乱我的头发了！", "少爷，你点得我头好晕呀...", "再戳我，大肥鱼可要生气了哦！",
        "笨蛋少爷，我才没有想你！", "走开走开，我要睡觉啦。", "少爷，你是不是又忘记喂我了？",
        "不理你了，除非你拿小鱼干来哄我！", "大肥鱼也是有脾气的！", "我是大肥鱼，会吐泡泡的那种哦！"
    )
    private val shakeLines = listOf("哎呀！地震了吗？", "晃得我头晕眼花...", "少爷别摇啦！", "救命呀！我要飞出去了！", "哇哦——飞高高！", "晕了晕了，别晃啦~")
    private val idleLines = listOf("好无聊呀...", "今天天气真好。", "少爷在忙什么呢？", "肚子好像有点饿了...", "想出去玩~", "飘来飘去的感觉真好！")
    private val sleepLines = listOf("呼...呼...", "Zzz...", "梦里也有少爷呢...", "别吵，我在睡觉...")

    private var currentState = "IDLE"
    private var lastClickTime = 0L
    private var clickCount = 0

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
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
        handler.postDelayed(autoChatRunnable, 15000) 
    }

    // 🌟 传感器：极高灵敏度
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // 放大倾斜影响，稍微歪一点手机她就飘走
            sensorX = -x * 2.0f 
            sensorY = y * 2.0f

            val currentTime = System.currentTimeMillis()
            if ((currentTime - lastShakeTime) > 400) {
                val deltaX = abs(x - lastX)
                val deltaY = abs(y - lastY)
                val deltaZ = abs(z - lastZ)
                val speed = sqrt((deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ).toDouble()).toFloat()

                if (speed > 10f && !isDragging) {
                    lastShakeTime = currentTime
                    handler.post {
                        playRandomAnimation(playList)
                        showDialogue(shakeLines[Random.nextInt(shakeLines.size)])
                        // 摇晃时给一个极大的初速度，让她乱撞
                        velocityY = -Random.nextInt(25, 45).toFloat()
                        velocityX = Random.nextInt(-30, 30).toFloat()
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

    private val stateMachineRunnable = object : Runnable {
        override fun run() {
            if (!isDragging) {
                currentState = when (Random.nextInt(100)) {
                    in 0..30 -> "IDLE"   
                    in 31..60 -> "WALK"  
                    in 61..85 -> "PLAY"  
                    else -> "SLEEP"      
                }
                when (currentState) {
                    "IDLE" -> { playRandomAnimation(idleList) }
                    "WALK" -> { 
                        playRandomAnimation(walkList)
                        velocityX += if (Random.nextBoolean()) Random.nextInt(10, 20).toFloat() else -Random.nextInt(10, 20).toFloat()
                    }
                    "PLAY" -> { 
                        playRandomAnimation(playList)
                        // 自己蹦跳，打破平衡
                        velocityY = -Random.nextInt(20, 35).toFloat()
                        velocityX = Random.nextInt(-20, 20).toFloat()
                    }
                    "SLEEP" -> { 
                        playRandomAnimation(sleepList)
                        velocityX = 0f
                        velocityY = 0f
                        showDialogue(sleepLines[Random.nextInt(sleepLines.size)])
                    }
                }
            }
            handler.postDelayed(this, 5000) 
        }
    }

    private val autoChatRunnable = object : Runnable {
        override fun run() {
            if (!isDragging && tvDialogue.visibility != View.VISIBLE && currentState == "IDLE") {
                showDialogue(idleLines[Random.nextInt(idleLines.size)])
            }
            handler.postDelayed(this, Random.nextLong(15000, 30000)) 
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
                            // 甩飞乘 3.0，直接变成流星
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

    // 🌟 极致悬浮物理引擎
    private val physicsRunnable = object : Runnable {
        override fun run() {
            if (!isDragging) {
                // 加上极其微弱的向下重力，以及手机倾斜的重力
                velocityX += sensorX
                velocityY += sensorY + gravityBase
                
                // 速度上限稍微放开，允许她飞得更狂野
                val maxSpeed = 50f
                if (velocityX > maxSpeed) velocityX = maxSpeed
                if (velocityX < -maxSpeed) velocityX = -maxSpeed
                if (velocityY > maxSpeed) velocityY = maxSpeed
                if (velocityY < -maxSpeed) velocityY = -maxSpeed

                params.x += velocityX.toInt()
                params.y += velocityY.toInt()

                // 碰壁反弹，因为反弹系数 0.98，撞击后速度几乎不衰减
                if (params.x <= 0) {
                    params.x = 0
                    velocityX = -velocityX * bounceFactor
                    cuteSquash() // 撞墙Q弹
                } else if (params.x + floatingView.width >= screenWidth) {
                    params.x = screenWidth - floatingView.width
                    velocityX = -velocityX * bounceFactor
                    cuteSquash()
                }

                if (params.y + floatingView.height >= screenHeight) {
                    params.y = screenHeight - floatingView.height
                    velocityY = -velocityY * bounceFactor
                    cuteSquash() 
                } else if (params.y <= 0) {
                    params.y = 0
                    velocityY = -velocityY * bounceFactor
                    cuteSquash()
                }

                // 如果在极慢的情况下（比如漂浮中），为了让基础重力生效，施加微小的动力
                // (但这步已在重力中完成)

                windowManager.updateViewLayout(floatingView, params)
            }
            handler.postDelayed(this, 16)
        }
    }

    // 🌟 果冻级 Q 弹（挤压幅度更大，恢复更快）
    private fun cuteSquash() {
        maidFish.animate().cancel()
        // 根据速度决定形变方向，但简单起见，这里统一做一个夸张的横向压扁
        maidFish.scaleX = 1.25f
        maidFish.scaleY = 0.75f
        maidFish.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
    }

    private fun triggerClickInteraction() {
        maidFish.animate().cancel()
        maidFish.scaleX = 0.8f
        maidFish.scaleY = 1.2f
        maidFish.animate().scaleX(1f).scaleY(1f).setDuration(250).start()

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
        sensorManager.unregisterListener(this)
        if (floatingView.isAttachedToWindow) windowManager.removeView(floatingView)
    }
}
