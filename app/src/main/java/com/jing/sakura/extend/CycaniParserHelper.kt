package com.jing.sakura.extend

import okhttp3.Response

object CycaniParserHelper : WebViewCookieHelper() {

    override val cookieName: String
        get() = "p_uv_id"

    override val notice: String?
        get() = null

    override val timeoutSeconds: Long
        get() = 20

    override fun shouldIntercept(response: Response): Boolean {
        return response.code == 403 &&
            response.request.url.host == "player.cycanime.com" &&
            response.header("server")?.contains("openresty", ignoreCase = true) == true
    }
}
