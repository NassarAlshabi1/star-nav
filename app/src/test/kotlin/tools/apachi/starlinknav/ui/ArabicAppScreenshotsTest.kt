package tools.apachi.starlinknav.ui

import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tools.apachi.starlinknav.App
import tools.apachi.starlinknav.core.dish.LocalLocationReading
import tools.apachi.starlinknav.setup.DishFindings

/**
 * The whole app in Arabic, the way a user gets it: Settings' Language choice set to العربية and
 * the activity recreated, which is exactly the flow MainActivity + AppLocale run on a phone
 * (attachBaseContext wraps the new locale for every screen, the notifications and the Quick
 * Settings tile). What lands in the image: the Position page with its cards, the Arabic bottom
 * bar, and every word set in the Cairo font, right to left.
 *
 * The map stays plain because its layer is off by default, so no MapLibre native code loads on
 * the JVM. The badge count on the Mock GPS tab depends on the setup checks the app polls, and
 * the dish probe races the capture, so the test waits for the first round and then pins the
 * no-dish findings a probe without a dish yields; see [cleanSharedState] in the other class for
 * why the shared state is emptied first. Record after an intentional change like in
 * [TranslationScreenshotsTest].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = App::class, qualifiers = "w412dp-h915dp-420dpi")
class ArabicAppScreenshotsTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun cleanSharedState() {
        SetupState.phone.value = emptyMap()
        SetupState.dish.value = null
        SetupState.googleAccuracy.value = null
        App.instance.settings.update { it.copy(languageTag = "") }
    }

    @Test
    fun wholeAppArabic() {
        // The app's own Language choice, then the recreate Settings performs on a phone.
        App.instance.settings.update { it.copy(languageTag = "ar") }
        compose.activity.recreate()
        compose.waitForIdle()
        // Let the AppRoot polling settle its first round (the phone checks come instantly, the
        // dish probe takes as long as the network lets it), then pin what a dish-less run shows.
        val deadline = System.nanoTime() + 10_000_000_000L
        while (SetupState.dish.value == null && System.nanoTime() < deadline) Thread.sleep(50)
        Thread.sleep(250) // and a margin, so a just-landing probe cannot slip past the pin
        SetupState.dish.value = DishFindings(false, LocalLocationReading.UNKNOWN, SystemClock.elapsedRealtimeNanos())
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/app_ar.png")
    }
}
