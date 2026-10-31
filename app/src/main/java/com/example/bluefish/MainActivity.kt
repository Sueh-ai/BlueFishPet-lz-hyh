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

        // 深色渐变背景
        val bgDrawable = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#1A1B21"), Color.parseColor("#2D2F3B"))
        )

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bgDrawable
            setPadding(60, 200, 60, 60)
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }

        val tvTitle = TextView(this).apply {
            text = "黎子少爷的专属大肥鱼"
            textSize = 24f
            setTextColor(Color.WHITE)
            setShadowLayer(10f, 0f, 0f, Color.parseColor("#80FFFFFF")) // 发光效果
        }
        layout.addView(tvTitle)

        val tvSubtitle = TextView(this).apply {
            text = "\n设置她的大小，然后召唤到桌面吧！\n"
            textSize = 14f
            setTextColor(Color.parseColor("#AAAAAA"))
        }
        layout.addView(tvSubtitle)

        val tvSizeShow = TextView(this).apply {
            text = "当前大小: 600"
            textSize = 18f
            setTextColor(Color.parseColor("#FFD700")) // 金黄色
            setPadding(0, 30, 0, 30)
        }
        layout.addView(tvSizeShow)

        // 美化滑动条（去掉系统默认背景）
        val seekBar = SeekBar(this).apply {
            max = 1000
            progress = 600
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FFD700"))
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FFD700"))
        }
        layout.addView(seekBar)

        val btnStart = Button(this).apply {
            text = "🚀 召唤大肥鱼"
            textSize = 18f
            setTextColor(Color.BLACK)
            val btnBg = GradientDrawable().apply {
                setColor(Color.parseColor("#FFD700"))
                cornerRadius = 50f // 圆角按钮
            }
            background = btnBg
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = 80
            layoutParams = lp
        }
        layout.addView(btnStart)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (progress > 200) tvSizeShow.text = "当前大小: $progress"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        setContentView(layout)

        btnStart.setOnClickListener {
            val finalSize = if (seekBar.progress > 200) seekBar.progress else 600
            getSharedPreferences("fish_prefs", MODE_PRIVATE).edit()
                .putInt("fish_size", finalSize).apply()

            // 检查悬浮窗权限并启动
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
