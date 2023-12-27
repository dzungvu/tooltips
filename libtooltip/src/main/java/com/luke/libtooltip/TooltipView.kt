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
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.luke.libtooltip.extensions.afterMeasured
import com.luke.libtooltip.extensions.getDecorRect
import com.luke.libtooltip.extensions.getDisplayHeight
import com.luke.libtooltip.extensions.getDisplayWidth
import com.luke.libtooltip.extensions.getLocationOnScreen
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

    private var tooltipViewMeasureWidth: Int = 0
    private var tooltipViewMeasureHeight: Int = 0

    private var savedPositionToShow: Point = Point(0, 0)
    private var onScrollChangedListener: ViewTreeObserver.OnScrollChangedListener? = null

    private var decorViewRect: Rect = Rect(0, 0, 0, 0)

    init {
        val tooltipLayoutId = builder.layoutId ?: R.layout.tooltip_default_layout
        tooltipView = LayoutInflater.from(context).inflate(tooltipLayoutId, null, false)
        tooltipTextView = tooltipView.findViewById(R.id.tv_tooltip_content)
        tooltipTextView?.text = builder.content ?: ""


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
//                            if (builder.dismissWhenTouchOutside) {
//                                this@Balloon.dismiss()
//                            }
//                            onBalloonOutsideTouchListener?.onBalloonOutsideTouch(view, event)
                            return true
                        }
                        return false
                    }
                }
            )
        }
    }

    private fun findPositionWithPositionSetup(
        viewWidth: Int,
        viewHeight: Int,
        point: Point
    ): Point {

        Log.d(TAG, "Anchor view width: $viewWidth - Anchor view height: $viewHeight")

        var moveXSpace = 0
        var moveYSpace = 0
        when (builder.anchorPosition) {
            TooltipPosition.TOP_LEFT -> {
                moveXSpace = 0
                moveYSpace = 0
            }

            TooltipPosition.TOP_CENTER -> {
                moveXSpace = (viewWidth / 2 - tooltipViewMeasureWidth / 2)
                moveYSpace = 0
            }

            TooltipPosition.TOP_RIGHT -> {
                moveXSpace = viewWidth
                moveYSpace = 0
            }

            TooltipPosition.BOTTOM_LEFT -> {
                moveXSpace = 0
                moveYSpace = viewHeight
            }

            TooltipPosition.BOTTOM_CENTER -> {
                moveXSpace = viewWidth / 2 - tooltipViewMeasureWidth / 2
                moveYSpace = viewHeight
            }

            TooltipPosition.BOTTOM_RIGHT -> {
                moveXSpace = viewWidth
                moveYSpace = viewHeight
            }
        }
        return Point(point.x + moveXSpace, point.y + moveYSpace)
    }

    private fun shouldShowTooltip(anchorView: View): Boolean {
        val anchorViewVisibleRect = anchorView.getVisibleRect()
        Log.d(
            TAG,
            "Anchor view visible rect: ${anchorViewVisibleRect.top} - ${anchorViewVisibleRect.bottom} - ${anchorViewVisibleRect.left} - ${anchorViewVisibleRect.right} with wxh: ${getDisplayWidth()}x${getDisplayHeight()} and decor txb: ${decorViewRect.top} - ${decorViewRect.bottom}"
        )

        when (builder.anchorPosition) {
            TooltipPosition.TOP_LEFT -> {
                if (anchorViewVisibleRect.top > getDisplayHeight() || anchorViewVisibleRect.left <= 0) {
                    return false
                }
            }

            TooltipPosition.TOP_CENTER -> {
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

            TooltipPosition.TOP_RIGHT -> {
                if (anchorViewVisibleRect.bottom < anchorView.height || anchorViewVisibleRect.right < 0) {
                    return false
                }
            }

            TooltipPosition.BOTTOM_LEFT -> {
                if (anchorViewVisibleRect.bottom < 0 || (anchorViewVisibleRect.bottom >= getDisplayHeight() && (anchorViewVisibleRect.top - anchorViewVisibleRect.bottom) < anchorView.height)) {
                    return false
                }
            }

            TooltipPosition.BOTTOM_CENTER -> {
                if (anchorViewVisibleRect.bottom < 0 || (anchorViewVisibleRect.bottom >= getDisplayHeight() && (anchorViewVisibleRect.top - anchorViewVisibleRect.bottom) < anchorView.height)) {
                    return false
                }
            }

            TooltipPosition.BOTTOM_RIGHT -> {
                if (anchorViewVisibleRect.bottom < 0 || (anchorViewVisibleRect.bottom >= getDisplayHeight() && (anchorViewVisibleRect.top - anchorViewVisibleRect.bottom) < anchorView.height)) {
                    return false
                }
            }
        }
        return true
    }

    private fun findPositionAndExecuteCallback(anchorView: View, callback: (point: Point) -> Unit) {

        val locationPoint = anchorView.getLocationOnScreen()
        Log.d(TAG, "View position on screen: $locationPoint")

        savedPositionToShow = findPositionWithPositionSetup(
            viewWidth = anchorView.width,
            viewHeight = anchorView.height,
            point = Point(locationPoint.x, locationPoint.y)
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
                    val locationPoint = anchorView.getLocationOnScreen()
                    savedPositionToShow = findPositionWithPositionSetup(
                        viewWidth = anchorView.width,
                        viewHeight = anchorView.height,
                        point = Point(locationPoint.x, locationPoint.y)
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

    /**
     * Calculate some constant values before show tooltip
     * @param anchorView the view that tooltip will be shown
     */
    private fun prepareBeforeShow(anchorView: View) {
        val decorRect = anchorView.getDecorRect()
        Log.d(TAG, "Decor rect: $decorRect")
        decorViewRect = decorRect

        tooltipTextView?.measure(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        tooltipViewMeasureWidth = tooltipTextView?.measuredWidth ?: 0
        tooltipViewMeasureHeight = tooltipTextView?.measuredHeight ?: 0
        Log.d(
            TAG,
            "Tooltip view width: $tooltipViewMeasureWidth - Tooltip view height: $tooltipViewMeasureHeight"
        )
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
            Log.d(TAG, "Show tooltip in sync")
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

        var anchorPosition: TooltipPosition = TooltipPosition.BOTTOM_RIGHT
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

        fun build(context: Context): TooltipView {
            return TooltipView(context = context, builder = this)
        }

    }

    override fun onDestroy(owner: LifecycleOwner) {
        popUpWindow.dismiss()
        super.onDestroy(owner)
    }

    enum class TooltipPosition {
        TOP_LEFT,
        TOP_CENTER,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_CENTER,
        BOTTOM_RIGHT
    }
}