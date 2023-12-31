package com.luke.tootips

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
        val btnBuyPackage = findViewById<Button>(R.id.btn_buy_package)
        val btnSetting = findViewById<Button>(R.id.btn_setting)
        val btnSportInteractive = findViewById<Button>(R.id.btn_sport_interactive)

        val tooltipView = buildTooltipView("Lorem Ipsum is simply dummy text of the print and typesetting")
        val tooltipBuyPackageView = buildTooltipView("It is a long established fact")
        val tooltipSportInteractive = buildTooltipView("Contrary to popular belief, Lorem Ipsum is not simply random text. It has roots in a piece of classical Latin ant from 45 BC, making it over 2000 years old")
        val tooltipSetting = buildTooltipView("Where can I get some?", TooltipView.TooltipPosition.TOP)
        val tooltipHello = buildTooltipView("Where to hello?", TooltipView.TooltipPosition.TOP)
//        val tooltipSetting = buildTooltipView("Text content")

        val tvHello = findViewById<TextView>(R.id.tv_hello)


//        btnClick.showTooltip(tooltipView)
//        btnBuyPackage.showTooltip(tooltipBuyPackageView)
        btnSportInteractive.showTooltip(tooltipSportInteractive)
//        btnSetting.showTooltip(tooltipSetting)

        btnBuyPackage.setOnClickListener {
            lifecycleScope.launch {
                tvHello.showTooltipAsync(tooltipHello)
            }
        }

        btnSportInteractive.setOnClickListener {
            showDefaultAlertDialog()
        }
        btnSetting.setOnClickListener {
            btnSportInteractive.visibility = if (btnSportInteractive.visibility == TextView.VISIBLE) TextView.GONE else TextView.VISIBLE
        }
//        lifecycleScope.launch {
//            tvHello.showTooltipAsync(tooltipView)
//        }
    }

    private fun buildTooltipView(content: String, tooltipPosition: TooltipView.TooltipPosition = TooltipView.TooltipPosition.BOTTOM): TooltipView {
        return TooltipView.TooltipBuilder()
            .setContent(content)
            .setAnchorPosition(tooltipPosition)
//            .setContentLayoutId(R.layout.custom_tooltip_content)
//            .setArrowResId(R.drawable.arrow)
            .setTooltipDismissListener(TooltipView.TooltipDismissListener {
                Toast.makeText(this, "Tooltip dismissed", Toast.LENGTH_SHORT).show()
            })
//            .setBackgroundColorRes(android.R.color.holo_green_light)
//            .setTextColorRes(android.R.color.holo_red_light)
            .setDismissStrategy(TooltipView.DismissStrategy.DISMISS_WHEN_TOUCH_INSIDE)
            .build(context = this)
    }

    private fun showDefaultAlertDialog() {
        val alertDialogBuilder = AlertDialog.Builder(this)
        alertDialogBuilder.setTitle("Alert Dialog Title")
        alertDialogBuilder.setMessage("This is a default alert dialog.")
        alertDialogBuilder.setPositiveButton("OK") { dialog, _ ->
            // Handle positive button click
            dialog.dismiss()
        }

        // Uncomment the following line if you want to include a negative button
        // alertDialogBuilder.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }

        // Uncomment the following line if you want to include a neutral button
        // alertDialogBuilder.setNeutralButton("Neutral") { dialog, _ -> dialog.dismiss() }

        val alertDialog = alertDialogBuilder.create()
        alertDialog.show()
    }
}