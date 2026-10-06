package com.moontvplus.native.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager

/**
 * TV 遥控焦点工具。
 * 让原生 View 组在 Android TV 上能正确响应方向键，并提供一个"焦点框"高亮当前焦点。
 */
object TVFocus {
    /** 给 Activity 窗口加 TV 沉浸式全屏 */
    fun applyImmersive(activity: Activity) {
        activity.window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
    }

    /**
     * 遍历 View 树，把可聚焦元素统一加 focusSelector（高亮框）。
     * TV 上原生焦点主要靠 focusable + focusableInTouchMode。
     */
    fun setupFocusable(root: View) {
        if (root is ViewGroup) {
            root.isFocusable = false
            for (i in 0 until root.childCount) {
                val c = root.getChildAt(i)
                c.isFocusable = true
                c.isFocusableInTouchMode = true
                setupFocusable(c)
            }
        } else {
            root.isFocusable = true
            root.isFocusableInTouchMode = true
        }
    }

    /** 把方向键事件分发给当前焦点 View（TV 遥控器方向键常进 Activity，需手动转发） */
    fun dispatchDpad(activity: Activity, keyCode: Int, event: KeyEvent): Boolean {
        val focused = activity.currentFocus
        if (focused != null && focused.isFocused) {
            focused.dispatchKeyEvent(event)
            return true
        }
        return false
    }
}

/** 把 Activity 的 base context 取出来 */
fun Context.baseContext(): Context = when (this) {
    is Activity -> application
    is ContextWrapper -> baseContext()
    else -> this
}
