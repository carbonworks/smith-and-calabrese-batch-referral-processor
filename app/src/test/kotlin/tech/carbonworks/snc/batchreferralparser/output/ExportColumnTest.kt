package tech.carbonworks.snc.batchreferralparser.output

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import tech.carbonworks.snc.batchreferralparser.extraction.ReferralFields
import tech.carbonworks.snc.batchreferralparser.extraction.ServiceLine
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for [ExportColumn], [ExportColumnConfig], [getFieldValue], and
 * [ExportPreferences].
 */
class ExportColumnTest {

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    /** Build a fully-populated [ReferralFields] with known test values. */
    private fun sampleReferral(): ReferralFields = ReferralFields(
        firstName = "Jane",
        middleName = "M",
        lastName = "Doe",
        caseId = "CASE-001",
        authorizationNumber = "AUTH-12345",
        requestId = "REQ-999",
        dateOfIssue = "02/01/2026",
        dob = "05/15/1990",
        applicantName = "John Doe",
        appointmentDate = "03/01/2026",
        appointmentTime = "10:00 AM",
        streetAddress = "123 Main St",
        city = "Anytown",
        state = "CA",
        zipCode = "90210",
        phone = "555-123-4567",
        services = listOf(
            ServiceLine(cptCode = "96130", description = "Psych eval"),
            ServiceLine(cptCode = "96131", description = "Add'l hour"),
        ),
        providerName = "Test Provider LLC",
        specialInstructions = "Please bring medical records",
        examinerNameContact = "Dr. Smith, 555-000-1234",
        federalTaxId = "12-3456789",
        vendorNumber = "V-1234",
        caseNumberFullFooter = "FULL-CASE-001",
        assignedCode = "AC-01",
        dccNumber = "DCC-555",
    )

    // -------------------------------------------------------------------
    // Test 1: Default config produces 25 fields in expected order
    // -------------------------------------------------------------------

    @Test
    fun `default config produces 25 fields in expected order`() {
        val config = ExportColumnConfig.default()

        assertEquals(25, config.columns.size, "Default config should have 25 columns")

        // Every column should be a Field, not a Spacer
        config.columns.forEach { col ->
            assertIs<ExportColumn.Field>(col, "Default columns should all be Field instances")
        }

        // Verify order matches DEFAULT_FIELD_ORDER
        val fields = config.columns.map { it as ExportColumn.Field }
        DEFAULT_FIELD_ORDER.forEachIndexed { index, (expectedId, expectedName) ->
            assertEquals(expectedId, fields[index].fieldId, "Field ID mismatch at index $index")
            assertEquals(expectedName, fields[index].displayName, "Display name mismatch at index $index")
            assertTrue(fields[index].enabled, "Default fields should be enabled")
        }
    }

    // -------------------------------------------------------------------
    // Test 2: getFieldValue returns correct values for all 25 field IDs
    // -------------------------------------------------------------------

    @Test
    fun `getFieldValue returns correct values for all 25 field IDs`() {
        val referral = sampleReferral()

        assertEquals("Jane", referral.getFieldValue("firstName"))
        assertEquals("M", referral.getFieldValue("middleName"))
        assertEquals("Doe", referral.getFieldValue("lastName"))
        assertEquals("CASE-001", referral.getFieldValue("caseId"))
        assertEquals("AUTH-12345", referral.getFieldValue("authorizationNumber"))
        assertEquals("REQ-999", referral.getFieldValue("requestId"))
        assertEquals("02/01/2026", referral.getFieldValue("dateOfIssue"))
        assertEquals("05/15/1990", referral.getFieldValue("dob"))
        assertEquals("John Doe", referral.getFieldValue("applicantName"))
        assertEquals("03/01/2026", referral.getFieldValue("appointmentDate"))
        assertEquals("10:00 AM", referral.getFieldValue("appointmentTime"))
        assertEquals("123 Main St", referral.getFieldValue("streetAddress"))
        assertEquals("Anytown", referral.getFieldValue("city"))
        assertEquals("CA", referral.getFieldValue("state"))
        assertEquals("90210", referral.getFieldValue("zipCode"))
        assertEquals("555-123-4567", referral.getFieldValue("phone"))
        assertEquals("96130, 96131", referral.getFieldValue("services"))
        assertEquals("Test Provider LLC", referral.getFieldValue("providerName"))
        assertEquals("Please bring medical records", referral.getFieldValue("specialInstructions"))
        assertEquals("Dr. Smith, 555-000-1234", referral.getFieldValue("examinerNameContact"))
        assertEquals("12-3456789", referral.getFieldValue("federalTaxId"))
        assertEquals("V-1234", referral.getFieldValue("vendorNumber"))
        assertEquals("FULL-CASE-001", referral.getFieldValue("caseNumberFullFooter"))
        assertEquals("AC-01", referral.getFieldValue("assignedCode"))
        assertEquals("DCC-555", referral.getFieldValue("dccNumber"))
    }

