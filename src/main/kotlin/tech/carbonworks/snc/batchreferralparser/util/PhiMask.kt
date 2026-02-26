package tech.carbonworks.snc.batchreferralparser.util

/**
 * PHI-safe masking utilities for debug/development builds.
 *
 * When masking is enabled ([isMaskingEnabled] returns true), all extracted
 * field values displayed in the UI are masked to prevent accidental PHI
 * exposure during development and testing.
 *
 * Masking rule: split on whitespace, then for each word keep the first
 * character and replace the rest with asterisks. Single-character words
 * become a single asterisk.
 *
 * Examples:
 * - `"Jane"` -> `"J***"`
 * - `"123 Main Street"` -> `"1** M*** S*****"`
 * - `"05/15/1990"` -> `"0*********"`
 */
object PhiMask {

    /**
     * Returns true when PHI masking should be applied in the UI.
     * Currently enabled for debug builds only.
     */
    fun isMaskingEnabled(): Boolean = BuildConfig.DEBUG

    /**
     * Mask a field value for safe display in the UI.
     *
     * If [text] is null, returns null. If masking is disabled, returns the
     * original text unchanged. Otherwise, splits on whitespace, masks each
     * word (first character visible, rest replaced with asterisks), and
     * rejoins with spaces.
     *
     * @param text the raw field value, or null
     * @return the masked value, or null if input was null
     */
    fun maskValue(text: String?): String? {
        if (text == null) return null
        if (!isMaskingEnabled()) return text
        if (text.isBlank()) return text

        return text.split(" ").joinToString(" ") { word ->
            maskWord(word)
        }
    }

    /**
     * Mask a non-null field value for display. Returns empty string for
     * empty input, masked value otherwise. Convenience for contexts where
     * null is not expected (e.g., after `.orEmpty()`).
     */
    fun maskDisplay(text: String): String {
        if (!isMaskingEnabled()) return text
        if (text.isEmpty()) return text

        return text.split(" ").joinToString(" ") { word ->
            maskWord(word)
        }
    }

    /**
     * Mask a single word: keep the first character, replace the rest with
     * asterisks. A single-character word becomes `*`.
     */
    private fun maskWord(word: String): String {
        if (word.isEmpty()) return word
        if (word.length == 1) return "*"
        return word[0] + "*".repeat(word.length - 1)
    }
}
