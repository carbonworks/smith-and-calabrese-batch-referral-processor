package tech.carbonworks.snc.batchreferralparser.output

import kotlinx.serialization.json.Json
import java.util.prefs.Preferences

/**
 * Persistence layer for the user's export column configuration.
 *
 * Stores the [ExportColumnConfig] as a JSON string in the same
 * [java.util.prefs.Preferences] node used by [tech.carbonworks.snc.batchreferralparser.util.PhiPreferences]
 * (`tech/carbonworks/snc/batchreferralparser`).
 *
 * On load failure (missing key, corrupt JSON, schema mismatch) the default
 * configuration is returned silently so the application always has a valid
 * column layout.
 */
object ExportPreferences {

    private const val KEY_EXPORT_COLUMN_CONFIG = "exportColumnConfig"

    private val prefs: Preferences =
        Preferences.userRoot().node("tech/carbonworks/snc/batchreferralparser")

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Loads the persisted export column configuration.
     *
     * @return the saved [ExportColumnConfig], or [ExportColumnConfig.default] if
     *         no configuration is saved or the stored value cannot be deserialized.
     */
    fun load(): ExportColumnConfig {
        val raw = prefs.get(KEY_EXPORT_COLUMN_CONFIG, null)
        if (raw == null) {
            println("[ExportPreferences] No saved config, using defaults")
            return ExportColumnConfig.default()
        }
        return try {
            val config = json.decodeFromString<ExportColumnConfig>(raw)
            val enabledCount = config.columns.count { it is ExportColumn.Field && it.enabled }
            val totalCount = config.columns.size
            println("[ExportPreferences] Loaded config: $enabledCount/$totalCount column(s) enabled, expandServices=${config.expandServices}")
            config
        } catch (e: Exception) {
            println("[ExportPreferences] Failed to deserialize saved config, using defaults: ${e.message}")
            ExportColumnConfig.default()
        }
    }

    /**
     * Persists the given export column configuration.
     */
    fun save(config: ExportColumnConfig) {
        val enabledCount = config.columns.count { it is ExportColumn.Field && it.enabled }
        println("[ExportPreferences] Saving config: $enabledCount/${config.columns.size} column(s) enabled, expandServices=${config.expandServices}")
        val raw = json.encodeToString(ExportColumnConfig.serializer(), config)
        prefs.put(KEY_EXPORT_COLUMN_CONFIG, raw)
    }

    /**
     * Removes the persisted configuration. The next call to [load] will return
     * [ExportColumnConfig.default].
     */
    fun reset() {
        println("[ExportPreferences] Reset to defaults")
        prefs.remove(KEY_EXPORT_COLUMN_CONFIG)
    }
}
