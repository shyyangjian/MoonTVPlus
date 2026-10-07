package com.moontvplus.tvapp.ui

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import com.moontvplus.tvapp.data.VideoItem
import com.moontvplus.tvapp.util.App
import com.moontvplus.tvapp.util.TVFocus

/**
 * 登录页（纯原生 View，遥控焦点驱动）。
 * 登录成功后回调进首页。
 */
class LoginScreen(context: Context, private val onLogged: () -> Unit) :
    FrameLayout(context) {

    private val layout = FrameLayout(context)
    private val etUser: EditText
    private val etPass: EditText
    private val etUrl: EditText
    private val btnLogin: TextView
    private val status: TextView

    init {
        setBackgroundColor(Color.parseColor("#070816"))
        val pad = (32 * resources.displayMetrics.density).toInt()

        val title = TextView(context).apply {
            text = "MoonTV Plus"
            textSize = 40f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
        }
        val subTitle = TextView(context).apply {
            text = "电视端登录"
            textSize = 16f
            setTextColor(Color.parseColor("#8888AA"))
            gravity = android.view.Gravity.CENTER
        }

        val labelUser = label("用户名（可选）")
        etUser = EditText(context).apply {
            hint = "admin"
            background = null
            setTextColor(Color.WHITE)
            textSize = 20f
            imeOptions = EditorInfo.IME_ACTION_NEXT
        }
        val labelPass = label("密码")
        etPass = EditText(context).apply {
            hint = "密码"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            background = null
            setTextColor(Color.WHITE)
            textSize = 20f
            imeOptions = EditorInfo.IME_ACTION_GO
        }
        val labelUrl = label("服务器地址")
        etUrl = EditText(context).apply {
            setText(App.baseUrl)
            hint = "https://example.com"
            background = null
            setTextColor(Color.WHITE)
            textSize = 20f
            imeOptions = EditorInfo.IME_ACTION_GO
        }
        btnLogin = TextView(context).apply {
            text = "登 录"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#6366F1"))
            gravity = android.view.Gravity.CENTER
            isFocusable = true
            isFocusableInTouchMode = true
        }
        status = TextView(context).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#8888AA"))
            gravity = android.view.Gravity.CENTER
        }

        val col = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(title)
            addView(spacer(16))
            addView(subTitle)
            addView(spacer(40))
            addView(labelUser)
            addView(etUser)
            addView(spacer(24))
            addView(labelPass)
            addView(etPass)
            addView(spacer(24))
            addView(labelUrl)
            addView(etUrl)
            addView(spacer(32))
            val btnWrap = FrameLayout(context)
            btnWrap.addView(btnLogin, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (72 * resources.displayMetrics.density).toInt()
            ))
            addView(btnWrap)
            addView(spacer(16))
            addView(status)
        }
        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        lp.gravity = android.view.Gravity.CENTER
        layout.addView(col, lp)
        addView(layout)

        TVFocus.setupFocusable(this)

        etPass.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) { doLogin(); true } else false
        }
        etUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) { App.saveUrl(etUrl.text.toString()); doLogin(); true } else false
        }
        btnLogin.setOnClickListener { doLogin() }
    }

    private fun label(text: String) = TextView(context).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.parseColor("#8888AA"))
        setPadding(0, (12 * resources.displayMetrics.density).toInt(), 0, 4)
    }

    private fun spacer(h: Int): View = View(context).apply {
        layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            (h * resources.displayMetrics.density).toInt()
        )
    }

    private fun doLogin() {
        status.text = "登录中..."
        status.setTextColor(Color.parseColor("#AAAAAA"))
        val user = etUser.text.toString().takeIf { it.isNotBlank() }
        val pass = etPass.text.toString()
        if (pass.isEmpty()) {
            status.text = "请输入密码"
            return
        }
        App.saveUrl(etUrl.text.toString())
        App.login(user, pass) { ok, err ->
            if (ok) onLogged() else {
                status.text = err ?: "登录失败"
                status.setTextColor(Color.parseColor("#FF6B6B"))
            }
        }
    }

    override fun onKeyDown(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (etUser.hasFocus()) etPass.requestFocus()
                else if (etPass.hasFocus()) etUrl.requestFocus()
                else if (etUrl.hasFocus()) btnLogin.requestFocus()
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (etUrl.hasFocus()) etPass.requestFocus()
                else if (etPass.hasFocus()) etUser.requestFocus()
                else btnLogin.requestFocus()
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (btnLogin.hasFocus() || etPass.hasFocus() || etUrl.hasFocus()) {
                    doLogin()
                    return true
                }
            }
        }
        return super.onKeyDown(event)
    }
}
