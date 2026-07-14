package com.jing.sakura.compose.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import com.github.houbb.opencc4j.util.ZhConverterUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class TvLanguage(val storageValue: String) {
    Traditional("zh-Hant"),
    Simplified("zh-Hans")
}

class TvLanguagePreferences private constructor(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
    private val _language = MutableStateFlow(
        TvLanguage.entries.firstOrNull {
            it.storageValue == preferences.getString(KEY_LANGUAGE, null)
        } ?: TvLanguage.Traditional
    )
    val language: StateFlow<TvLanguage> = _language

    fun setLanguage(language: TvLanguage) {
        if (_language.value == language) return
        preferences.edit().putString(KEY_LANGUAGE, language.storageValue).apply()
        _language.value = language
    }

    companion object {
        private const val PREFERENCES_NAME = "aulama_tv_preferences"
        private const val KEY_LANGUAGE = "language"

        @Volatile
        private var instance: TvLanguagePreferences? = null

        fun get(context: Context): TvLanguagePreferences = instance ?: synchronized(this) {
            instance ?: TvLanguagePreferences(context).also { instance = it }
        }
    }
}

val LocalTvLanguage = compositionLocalOf { TvLanguage.Traditional }

object ChineseText {
    fun convert(text: String, language: TvLanguage): String {
        if (text.isBlank()) return text
        return runCatching {
            when (language) {
                TvLanguage.Traditional -> ZhConverterUtil.toTraditional(text)
                TvLanguage.Simplified -> ZhConverterUtil.toSimple(text)
            }
        }.getOrElse { text }
    }
}

@Composable
fun localizedText(text: String): String {
    val language = LocalTvLanguage.current
    return remember(text, language) { ChineseText.convert(text, language) }
}
