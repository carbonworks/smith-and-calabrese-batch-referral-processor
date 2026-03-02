package tech.carbonworks.snc.batchreferralparser.util

import java.util.prefs.Preferences

/**
 * Persistence layer for PHI display preferences.
 *
 * Stores two boolean keys in the same Java Preferences node used by the
 * application's directory preference (`tech/carbonworks/snc/batchreferralparser`):
 *
 * - `showPhiByDefault` (default `false`): when true, extracted data is shown
 *   unmasked on app launch instead of masked.
 * - `phiToggleDismissed` (default `false`): when true, the discovery cue
 *   animation on the eye toggle is permanently suppressed.
 */
object PhiPreferences {

    private const val KEY_SHOW_BY_DEFAULT = "showPhiByDefault"
    private const val KEY_TOGGLE_DISMISSED = "phiToggleDismissed"

    private val prefs: Preferences =
        Preferences.userRoot().node("tech/carbonworks/snc/batchreferralparser")

    /**
     * Returns whether extracted data should be shown unmasked by default on
     * every app launch.
     */
    fun getShowByDefault(): Boolean = prefs.getBoolean(KEY_SHOW_BY_DEFAULT, false)

    /**
     * Persist the "show extracted data by default" preference.
     */
    fun setShowByDefault(value: Boolean) {
        prefs.putBoolean(KEY_SHOW_BY_DEFAULT, value)
    }

    /**
     * Returns whether the discovery cue animation has been permanently
     * dismissed by the user.
     */
    fun getToggleDismissed(): Boolean = prefs.getBoolean(KEY_TOGGLE_DISMISSED, false)

    /**
     * Permanently dismiss the discovery cue animation.
     */
    fun setToggleDismissed(value: Boolean) {
        prefs.putBoolean(KEY_TOGGLE_DISMISSED, value)
    }
}
