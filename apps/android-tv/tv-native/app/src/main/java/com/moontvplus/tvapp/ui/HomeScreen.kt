package com.moontvplus.tvapp.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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

/**
 * 首页：多行海报网格。遥控方向键由系统焦点框架驱动（不手动拦截）。
 * - 每行 = 一个类型（电影/剧集/动漫）
 * - 行内 4 列海报网格
 * - 加载失败会显示可读原因（不再是无提示的"暂无数据"）
 */
class HomeScreen(context: Context, private val onOpenDetail: (VideoItem) -> Unit) :
    FrameLayout(context) {

    private val rows = mutableListOf<Pair<String, RecyclerView>>()
    private val titleView = TextView(context)
    private val statusView = TextView(context)
    private val loading = ProgressBar(context)
    private val listLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val scope = CoroutineScope(Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        setBackgroundColor(Color.parseColor("#0A0A14"))
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        isFocusableInTouchMode = false

        titleView.text = "MoonTV Plus"
        titleView.textSize = 34f
        titleView.setTextColor(Color.WHITE)
        titleView.gravity = Gravity.CENTER
        titleView.isFocusable = false
        addView(titleView, LayoutParams(LayoutParams.MATCH_PARENT, (64 * density()).toInt()))

        statusView.text = "正在加载首页…"
        statusView.textSize = 18f
        statusView.setTextColor(Color.parseColor("#AAAAFF"))
        statusView.gravity = Gravity.CENTER
        statusView.isFocusable = true
        addView(statusView, LayoutParams(LayoutParams.MATCH_PARENT, (48 * density()).toInt(), Gravity.CENTER))

        listLayout.setPadding((24 * density()).toInt(), 0, (24 * density()).toInt(), (24 * density()).toInt())
        addView(listLayout, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.BOTTOM))

        loading.isIndeterminate = true
        loading.isFocusable = false
        addView(loading, LayoutParams((72 * density()).toInt(), (72 * density()).toInt(), Gravity.CENTER))

        load()
    }

    private fun density() = resources.displayMetrics.density

    private fun load() {
        loading.visibility = View.VISIBLE
        scope.launch {
            val (sections, error, needReauth) = withContext(Dispatchers.IO) {
                val list = mutableListOf<HomeSection>()
                var err: String? = null
                var reauth = false
                // /api/douban 只支持 type=movie / type=tv（传其他值会 400）
                val kinds = listOf("movie" to "热门电影", "tv" to "剧集")
                for ((kind, label) in kinds) {
                    val (items, e, r) = App.client.doubanSafe(kind, "热门", 12)
                    if (r) reauth = true
                    err = e ?: err
                    if (items.isNotEmpty()) list.add(HomeSection(label, items))
                }
                Triple(list, err, reauth)
            }
            loading.visibility = View.GONE
            if (needReauth) {
                statusView.text = "登录已失效，请重新登录"
                statusView.setTextColor(Color.parseColor("#FF6B6B"))
                statusView.visibility = View.VISIBLE
                mainHandler.postDelayed({
                    if (statusView.visibility == View.VISIBLE) {
                        App.logout()
                    }
                }, 2000L)
                return@launch
            }
            if (sections.isEmpty()) {
                statusView.text = error ?: "暂无数据（后台请至少配置一个影视源 / 豆瓣源）"
                statusView.setTextColor(if (error != null) Color.parseColor("#FF6B6B") else Color.parseColor("#AAAAFF"))
                statusView.visibility = View.VISIBLE
                return@launch
            }
            statusView.visibility = View.GONE
            buildRows(sections)
            mainHandler.postDelayed({
                firstFocusable()?.requestFocus()
            }, 120L)
        }
    }

    private fun buildRows(sections: List<HomeSection>) {
        listLayout.removeAllViews()
        rows.clear()
        sections.forEach { s ->
            val row = buildRow(s)
            listLayout.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, (420 * density()).toInt()))
        }
    }

    private fun buildRow(section: HomeSection): View {
        val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        val header = TextView(context).apply {
            text = section.title
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, (16 * density()).toInt(), 0, (8 * density()).toInt())
            isFocusable = false
        }

        val rv = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, 4)
            setHasFixedSize(false)
            adapter = PosterAdapter(section.items, onOpenDetail)
            overScrollMode = View.OVER_SCROLL_NEVER
            isFocusable = true
            isFocusableInTouchMode = false
            // 行内焦点：进 to 网格第一个 item，再靠系统左右上下移动
            isFocusable = true
        }

        container.addView(header, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        container.addView(rv, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (320 * density()).toInt()
        ))
        rows.add(section.title to rv)
        return container
    }

    /** 找第一个可请求焦点的 View（首页首行首卡片） */
    private fun firstFocusable(): View? {
        return if (rows.isNotEmpty()) {
            rows[0].second
        } else statusView
    }

    /** 海报网格适配器 */
    class PosterAdapter(private val items: List<VideoItem>, private val onOpen: (VideoItem) -> Unit) :
        RecyclerView.Adapter<PosterAdapter.VH>() {

        inner class VH(val card: PosterCardView) : RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val card = PosterCardView(parent.context)
            card.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (300 * parent.resources.displayMetrics.density).toInt()
            )
            return VH(card)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.card.bind(item)
            holder.card.setOnClickListener { onOpen(item) }
            holder.card.setOnLongClickListener { onOpen(item); true }
        }

        override fun getItemCount() = items.size
    }

    /** 海报卡片（异步加载封面 + 焦点高亮） */
    class PosterCardView(context: Context) : FrameLayout(context) {
        private fun density(): Float = context.resources.displayMetrics.density
        private val cover = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            adjustViewBounds = false
        }
        private val info = TextView(context).apply {
            setTextColor(Color.WHITE); textSize = 13f
            gravity = Gravity.CENTER
            setPadding(6, 6, 6, 6)
            maxLines = 2
        }
        private var loadedUrl: String? = null

        init {
            val pad = (12 * density()).toInt()
            setPadding(pad, pad, pad, pad)
            addView(cover, LayoutParams(LayoutParams.MATCH_PARENT, (260 * density()).toInt(), Gravity.TOP))
            addView(info, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            isFocusable = true
            isFocusableInTouchMode = false
            setOnFocusChangeListener { _, hasFocus ->
                setBackgroundColor(if (hasFocus) Color.parseColor("#4488FFFF") else Color.parseColor("#1A1A2E"))
                val params = layoutParams
                val scale = if (hasFocus) 1.08f else 1.0f
                animate().scaleX(scale).scaleY(scale).setDuration(120).start()
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
