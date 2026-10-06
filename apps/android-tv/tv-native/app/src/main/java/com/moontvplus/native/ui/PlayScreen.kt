package com.moontvplus.native.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ui.PlayerControlView
import com.google.android.exoplayer2.ui.PlayerView
import com.moontvplus.native.data.VideoDetail
import com.moontvplus.native.data.VideoItem
import com.moontvplus.native.util.App
import com.moontvplus.native.util.TVFocus

/**
 * 播放页：ExoPlayer（HLS）拉 m3u8 代理。
 * 遥控：OK 暂停、上下 ±10s、左右 ±30s、返回退。
 */
@SuppressLint("ViewConstructor")
class PlayScreen(context: Context, private val item: VideoItem, private val episode: VideoDetail.Episode, private val onBack: () -> Unit) :
    FrameLayout(context) {

    private var player: ExoPlayer? = null
    private val playerView = PlayerView(context)
    private val status = TextView(context)
    private var lastEp = 0

    init {
        setBackgroundColor(Color.BLACK)

        val layout = FrameLayout(context)
        addView(layout, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        layout.addView(playerView, LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER
        ))

        status.text = "加载中…"
        status.setTextColor(Color.WHITE)
        status.textSize = 16f
        status.gravity = Gravity.CENTER
        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        layout.addView(status, lp)

        TVFocus.applyImmersive(context as android.app.Activity)
        isFocusable = true
        isFocusableInTouchMode = true
        setOnKeyListener { _, e -> handleKey(e) }

        start()
    }

    private fun start() {
        lastEp = episode.index
        val m3u8 = App.client.playableM3u8Url(episode.url, item.source)
        player = ExoPlayer.Builder(context).build().also { p ->
            playerView.player = p
            val ctl = PlayerControlView(context)
            ctl.visibility = View.GONE
            p.addListener(object : com.google.android.exoplayer2.Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    status.visibility = if (isPlaying) View.GONE else View.VISIBLE
                }
                override fun onPlayerError(error: com.google.android.exoplayer2.PlaybackException) {
                    status.visibility = View.VISIBLE
                    status.text = "播放失败: ${error.message}"
                }
            })
            val ctlParams = PlayerView.ControllerParams(false)
            p.playWhenReady = true
        }
        player?.setMediaItem(MediaItem.fromUri(m3u8))
        player?.prepare()
    }

    private fun handleKey(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                player?.let {
                    if (it.isPlaying) it.pause() else it.play()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                player?.seekTo((player?.currentPosition ?: 0L) + 10_000L)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                player?.seekTo((player?.currentPosition ?: 0L) - 10_000L)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                player?.seekTo((player?.currentPosition ?: 0L) + 30_000L)
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                player?.seekTo((player?.currentPosition ?: 0L) - 30_000L)
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                onBack()
                return true
            }
        }
        return false
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        player?.release()
        player = null
    }
}
