package com.moontvplus.tvapp.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.moontvplus.tvapp.data.VideoDetail
import com.moontvplus.tvapp.data.VideoItem
import com.moontvplus.tvapp.util.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 详情页：标题 + 简介 + 集数按钮。
 * 遥控：方向键选集，OK 进播放。
 */
class DetailScreen(
    context: Context,
    private val item: VideoItem,
    private val onPlay: (VideoDetail.Episode) -> Unit
) : FrameLayout(context) {

    private val scope = CoroutineScope(Dispatchers.Main)
    private val epList = mutableListOf<VideoDetail.Episode>()
    private val root = FrameLayout(context)
    private lateinit var descBox: TextView
    private lateinit var epRow: LinearLayout
    private val loading = ProgressBar(context)

    init {
        setBackgroundColor(Color.parseColor("#0A0A14"))
        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        root.setBackgroundColor(Color.parseColor("#0A0A14"))

        val padL = (32 * density()).toInt()

        val title = TextView(context).apply {
            text = item.title
            textSize = 32f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(padL, (24 * density()).toInt(), 0, 0)
        }
        val subtitle = TextView(context).apply {
            text = listOfNotNull(item.year, item.score?.let { "★$it" }, item.typeName)
                .joinToString("  ·  ")
            textSize = 16f
            setTextColor(Color.parseColor("#8888AA"))
            setPadding(padL, 0, 0, 0)
        }
        val cover = ImageView(context).apply {
            layoutParams = LayoutParams((220 * density()).toInt(), (300 * density()).toInt(), Gravity.START or Gravity.TOP)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor("#1A1A2E"))
        }
        descBox = TextView(context).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#BBBBBB"))
            setPadding(padL, (16 * density()).toInt(), (32 * density()).toInt(), 0)
            maxLines = 4
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val sectionLabel = TextView(context).apply {
            text = "集数"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(padL, (24 * density()).toInt(), 0, (8 * density()).toInt())
        }

        epRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(padL, 0, padL, 0)
            isHorizontalFadingEdgeEnabled = false
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(cover)
        root.addView(descBox)
        root.addView(sectionLabel)
        root.addView(epRow, LayoutParams(LayoutParams.MATCH_PARENT, 0, Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM))

        loading.isIndeterminate = true
        root.addView(loading, LayoutParams((80 * density()).toInt(), (80 * density()).toInt(), Gravity.CENTER))

        isFocusable = true
        isFocusableInTouchMode = true

        load()
    }

    private fun density() = resources.displayMetrics.density

    private fun load() {
        scope.launch {
            val detail = withContext(Dispatchers.IO) {
                App.client.detail(item.source, item.id)
            }
            loading.visibility = View.GONE
            if (detail == null) {
                descBox.text = "详情加载失败"
                return@launch
            }
            descBox.text = detail.desc ?: "—"
            loadCover(detail.cover ?: item.cover)
            epList.clear()
            epList.addAll(detail.episodes)
            buildEpRows()
        }
    }

    private fun buildEpRows() {
        epRow.removeAllViews()
        epList.forEachIndexed { idx, ep ->
            val btn = TextView(context).apply {
                text = if (!ep.title.isNullOrBlank()) ep.title else "第${idx + 1}集"
                textSize = 16f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#2A2A3E"))
                setPadding((24 * density()).toInt(), (12 * density()).toInt(), (24 * density()).toInt(), (12 * density()).toInt())
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.marginEnd = (12 * density()).toInt()
                isFocusable = true
                isFocusableInTouchMode = true
                setOnFocusChangeListener { _, hasFocus ->
                    setBackgroundColor(if (hasFocus) Color.parseColor("#6366F1") else Color.parseColor("#2A2A3E"))
                }
                setOnClickListener { onPlay(ep) }
                layoutParams = lp
            }
            epRow.addView(btn)
        }
        if (epList.isEmpty()) {
            epRow.removeAllViews()
            val empty = TextView(context).apply {
                text = "暂无集数"
                textSize = 14f
                setTextColor(Color.parseColor("#8888AA"))
            }
            epRow.addView(empty)
        }
    }

    private fun loadCover(url: String?) {
        if (url.isNullOrBlank()) return
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 5000; conn.readTimeout = 8000
                    val bmp = android.graphics.BitmapFactory.decodeStream(conn.inputStream)
                    conn.disconnect()
                    if (bmp != null) {
                        withContext(Dispatchers.Main) {
                            // 封面是 root 第 3 个 view（title=0, subtitle=1, cover=2）
                            (root.getChildAt(2) as? ImageView)?.setImageBitmap(bmp)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
            return true // 返回键交给 Activity 的 onBackPressed
        }
        return super.onKeyDown(keyCode, event)
    }
}
