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
        val raw = prefs.get(KEY_EXPORT_COLUMN_CONFIG, null) ?: return ExportColumnConfig.default()
        return try {
            json.decodeFromString<ExportColumnConfig>(raw)
        } catch (_: Exception) {
            ExportColumnConfig.default()
        }
    }

    /**
     * Persists the given export column configuration.
     */
    fun save(config: ExportColumnConfig) {
        val raw = json.encodeToString(ExportColumnConfig.serializer(), config)
        prefs.put(KEY_EXPORT_COLUMN_CONFIG, raw)
    }

    /**
     * Removes the persisted configuration. The next call to [load] will return
     * [ExportColumnConfig.default].
     */
    fun reset() {
        prefs.remove(KEY_EXPORT_COLUMN_CONFIG)
    }
}
