package com.moontvplus.native.data

import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

/**
 * 轻量 HTTP 客户端。
 * - 登录走 POST /api/login，服务端种 auth cookie（Set-Cookie）
 * - 后续请求用 CookieManager 自动带 cookie，鉴权靠 cookie
 */
class ApiClient(private var baseUrl: String) {

    private val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)

    init {
        baseUrl = normalize(baseUrl)
    }

    @Volatile var isLoggedIn: Boolean = false
        private set
    @Volatile var loginError: String? = null

    fun setBaseUrl(url: String) {
        baseUrl = normalize(url)
        isLoggedIn = false
    }

    private fun normalize(url: String): String {
        var u = url.trim()
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
        while (u.endsWith("/")) u = u.dropLast()
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

    /** 登录：POST /api/login，成功则维护 cookie 供后续请求鉴权 */
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

            // 把 Set-Cookie 存进 cookieManager，后续请求自动带上
            try {
                val cookies = conn.getHeaderFields()["Set-Cookie"]
                cookies?.forEach { cookieStr ->
                    val name = cookieStr.substringBefore('=')
                    val value = cookieStr.substringAfter('=', "").substringBefore(';')
                    cookieManager.getCookiePolicy().let { _ }
                    // 简化：用内存 store 记录
                    sessionCookies["$name"] = value
                    requestCookies["$name"] = value
                }
            } catch (_: Exception) {}

            if (code in 200..299) {
                isLoggedIn = true
                loginError = null
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

    private val sessionCookies = HashMap<String, String>()
    private val requestCookies = HashMap<String, String>()

    fun clearAuth() {
        sessionCookies.clear()
        requestCookies.clear()
        isLoggedIn = false
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
            val url = buildUrl("api/douban", mapOf("type" to type, "tag" to tag, "pageSize" to pageSize.toString()))
            val result = httpGet(url)
            if (!result.success) return emptyList()
            val json = JSONObject(result.body)
            // /api/douban 直接返回数组或带 list 字段
            val arr = if (json.isNull("list")) json.optJSONArray("*") ?: JSONArray() else json.optJSONArray("list")
            if (arr.length() > 0) return parseItems(arr)
            // 兜底：整个 body 可能就是数组
            return parseItems(JSONArray(result.body))
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

    private fun httpGet(url: String): HttpResult {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "MoonTVNativeTV/1.0")
            val cookieHeader = requestCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            if (cookieHeader.isNotEmpty()) {
                conn.setRequestProperty("Cookie", cookieHeader)
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
                    year = o.optString("year", o.optString("vod_year", null)),
                    cover = o.optString("cover", o.optString("pic", o.optString("vod_pic", ""))).ifEmpty { null },
                    desc = o.optString("desc", o.optString("content", null)),
                    type = o.optString("type", null),
                    typeName = o.optString("type_name", null),
                    score = o.optString("score", o.optString("douban_score", null)),
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
            desc = item?.optString("desc", json.optString("desc", null)),
            year = item?.optString("year", json.optString("year", null)),
            episodes = episodes
        )
    }

    data class HttpResult(val success: Boolean, val code: Int, val body: String)
}
