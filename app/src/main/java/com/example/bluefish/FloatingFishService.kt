package com.example.bluefish

import android.app.Service
import android.content.Intent
import android.content.res.AssetManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
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
import kotlin.random.Random

class FloatingFishService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: LinearLayout
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var maidFish: ImageView
    private lateinit var tvDialogue: TextView
    private val handler = Handler(Looper.getMainLooper())

    private var velocityX = 0f
    private var velocityY = 0f
    private var isDragging = false
    private var gravity = 20f
    private var bounceFactor = -0.8f // 弹力
    private var friction = 0.96f // 摩擦
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
        "不理你了，除非你拿小鱼干来哄我！", "大肥鱼也是有脾气的！", "我是大肥鱼，会吐泡泡的那种哦！",
        "今天的网速好像有点慢呢。", "少爷，今天中午吃什么呀？", "救命，我卡在屏幕边缘了！"
    )
    private val idleLines = listOf("好无聊呀...", "今天天气真好。")
    private val sleepLines = listOf("呼...呼...", "Zzz...")

    private var currentState = "IDLE"
    private var lastClickTime = 0L
    private var clickCount = 0

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // 读取你在 MainActivity 里设置的大小
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
            lp.bottomMargin = 30
            layoutParams = lp
        }
        floatingView.addView(tvDialogue)

        maidFish = ImageView(this).apply {
            // 应用你设置的大小
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
        handler.postDelayed(stateMachineRunnable, 10000) 
    }

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
                    in 0..40 -> "IDLE"   
                    in 41..70 -> "WALK"  
                    in 71..85 -> "PLAY"  
                    else -> "SLEEP"      
                }
                when (currentState) {
                    "IDLE" -> { playRandomAnimation(idleList); velocityX = 0f }
                    "WALK" -> { playRandomAnimation(walkList); velocityX = if (Random.nextBoolean()) 15f else -15f }
                    "PLAY" -> { playRandomAnimation(playList); velocityY = -30f }
                    "SLEEP" -> { playRandomAnimation(sleepList); velocityX = 0f; showDialogue(sleepLines[Random.nextInt(sleepLines.size)]) }
                }
            }
            handler.postDelayed(this, 10000) 
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
                        
                        // 抓起时变大一点
                        maidFish.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, params)
                        
                        // 根据拖拽速度产生倾斜
                        val deltaX = event.rawX - lastTouchX
                        maidFish.rotation = deltaX * 1.5f // 晃动倾斜
                        
                        velocityX = deltaX * 1.5f
                        velocityY = (event.rawY - lastTouchY) * 1.5f
                        lastTouchX = event.rawX; lastTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        isDragging = false
                        // 松手回正
                        maidFish.animate().scaleX(1f).scaleY(1f).rotation(0f).setDuration(150).start()
                        
                        if (System.currentTimeMillis() - downTime < 200 && abs(event.rawX - initialTouchX) < 10) {
                            triggerClickInteraction()
                        } else {
                            velocityX = event.rawX - lastTouchX
                            velocityY = event.rawY - lastTouchY
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
                velocityY += gravity / 10f
                params.x += velocityX.toInt()
                params.y += velocityY.toInt()

                // 左右边缘
                if (params.x <= 0) {
                    params.x = 0
                    velocityX = -velocityX * bounceFactor
                    squash() // 撞墙挤压
                } else if (params.x + floatingView.width >= screenWidth) {
                    params.x = screenWidth - floatingView.width
                    velocityX = -velocityX * bounceFactor
                    squash() // 撞墙挤压
                }

                // 上下边缘
                if (params.y + floatingView.height >= screenHeight) {
                    params.y = screenHeight - floatingView.height
                    velocityY = -velocityY * bounceFactor
                    
                    if (abs(velocityY) > 30) squash() // 砸在地上重重挤压
                    
                    if (abs(velocityY) < 20) velocityY = 0f 
                    velocityX *= friction 
                } else if (params.y <= 0) {
                    params.y = 0
                    velocityY = -velocityY * bounceFactor
                }
                windowManager.updateViewLayout(floatingView, params)
            }
            handler.postDelayed(this, 16)
        }
    }

    // 🌟 物理挤压Q弹（压扁拉长）
    private fun squash() {
        maidFish.animate().cancel()
        maidFish.scaleX = 1.3f
        maidFish.scaleY = 0.7f
        maidFish.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
    }

    private fun triggerClickInteraction() {
        // 点击Q弹
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
            velocityY = -40f
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
        if (floatingView.isAttachedToWindow) windowManager.removeView(floatingView)
    }
}
