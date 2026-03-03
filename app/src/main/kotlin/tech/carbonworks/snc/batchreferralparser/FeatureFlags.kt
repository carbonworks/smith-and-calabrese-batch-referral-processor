package tech.carbonworks.snc.batchreferralparser

/**
 * Compile-time feature flags for gating in-progress features.
 *
 * Set a flag to `true` to enable the feature during development;
 * flip it to `false` (or remove the gate) before release.
 */
object FeatureFlags {
    /** Enable configurable export column ordering and visibility. */
    const val EXPORT_COLUMN_CONFIG: Boolean = false
}
