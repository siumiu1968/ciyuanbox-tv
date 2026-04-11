package com.jing.sakura.extend

import com.github.houbb.opencc4j.util.ZhConverterUtil

object TraditionalChinese {

    fun convert(text: String): String {
        if (text.isBlank()) return text
        return runCatching {
            ZhConverterUtil.toTraditional(text)
        }.getOrElse { text }
    }
}
