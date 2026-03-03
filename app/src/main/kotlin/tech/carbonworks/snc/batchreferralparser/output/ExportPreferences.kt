package tech.carbonworks.snc.batchreferralparser.output

import java.util.prefs.Preferences

/**
 * Persists and loads the user's export column configuration via Java Preferences.
 *
 * When no saved configuration exists, [load] returns [ExportColumnConfig.default].
 */
object ExportPreferences {

    private val prefs: Preferences =
        Preferences.userRoot().node("tech/carbonworks/snc/batchreferralparser/export")

    private const val KEY_COLUMN_CONFIG = "columnConfig"

    /**
     * Loads the saved column configuration, or the default if none is saved.
     */
    fun load(): ExportColumnConfig {
        val saved = prefs.get(KEY_COLUMN_CONFIG, null) ?: return ExportColumnConfig.default()
        return try {
            deserialize(saved)
        } catch (_: Exception) {
            ExportColumnConfig.default()
        }
    }

    /**
     * Saves the given column configuration to preferences.
     */
    fun save(config: ExportColumnConfig) {
        prefs.put(KEY_COLUMN_CONFIG, serialize(config))
        prefs.flush()
    }

    /**
     * Clears any saved configuration, reverting to default on next [load].
     */
    fun clear() {
        prefs.remove(KEY_COLUMN_CONFIG)
        prefs.flush()
    }

    // Simple pipe-delimited serialization: fieldId|displayName|enabled|isSpacer per line
    private fun serialize(config: ExportColumnConfig): String {
        return config.columns.joinToString("\n") { col ->
            "${col.fieldId ?: ""}|${col.displayName}|${col.enabled}|${col.isSpacer}"
        }
    }

    private fun deserialize(data: String): ExportColumnConfig {
        val columns = data.lines().filter { it.isNotBlank() }.map { line ->
            val parts = line.split("|", limit = 4)
            ExportColumn(
                fieldId = parts[0].ifEmpty { null },
                displayName = parts[1],
                enabled = parts[2].toBoolean(),
                isSpacer = parts[3].toBoolean(),
            )
        }
        return ExportColumnConfig(columns)
    }
}
