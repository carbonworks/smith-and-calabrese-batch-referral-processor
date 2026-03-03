package tech.carbonworks.snc.batchreferralparser

/**
 * Build-time feature flags.
 *
 * Each flag is a [const val] Boolean. The Kotlin compiler inlines these constants
 * and eliminates dead code behind disabled flags. To enable a feature, change its
 * value to `true` and commit — no runtime configuration or build variants needed.
 */
object FeatureFlags {
    /** Enable configurable export columns on the Settings screen (WP-35 through WP-37). */
    const val EXPORT_COLUMN_CONFIG = true
}
