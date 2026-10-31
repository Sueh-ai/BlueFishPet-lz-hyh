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
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bgDrawable
            setPadding(50, 150, 50, 50)
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }

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
        
        val spacer = android.view.View(this)
        rootLayout.addView(spacer, LinearLayout.LayoutParams(1, 0, 1f))

        val tvLove = TextView(this).apply {
            text = "我最最亲爱的黎子少爷，\n这是我为你做的，独属于你的鲸鱼娘，爱你😘"
            textSize = 16f
            setTextColor(Color.parseColor("#FFD700"))
            gravity = android.view.Gravity.CENTER
            setLineSpacing(20f, 1f)
            setPadding(0, 0, 0, 100)
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