    // -------------------------------------------------------------------
    // Test 3: getFieldValue returns empty string for null fields
    // -------------------------------------------------------------------

    @Test
    fun `getFieldValue returns empty string for null fields`() {
        val emptyReferral = ReferralFields()

        DEFAULT_FIELD_ORDER.forEach { (fieldId, _) ->
            assertEquals("", emptyReferral.getFieldValue(fieldId),
                "Null field '$fieldId' should return empty string")
        }
    }

    // -------------------------------------------------------------------
    // Test 4: getFieldValue returns empty string for unknown field ID
    // -------------------------------------------------------------------

    @Test
    fun `getFieldValue returns empty string for unknown field ID`() {
        val referral = sampleReferral()
        assertEquals("", referral.getFieldValue("nonExistentField"))
        assertEquals("", referral.getFieldValue(""))
    }

    // -------------------------------------------------------------------
    // Test 5: DATE_FIELD_IDS contains exactly the three date fields
    // -------------------------------------------------------------------

    @Test
    fun `DATE_FIELD_IDS contains exactly the three date fields`() {
        assertEquals(3, DATE_FIELD_IDS.size)
        assertTrue("dateOfIssue" in DATE_FIELD_IDS)
        assertTrue("dob" in DATE_FIELD_IDS)
        assertTrue("appointmentDate" in DATE_FIELD_IDS)
    }

    // -------------------------------------------------------------------
    // Test 6: ExportColumnConfig round-trips through JSON serialization
    // -------------------------------------------------------------------

    @Test
    fun `ExportColumnConfig round-trips through JSON serialization`() {
        val json = Json { encodeDefaults = true }

        val config = ExportColumnConfig(
            columns = listOf(
                ExportColumn.Field("firstName", "First Name", enabled = true),
                ExportColumn.Spacer(id = "spacer-1", label = "Gap"),
                ExportColumn.Field("lastName", "Last Name", enabled = false),
            )
        )

        val serialized = json.encodeToString(ExportColumnConfig.serializer(), config)
        val deserialized = json.decodeFromString<ExportColumnConfig>(serialized)

        assertEquals(config, deserialized, "Config should survive JSON round-trip")
        assertEquals(3, deserialized.columns.size)

        val first = deserialized.columns[0] as ExportColumn.Field
        assertEquals("firstName", first.fieldId)
        assertTrue(first.enabled)

        val spacer = deserialized.columns[1] as ExportColumn.Spacer
        assertEquals("spacer-1", spacer.id)
        assertEquals("Gap", spacer.label)

        val last = deserialized.columns[2] as ExportColumn.Field
        assertEquals("lastName", last.fieldId)
        assertEquals(false, last.enabled)
    }

    // -------------------------------------------------------------------
    // Test 7: ExportPreferences round-trips config through save/load
    // -------------------------------------------------------------------

