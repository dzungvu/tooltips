package com.luke.tootips

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.luke.libtooltip.TooltipView
import com.luke.libtooltip.extensions.showTooltip

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindView()
    }

    private fun bindView() {
        val btnClick = findViewById<Button>(R.id.btn_click_here)
        val btnBuyPackage = findViewById<Button>(R.id.btn_buy_package)
        val btnSetting = findViewById<Button>(R.id.btn_setting)
        val btnSportInteractive = findViewById<Button>(R.id.btn_sport_interactive)

        val tooltipView = buildTooltipView("Chọn nội dung theo từng thể loại cụ thể")
        val tooltipBuyPackageView = buildTooltipView("Chọn gói dịch vụ phổ biến bạn muốn mua")
        val tooltipSportInteractive = buildTooltipView("Khám phá dữ liệu thời gian thực của trận đấu")
        val tooltipSetting = buildTooltipView("Lụa chọn chất lượng xem video")
//        val tooltipSetting = buildTooltipView("Text content")

        val tvHello = findViewById<TextView>(R.id.tv_hello)


        btnClick.showTooltip(tooltipView)
        btnBuyPackage.showTooltip(tooltipBuyPackageView)
        btnSportInteractive.showTooltip(tooltipSportInteractive)
        btnSetting.showTooltip(tooltipSetting)
//        lifecycleScope.launch {
//            tvHello.showTooltipAsync(tooltipView)
//        }
    }

    private fun buildTooltipView(content: String, tooltipPosition: TooltipView.TooltipPosition = TooltipView.TooltipPosition.BOTTOM): TooltipView {
        return TooltipView.TooltipBuilder()
            .setContent(content)
            .setAnchorPosition(tooltipPosition)
            .setDismissStrategy(TooltipView.DismissStrategy.DISMISS_WHEN_TOUCH_INSIDE)
            .build(context = this)
    }
}