package com.example.bluefish

import android.content.Intent
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

        // 纯代码画一个设置界面
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 150, 50, 50)
        }

        val tvTitle = TextView(this).apply {
            text = "黎子少爷，请设置大肥鱼的大小："
            textSize = 18f
        }
        layout.addView(tvTitle)

        val tvSizeShow = TextView(this).apply {
            text = "当前大小: 600"
            textSize = 16f
            setPadding(0, 30, 0, 30)
        }
        layout.addView(tvSizeShow)

        val seekBar = SeekBar(this).apply {
            max = 1000
            progress = 600 // 默认大小
        }
        layout.addView(seekBar)

        val btnStart = Button(this).apply {
            text = "启动我的大肥鱼"
        }
        layout.addView(btnStart)

        // 监听滑动条
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (progress > 200) { // 限制最小 200，不然看不见了
                    tvSizeShow.text = "当前大小: $progress"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        setContentView(layout)

        btnStart.setOnClickListener {
            // 保存大小设置
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
                    finish() // 启动后关闭设置页
                }
            } else {
                startService(Intent(this, FloatingFishService::class.java))
                finish()
            }
        }
    }
}
