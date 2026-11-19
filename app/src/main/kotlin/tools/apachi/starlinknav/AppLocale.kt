package tools.apachi.starlinknav

import android.content.Context
import android.content.res.Configuration

/**
 * The app's own language choice ([SettingsSnapshot.languageTag]): "" follows the system locale (and Android 13's
 * per-app language, which the system then applies on its own), any other tag ("ar") overrides the locale of every
 * place the app draws words: the screens, the notifications and the Quick Settings tile.
 *
 * The components that show text wrap their base context in [wrap] from `attachBaseContext`; a change of the setting
 * recreates the activity, and the service and tile re-read it with every start (they are short-lived, and their
 * notifications are rebuilt on every update anyway).
 */
object AppLocale {
    /** The base context localized to the chosen language, or the base context itself when it follows the system. */
    fun wrap(base: Context): Context {
        val tag = App.instance.settings.value.languageTag
        if (tag.isEmpty()) return base
        val locales = android.os.LocaleList.forLanguageTags(tag)
        if (locales.isEmpty) return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(locales.get(0))
        config.setLocales(locales)
        return base.createConfigurationContext(config)
    }
}
