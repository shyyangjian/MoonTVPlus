package com.moontvplus.tvapp

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.moontvplus.tvapp.data.VideoDetail
import com.moontvplus.tvapp.data.VideoItem
import com.moontvplus.tvapp.ui.DetailScreen
import com.moontvplus.tvapp.ui.HomeScreen
import com.moontvplus.tvapp.ui.LoginScreen
import com.moontvplus.tvapp.ui.PlayScreen
import com.moontvplus.tvapp.util.App

class MainActivity : Activity() {

    private lateinit var root: FrameLayout

    @Override
    protected fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        App.attach(this)
        App.init(this, BuildConfig.BASE_URL)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )

        root = FrameLayout(this)
        setContentView(root)
        root.setBackgroundColor(Color.parseColor("#0A0A14"))

        if (App.client.isLoggedIn) {
            showHome()
        } else {
            showLogin()
        }
    }

    private fun showLogin() {
        val login = LoginScreen(this) { showHome() }
        root.removeAllViews()
        root.addView(login, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
    }

    private fun showHome() {
        val home = HomeScreen(this) { item -> showDetail(item) }
        root.removeAllViews()
        root.addView(home, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
    }

    private fun showDetail(item: VideoItem) {
        val detail = DetailScreen(this, item) { ep -> showPlay(item, ep) }
        root.removeAllViews()
        root.addView(detail, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
    }

    private fun showPlay(item: VideoItem, ep: VideoDetail.Episode) {
        val play = PlayScreen(this, item, ep) { showHome() }
        root.removeAllViews()
        root.addView(play, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
    }

    @Override
    fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // 让当前屏自己处理按键
        return super.onKeyDown(keyCode, event)
    }

    @Override
    protected fun onDestroy() {
        super.onDestroy()
    }
}
