package com.example.bluefish

import android.annotation.SuppressLint
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
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import kotlin.math.abs
import kotlin.random.Random

class FloatingFishService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: FrameLayout
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var maidFish: ImageView
    private lateinit var tvDialogue: TextView
    private lateinit var menuLayout: LinearLayout // 长按菜单
    private val handler = Handler(Looper.getMainLooper())

    // 物理参数
    private var velocityX = 0f
    private var velocityY = 0f
    private var isDragging = false
    private var isMenuOpen = false
    private var isHiding = false
    
    private val bounceFactor = -0.7f // 抛物线反弹
    private val friction = 0.96f // 地面摩擦
    
    private var screenWidth = 0
    private var screenHeight = 0
    private var fishSize = 600

    // 拖拽跟手参数
    private var targetX = 0
    private var targetY = 0

    // 贴边隐藏计时器
    private var lastInteractionTime = 0L
    private val hideDelay = 12000L // 12秒无操作隐藏

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
        "别拽我尾巴啦，要秃了！", "哼，怎么现在才来找我？", "笨蛋少爷，我才没有想你！"
    )
    private val idleLines = listOf("好无聊呀...", "今天天气真好。", "少爷在忙什么呢？", "飘来飘去~")

    private var currentState = "IDLE"
    private var lastClickTime = 0L
    private var clickCount = 0

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        val prefs = getSharedPreferences("fish_prefs", MODE_PRIVATE)
        fishSize = prefs.getInt("fish_size", 600)

        // 🌟 核心：使用 FrameLayout 固定大小，杜绝抽搐
        floatingView = FrameLayout(this)

        maidFish = ImageView(this).apply {
            val lp = FrameLayout.LayoutParams(fishSize, fishSize)
            layoutParams = lp
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        floatingView.addView(maidFish)

        // 气泡使用绝对定位，浮在头顶，不改变布局尺寸
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
        }
        val tvLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        tvLp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        tvLp.topMargin = -60 // 浮在头顶
        tvDialogue.layoutParams = tvLp
        floatingView.addView(tvDialogue)

        // 长按菜单（默认隐藏）
        menuLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#EE1A1B21"))
            setPadding(20, 20, 20, 20)
            visibility = View.GONE
            // 简单的菜单按钮，为了代码简洁，直接加文字
            addView(createMenuBtn("待机", "IDLE"))
            addView(createMenuBtn("散步", "WALK"))
            addView(createMenuBtn("玩耍", "PLAY"))
            addView(createMenuBtn("睡觉", "SLEEP"))
        }
        val menuLp = FrameLayout.LayoutParams(
            300,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        menuLp.gravity = Gravity.CENTER
        menuLayout.layoutParams = menuLp
        floatingView.addView(menuLayout)

        val displayMetrics = resources.displayMetrics
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels

        params = WindowManager.LayoutParams(
            fishSize, fishSize,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth / 2 - (fishSize / 2)
            y = screenHeight / 2 - (fishSize / 2) // 从屏幕正中间出来，不玩自由落体
        }

        windowManager.addView(floatingView, params)
        loadAssetsFiles()
        playRandomAnimation(idleList)
        setupTouchListener()
        
        handler.post(physicsRunnable)
        handler.postDelayed(stateMachineRunnable, 3000)
        handler.postDelayed(hideRunnable, hideDelay)
    }

    private fun createMenuBtn(text: String, state: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(20, 20, 20, 20)
            setOnClickListener {
                menuLayout.visibility = View.GONE
                isMenuOpen = false
                triggerState(state)
                lastInteractionTime = System.currentTimeMillis() // 重置交互时间
            }
        }
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

    // 状态机
    private val stateMachineRunnable = object : Runnable {
        override fun run() {
            if (!isDragging && !isMenuOpen && !isHiding) {
                // 随机状态
                val r = Random.nextInt(100)
                val state = when {
                    r < 40 -> "IDLE"
                    r < 80 -> "WALK"
                    else -> "PLAY"
                }
                triggerState(state)
            }
            handler.postDelayed(this, 6000) 
        }
    }

    private fun triggerState(state: String) {
        currentState = state
        when (state) {
            "IDLE" -> { 
                playRandomAnimation(idleList)
                velocityX = 0f // 停下
            }
            "WALK" -> { 
                playRandomAnimation(walkList)
                // 🌟 横向漫游：给定固定速度
                velocityX = if (Random.nextBoolean()) 6f else -6f
            }
            "PLAY" -> { 
                playRandomAnimation(playList)
                velocityY = -15f // 抛物线跳跃
                velocityX = Random.nextInt(-10, 10).toFloat()
            }
            "SLEEP" -> { 
                playRandomAnimation(sleepList)
                velocityX = 0f
            }
        }
    }

    // 自动自言自语
    private val autoChatRunnable = object : Runnable {
        override fun run() {
            if (!isDragging && tvDialogue.visibility != View.VISIBLE && !isHiding && !isMenuOpen) {
                showDialogue(idleLines[Random.nextInt(idleLines.size)])
            }
            handler.postDelayed(this, Random.nextLong(15000, 25000)) 
        }
    }

    // 🌟 贴边躲藏逻辑
    private val hideRunnable = object : Runnable {
        override fun run() {
            if (System.currentTimeMillis() - lastInteractionTime >= hideDelay && !isDragging && !isMenuOpen) {
                if (params.x <= 20) {
                    // 贴左边隐藏
                    maidFish.translationX = -fishSize / 2f
                    isHiding = true
                } else if (params.x + fishSize >= screenWidth - 20) {
                    // 贴右边隐藏
                    maidFish.translationX = fishSize / 2f
                    isHiding = true
                }
            }
            handler.postDelayed(this, 1000) 
        }
    }

    private fun reveal() {
        if (isHiding) {
            maidFish.animate().translationX(0f).setDuration(200).start()
            isHiding = false
        }
        lastInteractionTime = System.currentTimeMillis()
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
            private var longPressTriggered = false
            
            // 长按检测
            private val longPressRunnable = Runnable {
                longPressTriggered = true
                isMenuOpen = true
                menuLayout.visibility = View.VISIBLE
                hideDialogue()
            }

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isDragging = true
                        downTime = System.currentTimeMillis()
                        longPressTriggered = false
                        reveal() // 露出来
                        hideDialogue()
                        
                        initialX = params.x; initialY = params.y
                        initialTouchX = event.rawX; initialTouchY = event.rawY
                        lastTouchX = event.rawX; lastTouchY = event.rawY
                        
                        // 启动长按计时
                        handler.postDelayed(longPressRunnable, 600)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        
                        // 如果移动距离过大，取消长按
                        if (abs(dx) > 20 || abs(dy) > 20) {
                            handler.removeCallbacks(longPressRunnable)
                        }
                        
                        if (!isMenuOpen) {
                            // 🌟 过阻尼弹簧跟手
                            targetX = (initialX + dx).toInt()
                            targetY = (initialY + dy).toInt()
                            
                            velocityX = (event.rawX - lastTouchX) * 1.5f
                            velocityY = (event.rawY - lastTouchY) * 1.5f
                            lastTouchX = event.rawX; lastTouchY = event.rawY
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        isDragging = false
                        handler.removeCallbacks(longPressRunnable)
                        
                        if (!longPressTriggered) {
                            if (System.currentTimeMillis() - downTime < 200 && abs(event.rawX - initialTouchX) < 10) {
                                triggerClickInteraction()
                            } else {
                                // 甩飞惯性
                                velocityX = (event.rawX - lastTouchX) * 2.5f
                                velocityY = (event.rawY - lastTouchY) * 2.5f
                            }
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    // 🌟 物理引擎（无重力，弹簧跟手，抛物线，撞墙掉头）
    private val physicsRunnable = object : Runnable {
        override fun run() {
            if (isDragging && !isMenuOpen) {
                // 弹簧插值跟手 (过阻尼效果)
                params.x += ((targetX - params.x) * 0.3f).toInt()
                params.y += ((targetY - params.y) * 0.3f).toInt()
                windowManager.updateViewLayout(floatingView, params)
            } else if (!isMenuOpen) {
                // 不拖拽时，只有横向移动的惯性，没有往下掉的自由落体
                params.x += velocityX.toInt()
                params.y += velocityY.toInt()
                
                velocityX *= friction
                
                // 如果是在 PLAY 状态，给一点微小的下落
                if (currentState == "PLAY") {
                    velocityY += 1.5f // 轻微重力
                } else {
                    velocityY *= 0.9f // 悬停在空中
                }

                // 🌟 碰到左右边缘：掉头反弹
                if (params.x <= 0) {
                    params.x = 0
                    if (currentState == "WALK") {
                        velocityX = abs(velocityX) // 掉头往右走
                    } else {
                        velocityX = -velocityX * bounceFactor
                    }
                    if(!isHiding) jellySquash(1.15f, 0.85f)
                } else if (params.x + fishSize >= screenWidth) {
                    params.x = screenWidth - fishSize
                    if (currentState == "WALK") {
                        velocityX = -abs(velocityX) // 掉头往左走
                    } else {
                        velocityX = -velocityX * bounceFactor
                    }
                    if(!isHiding) jellySquash(1.15f, 0.85f)
                }

                // 上下边缘
                if (params.y <= 0) {
                    params.y = 0
                    velocityY = -velocityY * bounceFactor
                    if(!isHiding) jellySquash(1.15f, 0.85f)
                } else if (params.y + fishSize >= screenHeight) {
                    params.y = screenHeight - fishSize
                    velocityY = -velocityY * bounceFactor
                    if(!isHiding) jellySquash(0.85f, 1.15f)
                }

                windowManager.updateViewLayout(floatingView, params)
            }
            handler.postDelayed(this, 16)
        }
    }

    private fun jellySquash(sx: Float, sy: Float) {
        maidFish.animate().cancel()
        maidFish.scaleX = sx
        maidFish.scaleY = sy
        maidFish.animate().scaleX(1f).scaleY(1f).setInterpolator(OvershootInterpolator(2f)).setDuration(200).start()
    }

    private fun triggerClickInteraction() {
        jellySquash(0.85f, 1.15f)

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
        if (floatingView.isAttachedToWindow) windowManager.removeView(floatingView)
    }
}
