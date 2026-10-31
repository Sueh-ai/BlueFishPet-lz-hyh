package com.example.bluefish

import android.app.Service
import android.content.Intent
import android.content.res.AssetManager
import android.graphics.Color
import android.graphics.PixelFormat
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
    private var bounceFactor = -0.65f
    private var friction = 0.95f
    private var screenWidth = 0
    private var screenHeight = 0

    private val idleList = mutableListOf<String>()
    private val walkList = mutableListOf<String>()
    private val sleepList = mutableListOf<String>()
    private val playList = mutableListOf<String>()
    private val clickList = mutableListOf<String>()

    // 🌟 互动台词库（你想加多少就往下加，注意标点）
    private val clickLines = listOf(
        // 专属核心台词
        "我亲爱的黎子少爷",
        "今天还爱我",
        "我好想你",
        // 撒娇/深情
        "少爷，你终于来看我啦！",
        "一直盯着你看，都不好意思了...",
        "最喜欢少爷摸我头了~",
        "黎子少爷今天也很帅气呢！",
        "只要能在少爷身边，大肥鱼就很开心了。",
        "少爷，你要去哪儿呀？带上我好不好？",
        "偷偷亲少爷一下，嘿嘿~",
        "今天也想和少爷贴贴！",
        "少爷，你摸摸我的心跳，好快呀。",
        "外面风好大，少爷快躲到我身后！",
        // 傲娇/小脾气
        "哼，怎么现在才来找我？",
        "别拽我尾巴啦，要秃了！",
        "哎呀！你弄乱我的头发了！",
        "少爷，你点得我头好晕呀...",
        "再戳我，大肥鱼可要生气了哦！",
        "笨蛋少爷，我才没有想你！",
        "走开走开，我要睡觉啦。",
        "少爷，你是不是又忘记喂我了？",
        "不理你了，除非你拿小鱼干来哄我！",
        "大肥鱼也是有脾气的！",
        // 日常/搞笑
        "我是大肥鱼，会吐泡泡的那种哦！",
        "今天的网速好像有点慢呢。",
        "少爷，今天中午吃什么呀？",
        "救命，我卡在屏幕边缘了！",
        "如果少爷每天都能陪我玩就好了。",
        "我是不是全宇宙最可爱的女仆？",
        "黎子少爷，工作辛苦啦，休息一下吧。",
        "滴——电量不足，需要少爷的亲亲充电。",
        "我要在屏幕上跑圈圈啦！",
        "今天也是努力学习做女仆的一天！",
        "少爷，你看我新学的舞步好看吗？",
        "盯——(●'◡'●)",
        "不知道少爷的脑袋里在想什么呢？",
        "如果我不听话，少爷会赶我走吗？",
        "虽然我胖胖的，但是我跑得快呀！",
        "少爷，别熬夜啦，快去睡觉！",
        "我最大的梦想，就是一直陪在少爷身边。",
        "今天也是被黎子少爷迷倒的一天。",
        "偷偷告诉你，其实我是一条美人鱼~",
        "少爷少爷，快看！那里有只小猫！",
        "我刚刚是不是很没形象地摔倒了？",
        "少爷，你要是再不理我，我就哭给你看！",
        "大肥鱼想睡觉了，要抱抱才肯睡...",
        "其实我有点怕黑，少爷别关灯。",
        "少爷，我今天表现得好不好呀？"
    )

    private val idleLines = listOf("好无聊呀...", "今天天气真好。", "少爷怎么不理我...")
    private val sleepLines = listOf("呼...呼...", "Zzz...", "梦里也有少爷呢...")

    private var currentState = "IDLE"
    private var lastClickTime = 0L
    private var clickCount = 0

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // 纯代码构建 UI
        floatingView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        tvDialogue = TextView(this).apply {
            setBackgroundColor(Color.parseColor("#CC000000"))
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(20, 10, 20, 10)
            visibility = View.GONE
        }
        floatingView.addView(tvDialogue)

        maidFish = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(350, 350)
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
            x = screenWidth / 2 - 150
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
                        tvDialogue.visibility = View.GONE
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, params)
                        velocityX = (event.rawX - lastTouchX) * 1.5f
                        velocityY = (event.rawY - lastTouchY) * 1.5f
                        lastTouchX = event.rawX; lastTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        isDragging = false
                        if (System.currentTimeMillis() - downTime < 200 && Math.abs(event.rawX - initialTouchX) < 10) {
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

                if (params.x <= 0) {
                    params.x = 0
                    velocityX = -velocityX * bounceFactor
                } else if (params.x + floatingView.width >= screenWidth) {
                    params.x = screenWidth - floatingView.width
                    velocityX = -velocityX * bounceFactor
                }

                if (params.y + floatingView.height >= screenHeight) {
                    params.y = screenHeight - floatingView.height
                    velocityY = -velocityY * bounceFactor
                    if (Math.abs(velocityY) < 20) velocityY = 0f 
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

    private fun triggerClickInteraction() {
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
        tvDialogue.visibility = View.VISIBLE
        handler.removeCallbacks(hideDialogueRunnable)
        handler.postDelayed(hideDialogueRunnable, 3500)
    }

    private val hideDialogueRunnable = Runnable { tvDialogue.visibility = View.GONE }

    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (floatingView.isAttachedToWindow) windowManager.removeView(floatingView)
    }
}
