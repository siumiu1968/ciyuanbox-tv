package com.jing.sakura.player.mpv

import android.content.Context
import android.util.AttributeSet
import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.MPVLib

class CustomMPVView(context: Context, attrs: AttributeSet) : BaseMPVView(context, attrs) {

    override fun initOptions() {
        setVo("gpu")
        MPVLib.setOptionString("hwdec", "auto")
        MPVLib.setOptionString("ao", "audiotrack,opensles")
        MPVLib.setOptionString("keep-open", "yes")
        MPVLib.setOptionString("gpu-context", "android")
        MPVLib.setOptionString("keepaspect", "yes")
        MPVLib.setOptionString("video-aspect-override", "-1")
        MPVLib.setOptionString("panscan", "0.0")
        MPVLib.setOptionString("tls-verify", "no")
        MPVLib.setOptionString(
            "user-agent",
            DESKTOP_USER_AGENT
        )
        MPVLib.setOptionString("referrer", CYCANI_REFERER)
        MPVLib.setOptionString("http-header-fields", "Accept: */*")
        MPVLib.setOptionString("cache", "yes")
        MPVLib.setOptionString("cache-secs", "120")
        MPVLib.setOptionString("demuxer-max-bytes", "150M")
        MPVLib.setOptionString("demuxer-seekable-cache", "yes")
        MPVLib.setOptionString("softvol", "yes")
        MPVLib.setOptionString("volume-max", "200")
    }

    override fun postInitOptions() = Unit

    override fun observeProperties() = Unit

    private companion object {
        private const val CYCANI_REFERER = "https://www.cycani.org/"
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) cyc-desktop/1.0.8 Chrome/128.0.6613.36 Electron/32.0.1 Safari/537.36"
    }
}
