package com.geison.phonereminder.diagnostics

internal object DiagnosticBreadcrumb {
    const val MAX_VALUE_LENGTH = 120

    fun format(
        name: String,
        attributes: Map<String, String> = emptyMap(),
    ): String {
        val safeName = sanitizeToken(name)
        if (attributes.isEmpty()) {
            return safeName
        }

        val rendered = attributes.entries.joinToString(separator = " ") { (key, value) ->
            "${sanitizeToken(key)}=${value.sanitizeValue()}"
        }
        return "$safeName $rendered"
    }

    fun redactedFailure(
        area: String,
        throwable: Throwable,
    ): Throwable {
        val failure = DiagnosticFailure("${sanitizeToken(area)}:${exceptionChain(throwable)}")
        failure.stackTrace = throwable.stackTrace
        return failure
    }

    fun exceptionChain(throwable: Throwable): String {
        return generateSequence(throwable) { cause ->
            cause.cause?.takeUnless { it === cause }
        }
            .take(MAX_EXCEPTION_CHAIN)
            .map { it.javaClass.simpleName.ifBlank { "Throwable" } }
            .joinToString(">")
            .sanitizeValue()
    }

    fun sanitizeToken(raw: String): String {
        val sanitized = raw.lowercase()
            .replace(unsafeToken, "_")
            .trim('_')
        return sanitized.ifEmpty { "unknown" }.take(MAX_TOKEN_LENGTH)
    }

    fun String.sanitizeValue(): String {
        return emailAddress.replace(this, "[redacted]")
            .let { quotedText.replace(it, "\"[redacted]\"") }
            .let { localUri.replace(it, "[redacted-uri]") }
            .replace('\n', ' ')
            .replace('\r', ' ')
            .take(MAX_VALUE_LENGTH)
    }

    private val emailAddress = Regex(
        "[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
        RegexOption.IGNORE_CASE,
    )
    private val quotedText = Regex("\"(?:\\\\.|[^\"\\\\]){12,}\"")
    private val localUri = Regex("(?:content|file)://\\S+")
    private val unsafeToken = Regex("[^a-z0-9_]+")

    private const val MAX_TOKEN_LENGTH = 48
    private const val MAX_EXCEPTION_CHAIN = 8
}

internal class DiagnosticFailure(
    safeMessage: String,
) : Exception(safeMessage)