    @Test
    fun `ExportPreferences round-trips config through save and load`() {
        val config = ExportColumnConfig(
            columns = listOf(
                ExportColumn.Field("lastName", "Last Name", enabled = true),
                ExportColumn.Spacer(id = "sp-1"),
                ExportColumn.Field("firstName", "First Name", enabled = false),
            )
        )

        try {
            ExportPreferences.save(config)
            val loaded = ExportPreferences.load()
            assertEquals(config, loaded, "Saved config should match loaded config")
        } finally {
            // Clean up preferences after test
            ExportPreferences.reset()
        }
    }

    // -------------------------------------------------------------------
    // Test 8: ExportPreferences returns default on missing key
    // -------------------------------------------------------------------

    @Test
    fun `ExportPreferences returns default when no config is saved`() {
        // Ensure clean state
        ExportPreferences.reset()

        val loaded = ExportPreferences.load()
        assertEquals(ExportColumnConfig.default(), loaded,
            "Missing config should fall back to default")
    }

    // -------------------------------------------------------------------
    // Test 9: ExportPreferences returns default on corrupt JSON
    // -------------------------------------------------------------------

    @Test
    fun `ExportPreferences returns default on corrupt JSON`() {
        // Write corrupt data directly to preferences
        val prefs = java.util.prefs.Preferences.userRoot()
            .node("tech/carbonworks/snc/batchreferralparser")
        prefs.put("exportColumnConfig", "{{not valid json!!")

        try {
            val loaded = ExportPreferences.load()
            assertEquals(ExportColumnConfig.default(), loaded,
                "Corrupt JSON should fall back to default")
        } finally {
            // Clean up
            ExportPreferences.reset()
        }
    }

    // -------------------------------------------------------------------
    // Test 10: ExportPreferences returns default on empty JSON string
    // -------------------------------------------------------------------

    @Test
    fun `ExportPreferences returns default on empty JSON string`() {
        val prefs = java.util.prefs.Preferences.userRoot()
            .node("tech/carbonworks/snc/batchreferralparser")
        prefs.put("exportColumnConfig", "")

        try {
            val loaded = ExportPreferences.load()
            assertEquals(ExportColumnConfig.default(), loaded,
                "Empty JSON should fall back to default")
        } finally {
            ExportPreferences.reset()
        }
    }

    // -------------------------------------------------------------------
    // Test 11: ExportColumnConfig with expandServices round-trips through JSON
    // -------------------------------------------------------------------

    @Test
    fun `ExportColumnConfig with expandServices round-trips through JSON`() {
        val json = Json { encodeDefaults = true }

        val config = ExportColumnConfig(
            columns = listOf(
                ExportColumn.Field("firstName", "First Name"),
                ExportColumn.Field("services", "Services"),
            ),
            expandServices = true,
        )

        val serialized = json.encodeToString(ExportColumnConfig.serializer(), config)
        val deserialized = json.decodeFromString<ExportColumnConfig>(serialized)

        assertEquals(config, deserialized, "Config with expandServices should survive JSON round-trip")
        assertTrue(deserialized.expandServices, "expandServices should be true after round-trip")
    }

    // -------------------------------------------------------------------
    // Test 12: Default config has expandServices false
    // -------------------------------------------------------------------

    @Test
    fun `default config has expandServices false`() {
        val config = ExportColumnConfig.default()
        assertEquals(false, config.expandServices, "Default config should have expandServices = false")
    }

    // -------------------------------------------------------------------
    // Test 13: Default field order matches SpreadsheetWriter COLUMN_HEADINGS
    // -------------------------------------------------------------------

    @Test
    fun `default field order display names match SpreadsheetWriter COLUMN_HEADINGS`() {
        val defaultNames = DEFAULT_FIELD_ORDER.map { it.second }
        assertEquals(
            SpreadsheetWriter.COLUMN_HEADINGS,
            defaultNames,
            "DEFAULT_FIELD_ORDER display names should match COLUMN_HEADINGS exactly",
        )
    }
}
