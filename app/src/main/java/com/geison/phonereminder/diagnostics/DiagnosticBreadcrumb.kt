package com.geison.phonereminder.diagnostics

internal object DiagnosticBreadcrumb {
    const val MAX_VALUE_LENGTH = 120

    fun format(
        name: String,
        attributes: Map<String, String> = emptyMap(),
    ): String {
        val safeName = name.sanitizeToken()
        if (attributes.isEmpty()) {
            return safeName
        }

        val rendered = attributes.entries.joinToString(separator = " ") { (key, value) ->
            "${key.sanitizeToken()}=${value.sanitizeValue()}"
        }
        return "$safeName $rendered"
    }

    fun String.sanitizeValue(): String {
        return replace('\n', ' ')
            .replace('\r', ' ')
            .take(MAX_VALUE_LENGTH)
    }

    private fun String.sanitizeToken(): String {
        val sanitized = lowercase()
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
        return sanitized.ifEmpty { "unknown" }.take(MAX_TOKEN_LENGTH)
    }

    private const val MAX_TOKEN_LENGTH = 48
}
