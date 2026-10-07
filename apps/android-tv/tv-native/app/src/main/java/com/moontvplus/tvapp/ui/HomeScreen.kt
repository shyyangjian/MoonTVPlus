package com.moontvplus.tvapp.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.moontvplus.tvapp.data.HomeSection
import com.moontvplus.tvapp.data.VideoItem
import com.moontvplus.tvapp.util.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
class HomeScreen(context: Context, private val onOpenDetail: (VideoItem) -> Unit) :
    FrameLayout(context) {

    private val rows = mutableListOf<Pair<String, RecyclerView>>()
    private val titleView = TextView(context)
    private val statusBar = TextView(context)
    private val loading = ProgressBar(context)
    private val scope = CoroutineScope(Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        setBackgroundColor(Color.parseColor("#0A0A14"))

        titleView.text = "MoonTV Plus"
        titleView.textSize = 34f
        titleView.setTextColor(Color.WHITE)
        titleView.typeface = android.graphics.Typeface.DEFAULT_BOLD
        titleView.gravity = Gravity.CENTER
        addView(titleView, LayoutParams(LayoutParams.MATCH_PARENT, (64 * density()).toInt()))

        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        loading.visibility = View.VISIBLE
        loading.isIndeterminate = true

        // 遥控：方向键在 RecyclerView 行之间切换焦点
        isFocusable = true
        isFocusableInTouchMode = true
        setOnKeyListener { _, event -> handleNav(event) }

        load()
    }

    private fun density() = resources.displayMetrics.density

    private fun load() {
        loading.visibility = View.VISIBLE
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val sections = mutableListOf<HomeSection>()
                val kinds = listOf("movie" to "热门电影", "tv" to "剧集", "anime" to "动漫")
                for ((kind, label) in kinds) {
                    try {
                        val items = App.client.douban(kind, "热门", 12)
                        if (items.isNotEmpty()) sections.add(HomeSection(label, items))
                    } catch (_: Exception) {}
                }
                sections
            }
            buildRows(result)
            loading.visibility = View.GONE
        }
    }

    private fun buildRows(sections: List<HomeSection>) {
        val list = (getChildAt(1) as LinearLayout)
        list.removeAllViews()
        rows.clear()

        if (sections.isEmpty()) {
            val empty = TextView(context).apply {
                text = "暂无数据（检查服务器配置是否有资源站）"
                setTextColor(Color.WHITE); textSize = 18f
                gravity = Gravity.CENTER
            }
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            list.addView(empty, lp)
            return
        }

        sections.forEach { s ->
            val row = buildRow(s)
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, (420 * density()).toInt())
            list.addView(row, lp)
        }
    }

    private fun buildRow(section: HomeSection): View {
        val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        val header = TextView(context).apply {
            text = section.title
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding((24 * density()).toInt(), (16 * density()).toInt(), 0, (8 * density()).toInt())
        }

        val rv = object : RecyclerView(context) {
            init {
                layoutManager = GridLayoutManager(context, 4)
                setHasFixedSize(true)
                adapter = PosterAdapter(section.items, onOpenDetail)
                isFocusable = true
                isFocusableInTouchMode = true
                overScrollMode = View.OVER_SCROLL_NEVER
            }
        }

        container.addView(header)
        container.addView(rv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        rows.add(section.title to rv)
        return container
    }

    /** 遥控方向键导航：上下在行间切换，左右交给 RecyclerView 内部 */
    private fun handleNav(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP -> {
                val idx = rows.indexOfFirst { it.second.hasFocus() || it.second.descendantFocusability == ViewGroup.FOCUS_AFTER_DESCENDANTS && it.second.isFocused }
                val target = if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    (idx + 1).coerceAtLeast(0)
                } else {
                    (idx - 1).coerceIn(0, rows.size - 1).takeIf { it >= 0 } ?: 0
                }
                if (target in rows.indices) {
                    val recycler = rows[target].second
                    recycler.requestFocus()
                    mainHandler.postDelayed({ recycler.requestFocus() }, 50)
                    return true
                }
            }
        }
        return false
    }

    /** 海报网格适配器 */
    class PosterAdapter(private val items: List<VideoItem>, private val onOpen: (VideoItem) -> Unit) :
        RecyclerView.Adapter<PosterAdapter.VH>() {

        inner class VH(val card: PosterCardView) : RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val card = PosterCardView(parent.context)
            card.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (320 * parent.resources.displayMetrics.density).toInt()
            )
            return VH(card)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.card.bind(item)
            holder.card.setOnClickListener { onOpen(item) }
            holder.card.setOnLongClickListener { true }
        }

        override fun getItemCount() = items.size
    }

    /** 海报卡片（异步加载封面） */
    class PosterCardView(context: Context) : FrameLayout(context) {
        private fun density(): Float = context.resources.displayMetrics.density
        private val cover = ImageView(context).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                (300 * context.resources.displayMetrics.density).toInt()
            )
        }
        private val info = TextView(context).apply {
            setTextColor(Color.WHITE); textSize = 14f
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 8)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        }
        private var loadedUrl: String? = null

        init {
            setPadding((16 * density()).toInt(), 0, (16 * density()).toInt(), (8 * density()).toInt())
            addView(cover, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            addView(info, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            isFocusable = true
            isFocusableInTouchMode = true
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) setBackgroundColor(Color.parseColor("#3366FFFF"))
                else setBackgroundColor(Color.parseColor("#1A1A2E"))
            }
        }

        fun bind(item: VideoItem) {
            loadedUrl = item.cover
            info.text = item.title
            loadCover(item.cover)
        }

        @SuppressLint("SetTextInLayout")
        private fun loadCover(url: String?) {
            if (url.isNullOrBlank()) return
            CoroutineScope(Dispatchers.Main).launch {
                withContext(Dispatchers.IO) {
                    try {
                        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                        conn.connectTimeout = 5000; conn.readTimeout = 8000
                        val bmp = android.graphics.BitmapFactory.decodeStream(conn.inputStream)
                        conn.disconnect()
                        if (bmp != null) {
                            withContext(Dispatchers.Main) {
                                if (loadedUrl == url) cover.setImageBitmap(bmp)
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }
}
