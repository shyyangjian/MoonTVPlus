package com.moontvplus.tvapp

import android.app.Activity
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
    private var currentPlayScreen: PlayScreen? = null

    override fun onCreate(savedInstanceState: Bundle?) {
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
        root.setBackgroundColor(android.graphics.Color.parseColor("#0A0A14"))
        setContentView(root)

        if (App.client.isLoggedIn) showHome() else showLogin()
    }

    private fun showLogin() {
        val login = LoginScreen(this) { showHome() }
        root.removeAllViews()
        root.addView(login, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
    }

    private fun showHome() {
        currentPlayScreen?.let {
            it.release()
            it.saveProgress()
            currentPlayScreen = null
        }
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
        currentPlayScreen = PlayScreen(this, item, ep).also { it.releaseOnExit = { showHome() } }
        val play = currentPlayScreen!!
        root.removeAllViews()
        root.addView(play, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event?.action == KeyEvent.ACTION_DOWN) {
            when {
                currentPlayScreen != null -> {
                    currentPlayScreen?.let {
                        it.release()
                        it.saveProgress()
                        currentPlayScreen = null
                    }
                    showDetailFromBackStack()
                    return true
                }
                // 如果在详情页，返回到首页
                else -> {
                    // 简单判断：如果不在登录页就回首页
                    if (App.client.isLoggedIn) {
                        showHome()
                    }
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /** 简化版：返回栈通过 root 当前 child 类型判断 */
    private fun showDetailFromBackStack() {
        showHome()
    }

    override fun onDestroy() {
        currentPlayScreen?.release()
        super.onDestroy()
    }
}
