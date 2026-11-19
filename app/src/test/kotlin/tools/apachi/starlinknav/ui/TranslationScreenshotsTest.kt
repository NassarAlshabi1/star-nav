package tools.apachi.starlinknav.ui

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tools.apachi.starlinknav.App
import java.util.Locale

/**
 * Screenshots of the app's two busiest pages (Mock GPS and Settings) in every language the app
 * ships: English, Arabic and Ukrainian, light and dark. They run on the JVM (Robolectric Native
 * Graphics) in a plain activity, so CI compares every change against the recorded images in
 * src/test/screenshots: a layout that breaks with Cairo's wider Arabic letters, a string that
 * stops fitting its card, or a screen that slips back to untranslated text fails the build.
 *
 * The pages are drawn in a context localized the same way AppLocale localizes the real app
 * (Settings' Language choice), so the Arabic run exercises the Cairo typography: AppTheme swaps
 * the Material font family for Cairo when the locale is Arabic, and Robolectric loads the real
 * res/font files.
 *
 * To record after an intentional change: delete the PNGs and run
 * `./gradlew :app:testFossDebugUnitTest --tests "tools.apachi.starlinknav.ui.*ScreenshotsTest"`,
 * then commit the new images.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = App::class, qualifiers = "w412dp-h915dp-420dpi")
class TranslationScreenshotsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun cleanSharedState() {
        // SetupState outlives a test (it hangs off the class loader Robolectric reuses), and the
        // whole-app test's MainActivity polling fills it: empty it so the checklist renders the
        // same no-matter-what-run-order baseline every time.
        SetupState.phone.value = emptyMap()
        SetupState.dish.value = null
        SetupState.googleAccuracy.value = null
        App.instance.settings.update { it.copy(languageTag = "") }
    }

    /** Draws [content] as the app would on a phone set to [languageTag]. */
    private fun page(name: String, languageTag: String, dark: Boolean = false, content: @Composable () -> Unit) {
        val base: Context = ApplicationProvider.getApplicationContext<Application>()
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(languageTag))
        config.setLocales(LocaleList.forLanguageTags(languageTag))
        val localized = base.createConfigurationContext(config)
        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides localized.resources.configuration,
                // The localized context is not an Activity, but the screens' permission launcher needs
                // an ActivityResultRegistry: hand it the rule's own activity, like the real app's.
                LocalActivityResultRegistryOwner provides compose.activity,
            ) {
                AppTheme(dark = dark) { content() }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun mockGpsEnglish() = page("mock_gps_en", "en-US") { MockScreen() }

    @Test
    fun mockGpsArabic() = page("mock_gps_ar", "ar") { MockScreen() }

    @Test
    fun mockGpsUkrainian() = page("mock_gps_uk", "uk") { MockScreen() }

    @Test
    fun settingsEnglish() = page("settings_en", "en-US") { SettingsScreen() }

    @Test
    fun settingsArabic() = page("settings_ar", "ar") { SettingsScreen() }

    @Test
    fun settingsUkrainian() = page("settings_uk", "uk") { SettingsScreen() }

    /** The Cairo letters in the dark scheme, where the colours of the cards invert. */
    @Test
    fun settingsArabicDark() = page("settings_ar_dark", "ar", dark = true) { SettingsScreen() }
}
