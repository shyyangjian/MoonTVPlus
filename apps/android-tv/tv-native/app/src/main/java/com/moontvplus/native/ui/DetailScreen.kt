package com.moontvplus.native.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.moontvplus.native.data.VideoDetail
import com.moontvplus.native.data.VideoItem
import com.moontvplus.native.util.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 详情页：海报 + 简介 + 集数列表。
 * OK 选集进播放。
 */
class DetailScreen(
    context: Context,
    private val item: VideoItem,
    private val onPlay: (VideoDetail.Episode) -> Unit
) : FrameLayout(context) {

    private val scope = CoroutineScope(Dispatchers.Main)
    private val epList = mutableListOf<VideoDetail.Episode>()
    private val listLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private lateinit var epRow: LinearLayout
    private lateinit var descBox: TextView
    private val loading = ProgressBar(context)

    init {
        setBackgroundColor(Color.parseColor("#0A0A14"))

        val root = FrameLayout(context)
        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val title = TextView(context).apply {
            text = item.title
            textSize = 32f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding((32 * density()).toInt(), (24 * density()).toInt(), 0, 0)
        }
        val subtitle = TextView(context).apply {
            text = listOfNotNull(item.year, item.score?.let { "★$it" }, item.typeName)
                .joinToString("  ·  ")
            textSize = 16f
            setTextColor(Color.parseColor("#8888AA"))
            setPadding((32 * density()).toInt(), 0, 0, 0)
        }

        val cover = ImageView(context).apply {
            layoutParams = LayoutParams((220 * density()).toInt(), (300 * density()).toInt(), Gravity.START or Gravity.TOP)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.parseColor("#1A1A2E"))
        }

        val sectionLabel = TextView(context).apply {
            text = "集数"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding((32 * density()).toInt(), (24 * density()).toInt(), 0, (8 * density()).toInt())
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(cover)
        val descBox = TextView(context).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#BBBBBB"))
            setPadding((32 * density()).toInt(), (16 * density()).toInt(), (32 * density()).toInt(), 0)
            maxLines = 5
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        root.addView(descBox)
        root.addView(sectionLabel)

        listLayout.setPadding((32 * density()).toInt(), 0, 0, 0)
        root.addView(listLayout, LayoutParams(0, 0, Gravity.START or Gravity.BOTTOM))
        // 简化：集数用横向行
        this.epRow = epRow
        root.addView(epRow, LayoutParams(LayoutParams.MATCH_PARENT, 0, Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM))

        loading.isIndeterminate = true
        root.addView(loading, LayoutParams(
            (80 * density()).toInt(), (80 * density()).toInt(),
            Gravity.CENTER
        ))

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
            if (detail == null) {
                descBox.text = "详情加载失败"
                loading.visibility = View.GONE
                return@launch
            }
            loading.visibility = View.GONE
            descBox.text = detail.desc ?: "—"
            loadCover(detail.cover ?: item.cover)

            epList.clear()
            epList.addAll(detail.episodes)
            buildEpRows()
        }
    }

    private fun buildEpRows() {
        val epRow = (getChildAt(0) as FrameLayout).getChildAt(6) as? LinearLayout ?: return
        epRow.removeAllViews()
        epList.forEachIndexed { idx, ep ->
            val btn = TextView(context).apply {
                text = if (!ep.title.isNullOrBlank()) ep.title else "第${ep.index + 1}集"
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
                            (getChildAt(0) as FrameLayout).getChildAt(2) as? ImageView
                                ?.setImageBitmap(bmp)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    override fun onKeyDown(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            // 返回：交给 Activity 处理
        }
        return super.onKeyDown(event)
    }
}
