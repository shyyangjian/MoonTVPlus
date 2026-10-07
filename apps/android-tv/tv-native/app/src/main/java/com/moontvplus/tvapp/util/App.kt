package com.moontvplus.tvapp.util

import android.content.Context
import com.moontvplus.tvapp.data.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 全局单例 */
object App {
    lateinit var client: ApiClient
        private set
    var baseUrl: String = ""
    private lateinit var ctx: Context

    fun init(context: Context, defaultUrl: String) {
        ctx = context.applicationContext
        val prefs = ctx.getSharedPreferences("moontv", 0)
        val savedUrl = prefs.getString("base_url", defaultUrl) ?: defaultUrl
        baseUrl = savedUrl
        client = ApiClient(savedUrl)
        val token = prefs.getString("auth_token", null)
        if (token != null) client.restoreAuth(token)
        if (prefs.getBoolean("logged_in", false)) client.isLoggedIn = true
    }

    fun login(username: String?, password: String, onResult: (Boolean, String?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val ok = client.login(username, password)
            val prefs = ctx.getSharedPreferences("moontv", 0)
            if (ok) {
                prefs.edit()
                    .putString("auth_token", client.getToken())
                    .putBoolean("logged_in", true)
                    .apply()
            }
            withContext(Dispatchers.Main) {
                onResult(ok, if (ok) null else (client.loginError ?: "登录失败"))
            }
        }
    }

    fun logout() {
        client.clearAuth()
        ctx.getSharedPreferences("moontv", 0).edit()
            .remove("auth_token").putBoolean("logged_in", false).apply()
    }

    fun saveUrl(url: String) {
        ctx.getSharedPreferences("moontv", 0).edit().putString("base_url", url).apply()
        client.setBaseUrl(url)
    }
}
