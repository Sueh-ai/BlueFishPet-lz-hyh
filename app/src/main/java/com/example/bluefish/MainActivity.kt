package com.example.bluefish

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bgDrawable = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#121318"), Color.parseColor("#1E2029"))
        )
        // 根布局加上 weight，让上下分布均匀
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bgDrawable
            setPadding(50, 150, 50, 50)
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }

        // 1. 上面的控制卡片
        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(60, 80, 60, 80)
            val cardBg = GradientDrawable().apply {
                setColor(Color.parseColor("#2A2D3A"))
                cornerRadius = 60f
                setStroke(2, Color.parseColor("#FFD700"))
            }
            background = cardBg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val tvTitle = TextView(this).apply {
            text = "专属大肥鱼"
            textSize = 26f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        cardLayout.addView(tvTitle)

        val tvSubtitle = TextView(this).apply {
            text = "轻飘飘的弹簧果冻"
            textSize = 14f
            setTextColor(Color.parseColor("#FFD700"))
            setPadding(0, 10, 0, 40)
        }
        cardLayout.addView(tvSubtitle)

        val tvSizeShow = TextView(this).apply {
            text = "体型大小：600"
            textSize = 16f
            setTextColor(Color.parseColor("#CCCCCC"))
            setPadding(0, 20, 0, 20)
        }
        cardLayout.addView(tvSizeShow)

        val seekBar = SeekBar(this).apply {
            max = 800
            progress = 600
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FFD700"))
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FFD700"))
        }
        cardLayout.addView(seekBar)

        val btnStart = Button(this).apply {
            text = "召 唤 大 肥 鱼"
            textSize = 18f
            setTextColor(Color.parseColor("#1A1B21"))
            val btnBg = GradientDrawable().apply {
                setColor(Color.parseColor("#FFD700"))
                cornerRadius = 50f 
            }
            background = btnBg
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                140
            )
            lp.topMargin = 60
            layoutParams = lp
        }
        cardLayout.addView(btnStart)

        rootLayout.addView(cardLayout)
        
        // 添加一个弹簧占位，把情话推到最下面
        val spacer = android.view.View(this)
        rootLayout.addView(spacer, LinearLayout.LayoutParams(1, 0, 1f))

        // 2. 底部的情话
        val tvLove = TextView(this).apply {
            text = "我最最亲爱的黎子少爷，\n这是我为你做的，独属于你的鲸鱼娘，爱你😘"
            textSize = 16f
            setTextColor(Color.parseColor("#FFD700"))
            gravity = android.view.Gravity.CENTER
            setLineSpacing(20f, 1f)
            setPadding(0, 0, 0, 100) // 底部留白，避开手机虚拟按键
        }
        rootLayout.addView(tvLove)
        
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (progress > 200) tvSizeShow.text = "体型大小：$progress"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        setContentView(rootLayout)

        btnStart.setOnClickListener {
            val finalSize = if (seekBar.progress > 200) seekBar.progress else 600
            getSharedPreferences("fish_prefs", MODE_PRIVATE).edit()
                .putInt("fish_size", finalSize).apply()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } else {
                    startService(Intent(this, FloatingFishService::class.java))
                    finish()
                }
            } else {
                startService(Intent(this, FloatingFishService::class.java))
                finish()
            }
        }
    }
}
```

---

🪶 第二步：注入“超轻弹簧物理” (FloatingFishService.kt)

打开 FloatingFishService.kt，全选删光，贴入这段代码。这是终极调优版：

```kotlin
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

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var sensorX = 0f 
    private var sensorY = 0f 
    
    private var lastShakeTime = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f

    // 🌟 终极轻盈物理参数
    private var velocityX = 0f
    private var velocityY = 0f
    private var isDragging = false
    private var gravityBase = 0.03f // 重力降到极低，几乎像在太空
    private var bounceFactor = -0.85f // 弹簧级弹力
    private var friction = 0.992f // 空气阻力极小，飘得很远
    private var screenWidth = 0
    private var screenHeight = 0
    private var bottomSafeZone = 150f // 底部安全区，不让她掉到底

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
            y = 300 // 起始位置偏高一点
        }

        windowManager.addView(floatingView, params)
        loadAssetsFiles()
        playRandomAnimation(idleList)
        setupTouchListener()
        
        handler.post(physicsRunnable)
        handler.postDelayed(stateMachineRunnable, 5000)
        handler.postDelayed(autoChatRunnable, 15000) 
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // 加大倾斜感知，像水母受水流影响
            sensorX = -x * 2.5f 
            sensorY = y * 2.5f

            val currentTime = System.currentTimeMillis()
            if ((currentTime - lastShakeTime) > 500) {
                val deltaX = abs(x - lastX)
                val deltaY = abs(y - lastY)
                val deltaZ = abs(z - lastZ)
                val speed = sqrt((deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ).toDouble()).toFloat()

                if (speed > 12f && !isDragging) {
                    lastShakeTime = currentTime
                    handler.post {
                        playRandomAnimation(playList)
                        showDialogue(shakeLines[Random.nextInt(shakeLines.size)])
                        // 摇晃时给她向上的力，同时随机左右
                        velocityY = -Random.nextInt(15, 30).toFloat()
                        velocityX = Random.nextInt(-25, 25).toFloat()
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
                        velocityX += if (Random.nextBoolean()) Random.nextInt(3, 8).toFloat() else -Random.nextInt(3, 8).toFloat()
                    }
                    "PLAY" -> { 
                        playRandomAnimation(playList)
                        velocityY = -Random.nextInt(10, 20).toFloat()
                        velocityX = Random.nextInt(-15, 15).toFloat()
                    }
                    "SLEEP" -> { 
                        playRandomAnimation(sleepList)
                        velocityX = 0f
                        velocityY = 0f
                        showDialogue(sleepLines[Random.nextInt(sleepLines.size)])
                    }
                }
            }
            handler.postDelayed(this, 6000) 
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

    // 🌟 完美反重力弹簧循环
    private val physicsRunnable = object : Runnable {
        override fun run() {
            if (!isDragging) {
                // 加上传感器倾斜的力，加上几乎微不可查的重力
                velocityX += sensorX * 0.5f
                velocityY += sensorY * 0.5f + gravityBase
                
                // 空气阻力
                velocityX *= friction
                velocityY *= friction

                // 限速
                val maxSpeed = 25f 
                if (velocityX > maxSpeed) velocityX = maxSpeed
                if (velocityX < -maxSpeed) velocityX = -maxSpeed
                if (velocityY > maxSpeed) velocityY = maxSpeed
                if (velocityY < -maxSpeed) velocityY = -maxSpeed

                params.x += velocityX.toInt()
                params.y += velocityY.toInt()

                // 左右碰壁
                if (params.x <= 0) {
                    params.x = 0
                    velocityX = -velocityX * bounceFactor
                    jellySquash(1.15f, 0.85f) // 碰到左右墙：横向压扁
                } else if (params.x + floatingView.width >= screenWidth) {
                    params.x = screenWidth - floatingView.width
                    velocityX = -velocityX * bounceFactor
                    jellySquash(1.15f, 0.85f)
                }

                // 🌟 底部安全区（绝不落地，直接弹飞）
                if (params.y + floatingView.height >= screenHeight - bottomSafeZone) {
                    params.y = (screenHeight - bottomSafeZone - floatingView.height).toInt()
                    // 弹簧反弹：如果还在往下掉，给她一个向上的大冲力
                    if (velocityY > 0) {
                        velocityY = -velocityY * bounceFactor
                        // 如果速度太小，给她一个保底向上浮力
                        if (velocityY > -5f) velocityY = -15f 
                        jellySquash(0.85f, 1.15f) // 纵向拉伸（像弹簧一样弹起）
                    }
                } else if (params.y <= 0) {
                    params.y = 0
                    velocityY = -velocityY * bounceFactor
                    jellySquash(1.15f, 0.85f)
                }

                windowManager.updateViewLayout(floatingView, params)
            }
            handler.postDelayed(this, 16)
        }
    }

    // 🌟 终极弹簧恢复动画（解决扁掉的问题）
    private fun jellySquash(scaleX: Float, scaleY: Float) {
        maidFish.animate().cancel()
        // 每次形变前，先确保她恢复到正常比例，避免多次点击叠加导致卡死变扁
        maidFish.scaleX = 1f
        maidFish.scaleY = 1f
        
        // 瞬间形变
        maidFish.scaleX = scaleX
        maidFish.scaleY = scaleY
        
        // 使用弹簧插值器回弹，更生动
        maidFish.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setInterpolator(OvershootInterpolator(2f)) // 弹簧回弹感
            .setDuration(250)
            .start()
    }

    private fun triggerClickInteraction() {
        jellySquash(0.85f, 1.15f) // 点击也是弹簧果冻

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
            velocityY = -15f // 轻轻一蹦
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
           
