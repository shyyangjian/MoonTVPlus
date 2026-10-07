package com.moontvplus.tvapp.data

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

/**
 * 轻量 HTTP 客户端。
 * 登录走 POST /api/login，服务端返回 { ok:true, token:<auth cookie 值> }，
 * 后续所有请求带 "Cookie: auth=<token>" 完成鉴权。
 */
class ApiClient(private var baseUrl: String) {

    init {
        baseUrl = normalize(baseUrl)
    }

    @Volatile var isLoggedIn: Boolean = false
        private set
    @Volatile var loginError: String? = null

    /** 鉴权 token（登录成功后服务端给的 auth cookie 值） */
    @Volatile private var authToken: String? = null

    fun setBaseUrl(url: String) {
        baseUrl = normalize(url)
        isLoggedIn = false
    }

    private fun normalize(url: String): String {
        var u = url.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
        while (u.endsWith("/")) u = u.dropLast(1)
        return u
    }

    private fun buildUrl(path: String, params: Map<String, String> = emptyMap()): String {
        var full = "$baseUrl/${path.trimStart('/')}"
        if (params.isNotEmpty()) {
            val qs = params.entries.joinToString("&") {
                "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
            }
            full = full + "?" + qs
        }
        return full
    }

    /** 登录：POST /api/login，成功返回 token，后续请求带 Cookie: auth=token */
    fun login(username: String?, password: String): Boolean {
        return try {
            val url = buildUrl("api/login")
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "MoonTVNativeTV/1.0")

            val body = JSONObject().apply {
                put("password", password)
                if (!username.isNullOrBlank()) put("username", username)
            }.toString()
            conn.outputStream.write(body.toByteArray())

            val code = conn.responseCode
            val resp = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText()

            if (code in 200..299) {
                val json = JSONObject(resp)
                if (json.optBoolean("ok", false)) {
                    // 优先取 response 里的 token（服务端把它作为 auth cookie 值返回）
                    authToken = json.optString("token", "").ifEmpty {
                        // 兜底：从 Set-Cookie 头解析 auth
                        conn.getHeaderFields()["Set-Cookie"]?.firstOrNull()
                            ?.substringAfter("auth=")?.substringBefore(';') ?: ""
                    }
                    isLoggedIn = true
                    loginError = null
                } else {
                    isLoggedIn = false
                    loginError = json.optString("error", json.optString("message", "登录被拒"))
                }
            } else {
                isLoggedIn = false
                loginError = "HTTP $code: ${resp?.take(200)}"
            }
            conn.disconnect()
            isLoggedIn
        } catch (e: Exception) {
            loginError = e.message
            false
        }
    }

    fun clearAuth() {
        authToken = null
        isLoggedIn = false
    }

    /** 供 App 层持久化 token */
    fun currentAuth(): String? = authToken

    fun restoreAuth(token: String) {
        authToken = token
        isLoggedIn = true
    }

    fun markLoggedIn() {
        isLoggedIn = true
    }

    /** GET /api/search?q=... */
    fun search(keyword: String): List<VideoItem> {
        return try {
            val url = buildUrl("api/search", mapOf("q" to keyword))
            val result = httpGet(url)
            if (!result.success) return emptyList()
            val json = JSONObject(result.body)
            val results = json.optJSONArray("results") ?: return emptyList()
            parseItems(results)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 首页：按类型拉区块（对齐前端 /api/douban?type=..&tag=..&pageSize=..） */
    fun douban(type: String, tag: String, pageSize: Int = 12): List<VideoItem> {
        return try {
            val url = buildUrl("api/douban", mapOf(
                "type" to type,
                "tag" to tag,
                "pageSize" to pageSize.toString()
            ))
            val result = httpGet(url)
            if (!result.success) return emptyList()
            val trimmed = result.body.trim()
            val arr: JSONArray = if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
            } else {
                val json = JSONObject(trimmed)
                json.optJSONArray("list")
                    ?: json.optJSONArray("items")
                    ?: json.optJSONArray("results")
                    ?: JSONArray()
            }
            if (arr.length() > 0) return parseItems(arr)
            parseItems(JSONArray(result.body))
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** GET /api/source-detail?source=..&id=.. */
    fun detail(source: String, id: String): VideoDetail? {
        return try {
            val url = buildUrl("api/source-detail", mapOf("source" to source, "id" to id))
            val result = httpGet(url)
            if (!result.success) return null
            parseDetail(JSONObject(result.body))
        } catch (e: Exception) {
            null
        }
    }

    /** 解析可播放 m3u8 URL */
    fun playableM3u8Url(episodeUrl: String, source: String): String {
        val encoded = URLEncoder.encode(episodeUrl, "UTF-8")
        return "$baseUrl/api/proxy/vod/m3u8?url=$encoded&source=${URLEncoder.encode(source, "UTF-8")}"
    }

    /** 保存播放进度：POST /api/playrecords */
    fun savePlayRecord(record: PlayRecord) {
        try {
            val url = buildUrl("api/playrecords")
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "MoonTVNativeTV/1.0")
            val token = authToken
            if (!token.isNullOrBlank()) conn.setRequestProperty("Cookie", "auth=$token")
            val body = JSONObject().apply {
                put("source", record.source)
                put("id", record.id)
                put("episodeIndex", record.episodeIndex)
                put("position", record.playTimeMs)
                put("duration", record.totalMs)
                put("title", record.title)
            }.toString()
            conn.outputStream.write(body.toByteArray())
            conn.responseCode
            conn.disconnect()
        } catch (_: Exception) {}
    }

    /** 获取继续观看列表：GET /api/playrecords */
    fun getContinueWatching(): List<PlayRecord> {
        return try {
            val url = buildUrl("api/playrecords")
            val result = httpGet(url)
            if (!result.success) return emptyList()
            val trimmed = result.body.trim()
            val arr: JSONArray = if (trimmed.startsWith("[")) JSONArray(trimmed)
            else JSONObject(trimmed).optJSONArray("list") ?: JSONArray()
            val out = ArrayList<PlayRecord>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(
                    PlayRecord(
                        source = o.optString("source", ""),
                        id = o.optString("id", o.optString("vod_id", "")),
                        episodeIndex = o.optInt("episodeIndex", o.optInt("episode_index", 0)),
                        playTimeMs = o.optLong("position", o.optLong("playTimeMs", 0L)),
                        totalMs = o.optLong("duration", o.optLong("totalMs", 0L)),
                        title = o.optString("title", o.optString("vod_name", "")),
                        cover = o.optString("cover", o.optString("vod_pic", null))
                    )
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun httpGet(url: String): HttpResult {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "MoonTVNativeTV/1.0")
            conn.setRequestProperty("Accept", "application/json")
            // 关键：带上鉴权 cookie
            val token = authToken
            if (!token.isNullOrBlank()) {
                conn.setRequestProperty("Cookie", "auth=$token")
            }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText() ?: ""
            conn.disconnect()
            HttpResult(success = code in 200..299, code = code, body = body)
        } catch (e: Exception) {
            HttpResult(success = false, code = -1, body = e.message ?: "")
        }
    }

    private fun parseItems(array: JSONArray): List<VideoItem> {
        val out = ArrayList<VideoItem>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val id = o.optString("id", o.optString("vod_id", ""))
            val source = o.optString("source", o.optString("fromid", o.optString("source_key", "")))
            if (id.isEmpty() || source.isEmpty()) continue
            out.add(
                VideoItem(
                    id = id,
                    source = source,
                    title = o.optString("title", o.optString("vod_name", "")),
                    year = o.optString("year", o.optString("vod_year", "")),
                    cover = o.optString("cover", o.optString("pic", o.optString("vod_pic", ""))).ifEmpty { null },
                    desc = o.optString("desc", o.optString("content", "")),
                    type = o.optString("type", ""),
                    typeName = o.optString("type_name", ""),
                    score = o.optString("score", o.optString("douban_score", "")),
                    episodeCount = o.optInt("episode_count", -1).takeIf { it >= 0 }
                )
            )
        }
        return out
    }

    private fun parseDetail(json: JSONObject): VideoDetail? {
        val item = json.optJSONObject("item")
        val title = item?.optString("title", json.optString("title", "")) ?: json.optString("title", "")
        if (title.isEmpty()) return null
        val episodesArr = json.optJSONArray("episodes") ?: JSONArray()
        val episodes = ArrayList<VideoDetail.Episode>()
        for (i in 0 until episodesArr.length()) {
            val ep = episodesArr.optJSONObject(i) ?: continue
            val url = ep.optString("url", ep.optString("play_url", ep.optString("episodes_url", "")))
            if (url.isNotEmpty()) {
                episodes.add(VideoDetail.Episode(i, ep.optString("title", null), url))
            }
        }
        return VideoDetail(
            title = title,
            cover = item?.optString("cover", json.optString("cover", ""))?.ifEmpty { null },
            desc = item?.optString("desc", json.optString("desc", "")),
            year = item?.optString("year", json.optString("year", "")),
            episodes = episodes
        )
    }

    data class HttpResult(val success: Boolean, val code: Int, val body: String)
}
