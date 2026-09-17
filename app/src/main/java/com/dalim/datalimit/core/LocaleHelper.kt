package com.dalim.datalimit.core

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {

    const val LANG_EN = "en"
    const val LANG_ID = "in"

    fun resolve(context: Context): Context {
        val language = UsagePrefs(context).language
        val locale = Locale(language)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}