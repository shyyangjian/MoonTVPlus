package com.moontvplus.tvapp.data

/** 搜索结果/列表项（对齐后端 /api/search 的 results[] 字段） */
data class VideoItem(
    val id: String,
    val source: String,
    val title: String,
    val year: String?,
    val cover: String?,
    val desc: String?,
    val type: String?,
    val typeName: String?,
    val score: String?,
    val episodeCount: Int?
) {
    val fullKey: String get() = "$source+$id"
}

/** 详情（对齐 /api/source-detail 返回） */
data class VideoDetail(
    val title: String,
    val cover: String?,
    val desc: String?,
    val year: String?,
    val episodes: List<Episode>
) {
    data class Episode(val index: Int, val title: String?, val url: String)
}

/** 首页区块 */
data class HomeSection(val title: String, val items: List<VideoItem>)

/** 播放进度 */
data class PlayRecord(val source: String, val id: String, val episodeIndex: Int, val playTimeMs: Long, val totalMs: Long, val title: String, val cover: String?)
