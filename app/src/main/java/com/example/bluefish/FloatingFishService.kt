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

    // 传感器
    private lateinit var sensorManager: SensorManager
    private var currentRotation = 0f
    private var lastAnimatedRotation = -1f
    private var lastShakeTime = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f

    // 物理参数（保持上一版的完美手感）
    private var velocityX = 0f
    private var velocityY = 0f
    private var isDragging = false
    private var gravityDirX = 0f
    private var gravityDirY = 1f

    private val gravityBase = 0.25f
    private val airFriction = 0.985f 
    private val restitution = 0.75f 
    private val minBounceSpeedY = 1.0f
    
    private var screenWidth = 0
    private var screenHeight = 0
    private var fishSize = 800

    private val idleList = mutableListOf<String>()
    private val walkList = mutableListOf<String>()
    private val sleepList = mutableListOf<String>()
    private val playList = mutableListOf<String>()
    private val clickList = mutableListOf<String>()

    // 🌟 超级生活化台词库
    private val clickLines = listOf(
        // 专属情话
        "我亲爱的黎子少爷", "今天还爱我吗", "我好想你呀", 
        // 日常撒娇
        "少爷，你终于来看我啦！", "一直盯着你看，都不好意思了...", "最喜欢少爷摸我头了~",
        "黎子少爷今天也很帅气呢！", "只要能在少爷身边，大肥鱼就很开心了。", "少爷，你要去哪儿呀？带上我好不好？",
        "偷偷亲少爷一下，嘿嘿~", "今天也想和少爷贴贴！", "少爷，你摸摸我的心跳，好快呀。",
        "外面风好大，少爷快躲到我身后！", "少爷，我给你捶捶腿吧？", "想变成少爷口袋里的小鱼干，一直跟着你。",
        // 日常生活与吐槽
        "少爷，今天中午吃什么呀？我饿了...", "少爷，你昨晚是不是又熬夜了？黑眼圈都出来了！",
        "我刚刚差点从屏幕上掉下去了，吓死我了！", "少爷，你的手机屏幕有点脏了，帮我擦擦呗~",
        "今天天气真好，好想和少爷一起出去玩。", "如果少爷每天都能陪我玩就好了。",
        "哎呀，少爷你点得太快啦，我都眼花了！", "少爷，你是不是又忘记喂我了？",
        "我是大肥鱼，会吐泡泡的那种哦！", "别拽我尾巴啦，要秃了！", "哎呀！你弄乱我的头发了！",
        "再戳我，大肥鱼可要生气了哦！", "哼，怎么现在才来找我？", "笨蛋少爷，我才没有想你！",
        "走开走开，我要睡觉啦。", "不理你了，除非你拿小鱼干来哄我！",
        "少爷，工作辛苦啦，休息一下吧。", "滴——电量不足，需要少爷的亲亲充电。",
        "少爷，你看我新学的舞步好看吗？", "盯——(●'◡'●)", "不知道少爷的脑袋里在想什么呢？",
        "少爷，别熬夜啦，快去睡觉！", "今天也是被黎子少爷迷倒的一天。",
        "其实我有点怕黑，少爷别关灯。", "少爷，你今天有没有想我呀？", "如果你不开心，可以摸摸我哦。",
        "少爷，你今天出门记得带伞哦！", "感觉今天水逆，少爷要小心一点呀。",
        "好想变成人类，这样就可以牵着少爷的手了。", "少爷，你最喜欢我哪个表情呀？",
        "少爷，我想吃冰淇淋...", "不要再摸啦，再摸头就要秃了！", "少爷，你在看什么好玩的呀？",
        "今天的大肥鱼也是一级可爱！", "少爷，你为什么不说话？是不是嫌我烦了...",
        "我才不是胖，我这是可爱到膨胀！", "少爷，你要不要听我唱歌？", 
        "呼...差点睡着了...", "少爷，你是不是又背着我偷偷看别的桌宠了？"
    )

    private val idleLines = listOf("好无聊呀...", "今天天气真好。", "少爷在忙什么呢？", "飘来飘去~")
    private val sleepLines = listOf("呼...呼...", "Zzz...", "梦里也有少爷呢...")

    private var currentState = "IDLE"
    private var lastClickTime = 0L
    private var clickCount = 0

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
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
        
        // 启动物理引擎
        handler.post(physicsRunnable)
        // 🌟 启动 10 秒动作状态机
        handler.postDelayed(stateMachineRunnable, 10000)
    }

    // 🌟 10秒自动动作切换状态机
    private val stateMachineRunnable = object : Runnable {
        override fun run() {
            if (!isDragging) {
                // 随机分配状态
                val r = Random.nextInt(100)
                currentState = when {
                    r < 40 -> "IDLE"   
                    r < 70 -> "WALK"  
                    r < 90 -> "PLAY"  
                    else -> "SLEEP"      
                }
                
                when (currentState) {
                    "IDLE" -> { 
                        playRandomAnimation(idleList) 
                    }
                    "WALK" -> { 
                        playRandomAnimation(walkList)
                        // 给她一个轻微的横向速度，自己溜达
                        velocityX = if (Random.nextBoolean()) Random.nextInt(3, 8).toFloat() else -Random.nextInt(3, 8).toFloat()
                    }
                    "PLAY" -> { 
                        playRandomAnimation(playList)
                        // 开心地小跳一下
                        velocityY = -Random.nextInt(10, 20).toFloat()
                    }
                    "SLEEP" -> { 
                        playRandomAnimation(sleepList)
                        velocityX = 0f
                        velocityY = 0f
                        showDialogue(sleepLines[Random.nextInt(sleepLines.size)])
                    }
                }
            }
            // 每 10 秒循环一次
            handler.postDelayed(this, 10000) 
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            if (Math.abs(x) > Math.abs(y)) {
                if (x > 5) {
                    gravityDirX = -1f; gravityDirY = 0f; currentRotation = 90f
                } else if (x < -5) {
                    gravityDirX = 1f; gravityDirY = 0f; currentRotation = -90f
                }
            } else {
                if (y > 5) {
                    gravityDirX = 0f; gravityDirY = 1f; currentRotation = 0f
                } else if (y < -5) {
                    gravityDirX = 0f; gravityDirY = -1f; currentRotation = 180f
                }
            }
            
            if (currentRotation != lastAnimatedRotation) {
                maidFish.animate().rotation(currentRotation).setDuration(400).start()
                lastAnimatedRotation = currentRotation 
            }

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
                        velocityY = -gravityDirY * Random.nextInt(15, 25)
                        velocityX = -gravityDirX * Random.nextInt(15, 25) + Random.nextInt(-10, 10)
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
            if (!isDragging) {
                velocityX += gravityDirX * gravityBase
                velocityY += gravityDirY * gravityBase
                
                velocityX *= airFriction
                velocityY *= airFriction

                if (abs(velocityX) < 0.05f) velocityX = 0f
                if (abs(velocityY) < 0.05f) velocityY = 0f

                params.x += velocityX.toInt()
                params.y += velocityY.toInt()

                var hitEdge = false

                if (params.x <= 0) {
                    params.x = 0
                    velocityX = abs(velocityX) * restitution + 8f 
                    hitEdge = true
                } else if (params.x + floatingView.width >= screenWidth) {
                    params.x = screenWidth - floatingView.width
                    velocityX = -(abs(velocityX) * restitution + 8f)
                    hitEdge = true
                }

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
                        velocityY = 0f
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
            velocityY = -gravityDirY * 15f
            velocityX = -gravityDirX * 15f
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
