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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

class FloatingFishService : Service(), SensorEventListener {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: FrameLayout
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var maidFish: ImageView
    private lateinit var tvDialogue: TextView
    private val handler = Handler(Looper.getMainLooper())

    // 🌟 传感器与旋转防抖
    private lateinit var sensorManager: SensorManager
    private var currentRotation = 0f
    private var lastAnimatedRotation = -1f 
    private var lastShakeTime = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f

    // 物理参数
    private var velocityX = 0f
    private var velocityY = 0f
    private var isDragging = false
    
    private var gravityDirX = 0f
    private var gravityDirY = 1f
    
    private val gravityBase = 0.15f 
    private val airFriction = 0.98f 
    private val restitution = 0.6f  
    private val minBounceSpeedY = 1.5f 
    
    private var screenWidth = 0
    private var screenHeight = 0
    private var fishSize = 800

    private val idleList = mutableListOf<String>()
    private val walkList = mutableListOf<String>()
    private val sleepList = mutableListOf<String>()
    private val playList = mutableListOf<String>()
    private val clickList = mutableListOf<String>()

    private val clickLines = listOf(
        "我亲爱的黎子少爷", "今天还爱我", "我好想你", 
        "少爷，你终于来看我啦！", "一直盯着你看，都不好意思了...", "最喜欢少爷摸我头了~",
        "别拽我尾巴啦，要秃了！", "哼，怎么现在才来找我？", "笨蛋少爷，我才没有想你！"
    )

    private var lastClickTime = 0L
    private var clickCount = 0

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME)

        val prefs = getSharedPreferences("fish_prefs", MODE_PRIVATE)
        fishSize = prefs.getInt("fish_size", 800)

        floatingView = FrameLayout(this)

        maidFish = ImageView(this).apply {
            val lp = FrameLayout.LayoutParams(fishSize, fishSize)
            layoutParams = lp
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        floatingView.addView(maidFish)

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
        tvLp.topMargin = -50
        tvDialogue.layoutParams = tvLp
        floatingView.addView(tvDialogue)

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
            y = screenHeight / 2 - (fishSize / 2)
         }

        windowManager.addView(floatingView, params)
        loadAssetsFiles()
        playRandomAnimation(idleList)
        setupTouchListener()
        
        handler.post(physicsRunnable)
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

                if (speed > 12f && !isDragging) { 
                    lastShakeTime = currentTime
                    handler.post {
                        playRandomAnimation(playList)
                        velocityY = -gravityDirY * Random.nextInt(10, 20)
                        velocityX = -gravityDirX * Random.nextInt(10, 20) + Random.nextInt(-10, 10)
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
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
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

                // 左右碰撞（保底弹力）
                if (params.x <= 0) {
                    params.x = 0
                    velocityX = abs(velocityX) * restitution + 4f 
                    hitEdge = true
                } else if (params.x + fishSize >= screenWidth) {
                    params.x = screenWidth - fishSize
                    velocityX = -(abs(velocityX) * restitution + 4f)
                    hitEdge = true
                }

                // 上下碰撞（阻尼衰减）
                if (params.y <= 0) {
                    params.y = 0
                    if (abs(velocityY) > minBounceSpeedY) {
                        velocityY = abs(velocityY) * restitution
                        hitEdge = true
                    } else {
                        velocityY = 0f
                    }
                } else if (params.y + fishSize >= screenHeight) {
                    params.y = screenHeight - fishSize
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

    // 🌟 极致Q弹：大幅度形变 + 果冻颤抖
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
