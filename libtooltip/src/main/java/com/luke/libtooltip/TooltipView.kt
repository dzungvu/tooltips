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
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.luke.libtooltip.extensions.afterMeasured
import com.luke.libtooltip.extensions.getDecorRect
import com.luke.libtooltip.extensions.getDisplayHeight
import com.luke.libtooltip.extensions.getDisplayWidth
import com.luke.libtooltip.extensions.getVisibleRect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min

class TooltipView(private val context: Context, private val builder: TooltipBuilder) :
    DefaultLifecycleObserver {
    private companion object {
        const val TAG = "TooltipView"
    }

    private var isDismissed: Boolean = false
    private val popUpWindow: PopupWindow
    private val tooltipContainerView: View
    private val tooltipContentView: View
    private val tooltipTextView: TextView?
    private val tooltipArrowView: ImageView

    private var tooltipViewMeasureWidth: Int = 0
    private var tooltipViewMeasureHeight: Int = 0

    private var savedPositionToShow: Point = Point(0, 0)
    private var onPreDrawListener: ViewTreeObserver.OnPreDrawListener? = null

    private var decorViewRect: Rect = Rect(0, 0, 0, 0)

    init {
        //tooltip container view
        val tooltipContainerLayoutId = when (builder.anchorPosition) {
            TooltipPosition.TOP -> R.layout.tooltip_container_top
            TooltipPosition.BOTTOM -> R.layout.tooltip_container_bottom
        }
        tooltipContainerView =
            LayoutInflater.from(context).inflate(tooltipContainerLayoutId, null, false)

        //tooltip content view
        val tooltipContentLayoutId = builder.contentLayoutId ?: R.layout.tooltip_content
        tooltipContentView =
            LayoutInflater.from(context).inflate(tooltipContentLayoutId, null, false)
        tooltipContainerView.findViewById<FrameLayout>(R.id.fl_tooltip_content_container)
            .addView(tooltipContentView)

        //tooltip text view
        tooltipTextView = tooltipContentView.findViewById(R.id.tv_tooltip_content)
        if (tooltipTextView == null) {
            Log.e(TAG, "Tooltip text view is null")
        }
        tooltipTextView?.text = builder.content ?: ""

        //tooltip arrow view
        tooltipArrowView = tooltipContainerView.findViewById(R.id.iv_tooltip_arrow)
        if (builder.arrowResId != null) {
            tooltipArrowView.setImageResource(builder.arrowResId!!)
        }

        //tooltip text color
        if (builder.contentLayoutId == null && builder.textColorRes != null) {
            val textColor = builder.textColorRes?.let {
                ContextCompat.getColor(context, it)
            } ?: Color.WHITE
            tooltipTextView?.setTextColor(textColor)
        }

        //tooltip background
        if (builder.contentLayoutId == null && builder.backgroundColorRes != null) {
            val backgroundColor = builder.backgroundColorRes?.let {
                ContextCompat.getColor(context, it)
            } ?: Color.WHITE
            ContextCompat.getDrawable(context, R.drawable.rectangle)?.let {
                DrawableCompat.setTint(it, backgroundColor)
                tooltipContentView.background = it
            }
        }
        if (builder.arrowResId == null && builder.backgroundColorRes != null) {
            val backgroundColor = builder.backgroundColorRes?.let {
                ContextCompat.getColor(context, it)
            } ?: Color.WHITE
            ContextCompat.getDrawable(context, R.drawable.polygon)?.let {
                DrawableCompat.setTint(it, backgroundColor)
                tooltipArrowView.setImageDrawable(it)
            }
        }

        popUpWindow = PopupWindow(
            tooltipContainerView,
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
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
                                dismissTooltip()
                            }
                            return true
                        }
                        return false
                    }
                }
            )
        }

        if (builder.dismissStrategy == DismissStrategy.DISMISS_WHEN_TOUCH_INSIDE) {
            tooltipContainerView.setOnClickListener {
                dismissTooltip()
            }
        }
    }

    /**
     * find the point to show tooltip
     * @param anchorView the view that tooltip will be shown
     * @return the point to show tooltip
     */
    private fun findTooltipRawPosition(
        anchorView: View,
    ): Point {
        return anchorView.getVisibleRect().let {
            measureContentView()
            val tooltipViewWidth = min(tooltipContentView.measuredWidth, getDisplayWidth())
            val tooltipViewHeight = tooltipContentView.measuredHeight

            when (builder.anchorPosition) {
                TooltipPosition.TOP -> {
                    tooltipArrowView.measure(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                    val arrowHeight = tooltipArrowView.measuredHeight
                    Point(
                        (it.left + (it.width() / 2)) - (tooltipViewWidth / 2),
                        it.top - tooltipViewHeight - arrowHeight
                    )
                }

                TooltipPosition.BOTTOM -> {
                    Point(
                        (it.left + (it.width() / 2)) - (tooltipViewWidth / 2),
                        it.bottom
                    )
                }
            }
        }
    }

    private fun shouldShowTooltip(anchorView: View): Boolean {
        if (
            anchorView.visibility != View.VISIBLE
            || anchorView.alpha == 0f
            || anchorView.scaleX == 0f
            || anchorView.scaleY == 0f
        ) {
            return false
        }
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

    private fun measureContentView() {
        when (tooltipContentView) {
            is TextView -> {
                tooltipContentView.measure(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            }

            else -> {
                tooltipContentView.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
            }
        }
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

        measureContentView()
        tooltipViewMeasureWidth = min(tooltipContentView.measuredWidth, getDisplayWidth())
        tooltipViewMeasureHeight = tooltipContentView.measuredHeight

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
        val constrainSet = ConstraintSet()
        constrainSet.clone(tooltipContainerView as ConstraintLayout)

        if (savedPositionToShow.x + tooltipViewMeasureWidth > getDisplayWidth()) {
            val newPosition = getDisplayWidth() - tooltipViewMeasureWidth
            if(newPosition == 0) {
                constrainSet.clear(R.id.iv_tooltip_arrow, ConstraintSet.START)
                constrainSet.connect(R.id.iv_tooltip_arrow, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                constrainSet.setMargin(
                    R.id.iv_tooltip_arrow,
                    ConstraintSet.END,
                    getDisplayWidth() - anchorRect.centerX() - (tooltipArrowViewWidth / 2)
                )
            } else {
                constrainSet.setMargin(
                    R.id.iv_tooltip_arrow,
                    ConstraintSet.START,
                    anchorRect.centerX() - newPosition - (tooltipArrowViewWidth / 2)
                )
            }

        } else if (savedPositionToShow.x < 0) {
            constrainSet.setMargin(
                R.id.iv_tooltip_arrow,
                ConstraintSet.START,
                anchorRect.centerX() - (tooltipArrowViewWidth / 2)
            )
        } else {
            constrainSet.setMargin(
                R.id.iv_tooltip_arrow,
                ConstraintSet.START,
                anchorRect.centerX() - savedPositionToShow.x - (tooltipArrowViewWidth / 2)
            )
        }
        // Apply the updated LayoutParams
        constrainSet.applyTo(tooltipContainerView)
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
        if(!isDismissed || popUpWindow.isShowing) {
            findPositionAndExecuteCallback(anchorView) { point ->
                show(anchorView, point)
            }
        }
    }

    private fun show(anchorView: View, point: Point) {
        if (isDismissed || popUpWindow.isShowing) {
            Log.d(TAG, "Tooltip is dismissed")
            return
        } else {
            prepareBeforeShow(anchorView)
            Log.d(TAG, "Point to show: $point")
            initVisibleListenerForAnchor(anchorView)
            if (shouldShowTooltip(anchorView)) {
                popUpWindow.showAtLocation(anchorView, Gravity.NO_GRAVITY, point.x, point.y)
            }
        }
    }

    private fun initVisibleListenerForAnchor(anchorView: View) {
        if(onPreDrawListener != null) {
            try {
                anchorView.viewTreeObserver.removeOnPreDrawListener(onPreDrawListener)
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            }
        }
        anchorView.viewTreeObserver.addOnPreDrawListener {
            if (anchorView.isVisible) {
                Log.d(TAG, "Pre-draw Anchor view visible")
                if (shouldShowTooltip(anchorView)) {
                    if(popUpWindow.isShowing) {
                        updateTooltipPosition(anchorView)
                    } else {
                        show(anchorView)
                    }
                } else {
                    popUpWindow.dismiss()
                }
            } else {
                Log.d(TAG, "Pre-draw Anchor view is not visible")
                popUpWindow.dismiss()
            }
            true
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

    internal fun removeOnPreDrawListener(anchorView: View) {
        try {
            onPreDrawListener?.let {
                anchorView.viewTreeObserver.removeOnPreDrawListener(it)
            }
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
    }

    internal fun dismissTooltip() {
        if (popUpWindow.isShowing) {
            popUpWindow.dismiss()
        }
        builder.tooltipDismissListener?.onTooltipDismissed()
        onPreDrawListener = null
        isDismissed = true
    }

    override fun onDestroy(owner: LifecycleOwner) {
        dismissTooltip()
        super.onDestroy(owner)
    }

    class TooltipBuilder {
        var content: String? = null
            private set

        @LayoutRes
        var contentLayoutId: Int? = null
            private set

        @DrawableRes
        var arrowResId: Int? = null
            private set

        @ColorRes
        var backgroundColorRes: Int? = null
            private set

        @ColorRes
        var textColorRes: Int? = null
            private set

        var anchorPosition: TooltipPosition = TooltipPosition.BOTTOM
            private set

        var dismissStrategy: DismissStrategy = DismissStrategy.DISMISS_WHEN_TOUCH_INSIDE
            private set

        var tooltipDismissListener: TooltipDismissListener? = null
            private set

        fun setContent(content: String): TooltipBuilder = this.apply {
            this.content = content
        }

        /**
         * Set custom layout for tooltip
         * @param layoutId the layout id
         * @note: the layout must have TextView with id is tv_tooltip_content
         */
        fun setContentLayoutId(@LayoutRes layoutId: Int) = this.apply {
            this.contentLayoutId = layoutId
        }

        /**
         * Set custom arrow for tooltip
         * @param arrowResId the arrow resource id
         * If this method is not called, the default arrow will be used
         * @see R.drawable.polygon
         */
        fun setArrowResId(@DrawableRes arrowResId: Int) = this.apply {
            this.arrowResId = arrowResId
        }

        /**
         * Set background color for tooltip
         * @param backgroundColorRes the background color resource id
         * If this method is not called, the default background color will be used
         */
        fun setBackgroundColorRes(@ColorRes backgroundColorRes: Int) = this.apply {
            this.backgroundColorRes = backgroundColorRes
        }

        /**
         * Set text color for tooltip
         * @param textColorRes the text color resource id
         * If this method is not called, the default text color will be used
         */
        fun setTextColorRes(@ColorRes textColorRes: Int) = this.apply {
            this.textColorRes = textColorRes
        }

        /**
         * Set anchor position for tooltip
         * @param position the anchor position
         * If this method is not called, the default anchor position will be used
         * @see TooltipPosition.BOTTOM
         */
        fun setAnchorPosition(position: TooltipPosition): TooltipBuilder = this.apply {
            this.anchorPosition = position
        }

        /**
         * Set dismiss strategy for tooltip
         * @param strategy the dismiss strategy
         * If this method is not called, the default dismiss strategy will be used
         * @see DismissStrategy.DISMISS_WHEN_TOUCH_INSIDE
         */
        fun setDismissStrategy(strategy: DismissStrategy): TooltipBuilder = this.apply {
            this.dismissStrategy = strategy
        }

        /**
         * Set tooltip dismiss listener
         * @param tooltipDismissListener the tooltip dismiss listener
         */
        fun setTooltipDismissListener(tooltipDismissListener: TooltipDismissListener): TooltipBuilder =
            this.apply {
                this.tooltipDismissListener = tooltipDismissListener
            }

        /**
         * Build tooltip view
         * @param context the context
         */
        fun build(context: Context): TooltipView {
            return TooltipView(context = context, builder = this)
        }

    }

    interface TooltipDismissListener {
        fun onTooltipDismissed()

        companion object {
            inline operator fun invoke(crossinline block: () -> Unit) =
                object : TooltipDismissListener {
                    override fun onTooltipDismissed() = block()
                }
        }
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
