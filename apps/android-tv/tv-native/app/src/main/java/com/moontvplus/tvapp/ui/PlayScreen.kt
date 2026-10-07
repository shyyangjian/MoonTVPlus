package com.moontvplus.tvapp.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.util.C
import com.moontvplus.tvapp.data.VideoDetail
import com.moontvplus.tvapp.data.VideoItem
import com.moontvplus.tvapp.util.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 播放页：ExoPlayer 拉 HLS。
 * 上下方向键调倍速，OK 播放/暂停，返回退出。
 */
class PlayScreen(
    context: Context,
    private val item: VideoItem,
    private val episode: VideoDetail.Episode
) : FrameLayout(context) {

    private val scope = CoroutineScope(Dispatchers.Main)
    lateinit var player: ExoPlayer
        private set
    lateinit var playerView: PlayerView
        private set
    private lateinit var statusLabel: TextView
    private lateinit var playBtn: TextView

    /** 由 Activity 注入，播放页按返回键时回调到首页 */
    var releaseOnExit: (() -> Unit)? = null

    init {
        val root = FrameLayout(context)
        root.setBackgroundColor(Color.BLACK)
        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        playerView = PlayerView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            useController = false
            resizeMode = C.RESIZE_MODE_FIT
        }
        root.addView(playerView)

        player = ExoPlayer.Builder(context).build()
        playerView.player = player

        playBtn = TextView(context).apply {
            text = "▶ 播放"
            textSize = 20f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#6366F1"))
            gravity = android.view.Gravity.CENTER
            isFocusable = true
            isFocusableInTouchMode = false
        }
        root.addView(playBtn, LayoutParams(
            LayoutParams.WRAP_CONTENT,
            (64 * context.resources.displayMetrics.density).toInt(),
            android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
        ))

        statusLabel = TextView(context).apply {
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(
                (32 * context.resources.displayMetrics.density).toInt(),
                (16 * context.resources.displayMetrics.density).toInt(),
                0, 0
            )
            alpha = 0.8f
        }
        root.addView(statusLabel, LayoutParams(
            LayoutParams.MATCH_PARENT,
            (48 * context.resources.displayMetrics.density).toInt(),
            android.view.Gravity.TOP or android.view.Gravity.START
        ))

        isFocusable = true
        isFocusableInTouchMode = false

        statusLabel.text = "加载中..."
        load()
    }

    private fun load() {
        scope.launch {
            val url = withContext(Dispatchers.IO) {
                App.client.playableM3u8Url(episode.url, item.source)
            }
            statusLabel.text = "《${item.title}》 ${if (episode.title.isNullOrBlank()) "第${episode.index}集" else episode.title}"

            val mediaItem = MediaItem.fromUri(url)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
            player.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    statusLabel.text = "播放失败：" + (error.errorMessage?.toString() ?: "未知错误")
                    statusLabel.setTextColor(Color.parseColor("#FF6B6B"))
                }
                override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                    playBtn.text = if (isPlayingNow) "⏸ 暂停" else "▶ 播放"
                }
            })
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (event?.action != android.view.KeyEvent.ACTION_DOWN) return false
        when (keyCode) {
            android.view.KeyEvent.KEYCODE_DPAD_CENTER,
            android.view.KeyEvent.KEYCODE_ENTER -> {
                if (player.isPlaying) player.pause() else player.play()
                return true
            }
            android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                val newSpeed = (player.playbackSpeed + 0.5f).coerceIn(0.5f, 4f)
                player.setPlaybackSpeed(newSpeed)
                statusLabel.text = "倍速：${newSpeed}x"
                return true
            }
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                val newSpeed = (player.playbackSpeed - 0.5f).coerceIn(0.5f, 4f)
                player.setPlaybackSpeed(newSpeed)
                statusLabel.text = "倍速：${newSpeed}x"
                return true
            }
            android.view.KeyEvent.KEYCODE_BACK -> {
                saveProgress()
                release()
                releaseOnExit?.invoke()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    fun release() {
        try { player.release() } catch (_: Exception) {}
    }

    fun saveProgress() {
        try {
            val total = player.duration
            if (total > 0 && total > 10_000L) {
                App.savePlayRecord(
                    com.moontvplus.tvapp.data.PlayRecord(
                        source = item.source,
                        id = item.id,
                        episodeIndex = episode.index,
                        playTimeMs = player.currentPosition,
                        totalMs = total,
                        title = item.title,
                        cover = item.cover
                    )
                )
            }
        } catch (_: Exception) {}
    }

    @SuppressLint("SetTextInLayout")
    private fun density() = resources.displayMetrics.density
}
