package com.luke.tootips

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.luke.libtooltip.TooltipView
import com.luke.libtooltip.extensions.showTooltip
import com.luke.libtooltip.extensions.showTooltipAsync
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindView()
    }

    private fun bindView() {
        val btnClick = findViewById<Button>(R.id.btn_click_here)
        val tooltipView = TooltipView.TooltipBuilder()
            .setContent("Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's")
            .setAnchorPosition(TooltipView.TooltipPosition.BOTTOM_CENTER)
            .build(context = this)

        val tvHello = findViewById<TextView>(R.id.tv_hello)

        btnClick.setOnClickListener {
            tvHello.showTooltip(tooltipView)
        }

        btnClick.showTooltip(tooltipView)
//        lifecycleScope.launch {
//            tvHello.showTooltipAsync(tooltipView)
//        }
    }
}