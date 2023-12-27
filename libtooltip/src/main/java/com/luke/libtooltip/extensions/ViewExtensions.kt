package com.luke.libtooltip.extensions

import android.app.Activity
import android.content.ContextWrapper
import android.content.res.Resources
import android.graphics.Point
import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.OnScrollChangedListener
import android.view.Window
import com.luke.libtooltip.TooltipView

fun View.showTooltip(tooltipView: TooltipView) {
    post {
        tooltipView.show(anchorView = this)
    }
}

suspend fun View.showTooltipAsync(tooltipView: TooltipView) {
    tooltipView.showAsync(this)
}

fun <T : View> T.afterMeasured(f: T.() -> Unit) {
    viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            if (measuredWidth > 0 && measuredHeight > 0) {
                viewTreeObserver.removeOnGlobalLayoutListener(this)
                try {
                    f()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    })
}

fun View.onScrollChangedListener(callback: () -> Unit): OnScrollChangedListener {
    val onScrollChanged = OnScrollChangedListener {
        callback.invoke()
    }
    viewTreeObserver.addOnScrollChangedListener(onScrollChanged)
    return onScrollChanged
}

fun View.getLocationOnScreen(): Point {
    val location = IntArray(2)
    getLocationOnScreen(location)
    return Point(location[0], location[1])
}

fun View.getVisibleRect(): Rect {
    val rect = Rect()
    getGlobalVisibleRect(rect)
    return rect
}

fun View.getWindow(): Window? {
    val mContext = this.context
    if (mContext is Activity) {
        return mContext.window
    } else if (mContext is ContextWrapper) {
        val baseContext = mContext.baseContext
        if (baseContext is Activity) {
            return baseContext.window
        }
    }
    return null
}

fun getDisplayWidth(): Int {
    return Resources.getSystem().displayMetrics.widthPixels
}

fun getDisplayHeight(): Int {
    return Resources.getSystem().displayMetrics.heightPixels
}

fun View.getDecorRect(): Rect {
    getWindow()?.decorView?.let { decorView ->
        val rect = Rect()
        decorView.getWindowVisibleDisplayFrame(rect)
        return rect
    }
    return Rect(0, 0, getDisplayWidth(), getDisplayHeight())
}

fun View.displayRectWithoutDecorView(): Rect {
    val screenWidth = getDisplayWidth()
    val screenHeight = getDisplayHeight()

    getWindow()?.decorView?.let { decorView ->
        val rect = Rect()
        decorView.getWindowVisibleDisplayFrame(rect)

        // Calculate the main display rect by subtracting system UI dimensions
        return Rect(
            0,
            0,
            screenWidth - (rect.right - rect.left),
            screenHeight - (rect.bottom - rect.top)
        )
    } ?: return Rect(0, 0, screenWidth, screenHeight)
}


