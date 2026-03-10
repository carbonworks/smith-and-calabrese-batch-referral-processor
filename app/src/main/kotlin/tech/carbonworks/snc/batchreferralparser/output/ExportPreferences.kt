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
    private const val KEY_EXPORT_FORMAT = "exportFormat"
    /** Legacy key retained for backward-compat migration from older versions. */
    private const val KEY_EXPORT_AS_CSV = "exportAsCsv"

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
            val reconciled = reconcile(config)
            val enabledCount = reconciled.columns.count { it is ExportColumn.Field && it.enabled }
            val totalCount = reconciled.columns.size
            println("[ExportPreferences] Loaded config: $enabledCount/$totalCount column(s) enabled, expandServices=${reconciled.expandServices}")
            reconciled
        } catch (e: Exception) {
            println("[ExportPreferences] Failed to deserialize saved config, using defaults: ${e.message}")
            ExportColumnConfig.default()
        }
    }

    /**
     * Reconciles a deserialized config against the current [DEFAULT_FIELD_ORDER].
     *
     * - Appends any fields present in [DEFAULT_FIELD_ORDER] but missing from the
     *   saved config (new fields added in a later app version).
     * - Removes any [ExportColumn.Field] entries whose `fieldId` is not in
     *   [DEFAULT_FIELD_ORDER] (stale fields from a removed version).
     * - Preserves all [ExportColumn.Spacer] entries unchanged.
     * - Auto-saves the migrated config so reconciliation only runs once.
     */
    private fun reconcile(config: ExportColumnConfig): ExportColumnConfig {
        val knownFieldIds = DEFAULT_FIELD_ORDER.map { it.first }.toSet()
        val savedFieldIds = config.columns
            .filterIsInstance<ExportColumn.Field>()
            .map { it.fieldId }
            .toSet()

        // Fields in DEFAULT_FIELD_ORDER missing from saved config
        val newFields = DEFAULT_FIELD_ORDER
            .filter { (fieldId, _) -> fieldId !in savedFieldIds }
            .map { (fieldId, displayName) ->
                ExportColumn.Field(fieldId = fieldId, displayName = displayName, enabled = true)
            }

        // Remove stale Field entries not in DEFAULT_FIELD_ORDER; keep Spacers
        val prunedColumns = config.columns.filter { col ->
            when (col) {
                is ExportColumn.Field -> col.fieldId in knownFieldIds
                is ExportColumn.Spacer -> true
            }
        }
        val staleCount = config.columns.count { it is ExportColumn.Field && (it as ExportColumn.Field).fieldId !in knownFieldIds }

        if (newFields.isEmpty() && staleCount == 0) {
            return config
        }

        val migratedConfig = config.copy(columns = prunedColumns + newFields)
        println("[ExportPreferences] Migrated config: added ${newFields.size} new field(s), removed $staleCount stale field(s)")
        save(migratedConfig)
        return migratedConfig
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

    /**
     * Returns the persisted export format.
     *
     * For backward compatibility with pre-1.2 installs: if no format key is
     * stored but the legacy [KEY_EXPORT_AS_CSV] boolean is `true`, returns
     * [ExportFormat.CSV]. Otherwise defaults to [ExportFormat.XLSX].
     *
     * @return the user's chosen [ExportFormat]
     */
    fun getExportFormat(): ExportFormat {
        val raw = prefs.get(KEY_EXPORT_FORMAT, null)
        if (raw != null) {
            return try {
                ExportFormat.valueOf(raw)
            } catch (_: IllegalArgumentException) {
                ExportFormat.XLSX
            }
        }
        // Backward-compat: migrate old boolean preference
        return if (prefs.getBoolean(KEY_EXPORT_AS_CSV, false)) {
            ExportFormat.CSV
        } else {
            ExportFormat.XLSX
        }
    }

    /**
     * Persist the export format preference.
     */
    fun setExportFormat(format: ExportFormat) {
        println("[ExportPreferences] Export format changed to: ${format.name}")
        prefs.put(KEY_EXPORT_FORMAT, format.name)
    }
}
