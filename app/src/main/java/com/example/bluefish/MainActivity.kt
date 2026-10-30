package com.yourname.maidfish // 记得改包名

import android.animation.ObjectAnimator
import android.app.Service
import android.content.Intent
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
import kotlin.random.Random

class FloatingFishService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var maidFish: ImageView

    // 物理引擎参数
    private var velocityX = 0f
    private var velocityY = 0f
    private var isDragging = false
    private val gravity = 2.0f // 重力加速度
    private val bounceFactor = -0.6f // 弹性系数（撞墙反弹损失）
    private val handler = Handler(Looper.getMainLooper())

    // 屏幕尺寸
    private var screenWidth = 0
    private var screenHeight = 0

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_fish_layout, null)
        maidFish = floatingView.findViewById(R.id.iv_fish)

        val displayMetrics = resources.displayMetrics
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth / 2 - 150
            y = 100 // 一开始从天而降
        }

        windowManager.addView(floatingView, params)

        setupTouchListener()
        startPhysicsLoop()
    }

    private fun setupTouchListener() {
        floatingView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX: Int = 0
            private var initialY: Int = 0
            private var initialTouchX: Float = 0f
            private var initialTouchY: Float = 0f
            private var lastTouchX: Float = 0f
            private var lastTouchY: Float = 0f
            private var downTime: Long = 0

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isDragging = true
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        lastTouchX = event.rawX
                        lastTouchY = event.rawY
                        downTime = System.currentTimeMillis()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, params)
                        
                        // 计算拖拽速度，松手时会有惯性
                        velocityX = event.rawX - lastTouchX
                        velocityY = event.rawY - lastTouchY
                        lastTouchX = event.rawX
                        lastTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        isDragging = false
                        val upTime = System.currentTimeMillis()
                        
                        // 如果按下和抬起的时间很短，算作“点击”
                        if (upTime - downTime < 200 && Math.abs(event.rawX - initialTouchX) < 10) {
                            triggerInteraction()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    // 物理循环：每 16 毫秒刷新一次（约60帧）
    private val physicsRunnable = object : Runnable {
        override fun run() {
            if (!isDragging) {
                // 1. 应用重力
                velocityY += gravity
                
                // 2. 更新位置
                params.x += velocityX.toInt()
                params.y += velocityY.toInt()

                // 3. 碰撞检测（左右墙壁）
                if (params.x <= 0) {
                    params.x = 0
                    velocityX = -velocityX * bounceFactor
                } else if (para
