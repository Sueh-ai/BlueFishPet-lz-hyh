package com.example.bluefish //

import android.app.Service
import android.content.Intent
import android.content.res.AssetManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import kotlin.random.Random

class FloatingFishService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var maidFish: ImageView
    private lateinit var tvDialogue: TextView
    private val handler = Handler(Looper.getMainLooper())

    // 物理参数
    private var velocityX = 0f
    private var velocityY = 0f
    private var isDragging = false
    private var gravity = 20f 
    private var bounceFactor = -0.65f 
    private var friction = 0.95f 
    private var screenWidth = 0
    private var screenHeight = 0

    // 素材列表
    private val idleList = mutableListOf<String>()
    private val walkList = mutableListOf<String>()
    private val sleepList = mutableListOf<String>()
    private val playList = mutableListOf<String>()
    private val clickList = mutableListOf<String>()

    // 🌟 台词库：把你想要的 350 条全塞在这个括号里，加在下面就行！
    private val clickLines = listOf(
        "我亲爱的黎子少爷", "今天还爱我", "我好想你", 
        "哎呀，别拽我！", "主人，你在干嘛呀？", "哼，不理你了！"
        // 👇 在这里继续加台词，一行一条，记得后面加英文逗号
        // "少爷今天真帅", "不要再摸啦~"
    )
    private val idleLines = listOf("好无聊呀...", "今天天气真好。", "少爷怎么不理我...")
    private val sleepLines = listOf("呼...呼...", "Zzz...", "梦里也有少爷呢...")

    private var currentState = "IDLE"
    private var lastClickTime = 0L
    private var clickCount = 0

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_fish_layout, null)
        maidFish = floatingView.findViewById(R.id.iv_fish)
        tvDialogue = floatingView.findViewById(R.id.tv_dialogue)

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
                    "WALK" -> { 
                        playRandomAnimation(walkList)
                        velocityX = if (Random.nextBoolean()) 15f else -15f
                    }
                    "PLAY" -> { 
                        playRandomAnimation(playList)
                        velocityY = -30f 
                    }
                    "SLEEP" -> { 
                        playRandomAnimation(sleepList)
                        velocityX = 0f
                    }              }              }
