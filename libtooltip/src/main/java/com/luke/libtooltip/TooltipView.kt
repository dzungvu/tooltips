package com.luke.libtooltip

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.luke.libtooltip.extensions.afterMeasured
import com.luke.libtooltip.extensions.getDecorRect
import com.luke.libtooltip.extensions.getDisplayHeight
import com.luke.libtooltip.extensions.getDisplayWidth
import com.luke.libtooltip.extensions.getVisibleRect
import com.luke.libtooltip.extensions.onScrollChangedListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class TooltipView(private val context: Context, private val builder: TooltipBuilder) :
    DefaultLifecycleObserver {
    private companion object {
        const val TAG = "TooltipView"
    }

    private val popUpWindow: PopupWindow
    private val tooltipView: View
    private val tooltipTextView: TextView?
    private val tooltipArrowView: ImageView

    private var tooltipViewMeasureWidth: Int = 0
    private var tooltipViewMeasureHeight: Int = 0

    private var savedPositionToShow: Point = Point(0, 0)
    private var onScrollChangedListener: ViewTreeObserver.OnScrollChangedListener? = null

    private var decorViewRect: Rect = Rect(0, 0, 0, 0)
    private var anchorViewRect: Rect = Rect(0, 0, 0, 0)

    init {
        val tooltipLayoutId = builder.layoutId ?: R.layout.tooltip_default_layout
        tooltipView = LayoutInflater.from(context).inflate(tooltipLayoutId, null, false)
        tooltipTextView = tooltipView.findViewById(R.id.tv_tooltip_content)
        tooltipTextView?.text = builder.content ?: ""
        tooltipArrowView = tooltipView.findViewById(R.id.iv_tooltip_arrow)


        popUpWindow = PopupWindow(
            tooltipView,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isFocusable = false
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setTouchInterceptor(
                object : View.OnTouchListener {
                    @SuppressLint("ClickableViewAccessibility")
                    override fun onTouch(view: View, event: MotionEvent): Boolean {
                        if (event.action == MotionEvent.ACTION_OUTSIDE) {
                            if (builder.dismissStrategy == DismissStrategy.DISMISS_WHEN_TOUCH_OUTSIDE) {
                                dismiss()
                            }
                            return true
                        }
                        return false
                    }
                }
            )
        }

        if (builder.dismissStrategy == DismissStrategy.DISMISS_WHEN_TOUCH_INSIDE) {
            tooltipView.setOnClickListener {
                popUpWindow.dismiss()
            }
        }
    }

    private fun findTooltipRawPosition(
        anchorView: View,
    ): Point {
        return anchorView.getVisibleRect().let {
            tooltipTextView?.measure(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            val tooltipViewWidth = tooltipTextView?.measuredWidth ?: 0
            val tooltipViewHeight = tooltipTextView?.measuredHeight ?: 0
            when(builder.anchorPosition) {
                TooltipPosition.TOP -> {
                    Point(
                        (it.left + (it.width() / 2)) - (tooltipViewWidth / 2),
                        it.top - tooltipViewHeight
                    )
                }
                TooltipPosition.BOTTOM -> {
                    Point(
                        (it.left + (it.width() / 2)) - (tooltipViewWidth / 2),
                        it.bottom
                    )
                }
            }
            Point(
                (it.left + (it.width() / 2)) - (tooltipViewWidth / 2),
                it.bottom
            )
        }
    }

    private fun shouldShowTooltip(anchorView: View): Boolean {
        val anchorViewVisibleRect = anchorView.getVisibleRect()
        Log.d(
            TAG,
            "Anchor view visible rect: ${anchorViewVisibleRect.top} - ${anchorViewVisibleRect.bottom} - ${anchorViewVisibleRect.left} - ${anchorViewVisibleRect.right} with wxh: ${getDisplayWidth()}x${getDisplayHeight()} and decor txb: ${decorViewRect.top} - ${decorViewRect.bottom}"
        )

        when (builder.anchorPosition) {

            TooltipPosition.TOP -> {
                return (
                        !(
                                //the anchor's rect with full height but the bottom is out of screen
                                //==> the anchor is display completely out of screen
                                (anchorViewVisibleRect.bottom - anchorViewVisibleRect.top) == anchorView.height
                                        && anchorViewVisibleRect.bottom > decorViewRect.bottom
                                )
                                && anchorViewVisibleRect.top > decorViewRect.top
                        )
            }

            TooltipPosition.BOTTOM -> {
                if (anchorViewVisibleRect.bottom < 0 || (anchorViewVisibleRect.bottom >= getDisplayHeight() && (anchorViewVisibleRect.top - anchorViewVisibleRect.bottom) < anchorView.height)) {
                    return false
                }
            }

        }
        return true
    }

    private fun findPositionAndExecuteCallback(anchorView: View, callback: (point: Point) -> Unit) {
        savedPositionToShow = findTooltipRawPosition(
            anchorView = anchorView,
        )
        callback.invoke(savedPositionToShow)
    }


    private suspend fun findPositionAndExecuteCallbackAsync(
        anchorView: View,
        callback: (point: Point) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            withTimeoutOrNull(1_000) {
                anchorView.afterMeasured {
                    savedPositionToShow = findTooltipRawPosition(
                        anchorView = anchorView,
                    )

                    Log.d(TAG, "emit before emit")
                    callback.invoke(savedPositionToShow)
                }
            } ?: kotlin.run {
                Log.d(TAG, "emit after emit")
                callback.invoke(Point(0, 0))
            }
        }
    }

    private fun adjustPositionAfterLayout() {

    }

    /**
     * Calculate some constant values before show tooltip
     * @param anchorView the view that tooltip will be shown
     */
    private fun prepareBeforeShow(anchorView: View) {
        val decorRect = anchorView.getDecorRect()
        val anchorRect = anchorView.getVisibleRect()
        Log.d(TAG, "Decor rect: $decorRect")
        decorViewRect = decorRect

        tooltipTextView?.measure(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        tooltipViewMeasureWidth = tooltipTextView?.measuredWidth ?: 0
        tooltipViewMeasureHeight = tooltipTextView?.measuredHeight ?: 0

        tooltipArrowView.measure(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        val tooltipArrowViewWidth = tooltipArrowView.measuredWidth
        Log.d(
            TAG,
            " prepareBeforeShow Tooltip view width: $tooltipViewMeasureWidth - Tooltip view height: $tooltipViewMeasureHeight"
        )
        Log.d(
            TAG,
            " prepareBeforeShow anchorRect centerX: ${anchorRect.centerX()} - arrow width: ${tooltipArrowViewWidth}"
        )
        Log.d(
            TAG,
            " prepareBeforeShow savedPositionToShow x: ${savedPositionToShow.x} - savedPositionToShow y: ${savedPositionToShow.y}"
        )
        Log.d(
            TAG,
            " prepareBeforeShow margin left: ${anchorRect.left - savedPositionToShow.x}"
        )

        val margin = if(savedPositionToShow.x + tooltipViewMeasureWidth > getDisplayWidth()) {
            val newPosition = getDisplayWidth() - tooltipViewMeasureWidth
            anchorRect.centerX() - newPosition - (tooltipArrowViewWidth / 2)
        } else if(savedPositionToShow.x < 0) {
            anchorRect.centerX() - (tooltipArrowViewWidth / 2)
        } else {
            anchorRect.centerX() - savedPositionToShow.x - (tooltipArrowViewWidth / 2)
        }

        val layoutParams = tooltipArrowView.layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.setMargins(
            margin,
            0,
            0,
            0
        )

        // Apply the updated LayoutParams
        tooltipArrowView.layoutParams = layoutParams
    }

    internal suspend fun showAsync(anchorView: View) {
        findPositionAndExecuteCallbackAsync(anchorView) { point ->
            Log.d(TAG, "Show tooltip in async")
            anchorView.post {
                show(anchorView, point)
            }
        }
    }

    internal fun show(anchorView: View) {
        findPositionAndExecuteCallback(anchorView) { point ->
            show(anchorView, point)
        }
    }

    private fun show(anchorView: View, point: Point) {
        prepareBeforeShow(anchorView)
        if (!popUpWindow.isShowing) {
            Log.d(TAG, "Point to show: $point")
            initScrollListenerForAnchor(anchorView)
            if (shouldShowTooltip(anchorView)) {
                popUpWindow.showAtLocation(anchorView, Gravity.NO_GRAVITY, point.x, point.y)
            }
        }
    }

    private fun initScrollListenerForAnchor(anchorView: View) {
        if (onScrollChangedListener != null) {
            try {
                anchorView.viewTreeObserver.removeOnScrollChangedListener(onScrollChangedListener)
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            }
        }
        onScrollChangedListener = anchorView.onScrollChangedListener {
            if (shouldShowTooltip(anchorView)) {
                updateTooltipPosition(anchorView)
            } else {
                popUpWindow.dismiss()
            }
        }
    }

    private fun updateTooltipPosition(anchorView: View) {
        findPositionAndExecuteCallback(anchorView) { point ->
            if (popUpWindow.isShowing) {
                popUpWindow.update(point.x, point.y, -1, -1)
            } else {
                show(anchorView, point)
            }
        }
    }


    class TooltipBuilder {
        var content: String? = null
            private set

        @DrawableRes
        var layoutId: Int? = null
            private set

        var anchorPosition: TooltipPosition = TooltipPosition.BOTTOM
            private set

        var dismissStrategy: DismissStrategy = DismissStrategy.DISMISS_WHEN_TOUCH_INSIDE
            private set

        fun setContent(content: String): TooltipBuilder = this.apply {
            this.content = content
        }

        fun setLayoutId(@DrawableRes layoutId: Int) = this.apply {
            this.layoutId = layoutId
        }

        fun setAnchorPosition(position: TooltipPosition): TooltipBuilder = this.apply {
            this.anchorPosition = position
        }

        fun setDismissStrategy(strategy: DismissStrategy): TooltipBuilder = this.apply {
            this.dismissStrategy = strategy
        }

        fun build(context: Context): TooltipView {
            return TooltipView(context = context, builder = this)
        }

    }

    override fun onDestroy(owner: LifecycleOwner) {
        popUpWindow.dismiss()
        super.onDestroy(owner)
    }

    enum class TooltipPosition {
        TOP,
        BOTTOM,
    }

    enum class DismissStrategy {
        DISMISS_WHEN_TOUCH_OUTSIDE,
        DISMISS_WHEN_TOUCH_INSIDE
    }
}
