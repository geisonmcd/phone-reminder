package com.geison.phonereminder.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/**
 * Minimal Google Drive v3 REST client that authenticates with a plain OAuth access token.
 *
 * It deliberately avoids `GoogleAccountCredential` (which resolves the account through
 * AccountManager and silently loses it on Android 8+ when the account is not visible to the app)
 * and the reflection-heavy Google API Java client (which R8 kept breaking in release builds).
 */
class DriveBackupClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val connectTimeoutMillis: Int = 20_000,
    private val readTimeoutMillis: Int = 30_000,
) {
    data class DriveFile(
        val id: String,
        val name: String,
        val modifiedTime: String?,
    )

    /** Returns the most recently modified backup file created by this app, or null. */
    fun findBackupFile(accessToken: String, fileName: String): DriveFile? {
        val query = "name = '${fileName.replace("\\", "\\\\").replace("'", "\\'")}' and 'me' in owners and trashed = false"
        val url = buildString {
            append(baseUrl.trimEnd('/'))
            append("/drive/v3/files?q=")
            append(query.urlEncode())
            append("&spaces=drive")
            append("&orderBy=")
            append("modifiedTime desc".urlEncode())
            append("&pageSize=10")
            append("&fields=")
            append("files(id,name,modifiedTime)".urlEncode())
        }
        val body = execute(accessToken, "GET", url).decodeToString()
        val files = json.parseToJsonElement(body).jsonObject["files"] as? JsonArray ?: return null
        return files.firstOrNull()?.jsonObject?.let { file ->
            DriveFile(
                id = file["id"]?.jsonPrimitive?.contentOrNull ?: return null,
                name = file["name"]?.jsonPrimitive?.contentOrNull ?: fileName,
                modifiedTime = file["modifiedTime"]?.jsonPrimitive?.contentOrNull,
            )
        }
    }

    /** Creates the backup file or overwrites the content of the existing one. Returns the file id. */
    fun uploadBackup(
        accessToken: String,
        fileName: String,
        mimeType: String,
        content: ByteArray,
    ): String {
        val existing = findBackupFile(accessToken, fileName)
        return if (existing != null) {
            val url = "${baseUrl.trimEnd('/')}/upload/drive/v3/files/${existing.id.urlEncode()}" +
                "?uploadType=media&fields=id"
            // HttpURLConnection does not support PATCH; Google APIs honour the override header.
            val response = execute(
                accessToken = accessToken,
                method = "POST",
                url = url,
                contentType = mimeType,
                body = content,
                extraHeaders = mapOf("X-HTTP-Method-Override" to "PATCH"),
            )
            parseId(response) ?: existing.id
        } else {
            val boundary = "srr-" + UUID.randomUUID().toString()
            val metadata = """{"name":${jsonString(fileName)},"mimeType":${jsonString(mimeType)}}"""
            val multipart = buildList<ByteArray> {
                add("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray())
                add(metadata.toByteArray(Charsets.UTF_8))
                add("\r\n--$boundary\r\nContent-Type: $mimeType\r\n\r\n".toByteArray())
                add(content)
                add("\r\n--$boundary--\r\n".toByteArray())
            }.reduce { acc, bytes -> acc + bytes }
            val url = "${baseUrl.trimEnd('/')}/upload/drive/v3/files?uploadType=multipart&fields=id"
            val response = execute(
                accessToken = accessToken,
                method = "POST",
                url = url,
                contentType = "multipart/related; boundary=$boundary",
                body = multipart,
            )
            parseId(response) ?: throw IOException("Google Drive did not return a file id.")
        }
    }

    fun download(accessToken: String, fileId: String): ByteArray {
        val url = "${baseUrl.trimEnd('/')}/drive/v3/files/${fileId.urlEncode()}?alt=media"
        return execute(accessToken, "GET", url)
    }

    private fun execute(
        accessToken: String,
        method: String,
        url: String,
        contentType: String? = null,
        body: ByteArray? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.useCaches = false
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Accept", "application/json")
            extraHeaders.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType ?: "application/octet-stream")
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                val errorBody = connection.errorStream?.use { it.readBytes().decodeToString() }.orEmpty()
                throw DriveApiException.fromResponse(code, errorBody)
            }
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseId(response: ByteArray): String? {
        if (response.isEmpty()) return null
        return runCatching {
            json.parseToJsonElement(response.decodeToString()).jsonObject["id"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }

    private fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")

    private fun jsonString(value: String): String = kotlinx.serialization.json.JsonPrimitive(value).toString()

    companion object {
        const val DEFAULT_BASE_URL = "https://www.googleapis.com"
        private val json = Json { ignoreUnknownKeys = true }
    }
}

/** A non-2xx response from the Drive REST API. */
class DriveApiException(
    val httpCode: Int,
    val reason: String?,
    message: String,
) : IOException(message) {

    val isUnauthorized: Boolean get() = httpCode == 401

    /** The Drive API is not enabled in the Google Cloud project that owns the OAuth client. */
    val isApiDisabled: Boolean
        get() = httpCode == 403 && reason in setOf("accessNotConfigured", "SERVICE_DISABLED")

    val isInsufficientScope: Boolean
        get() = httpCode == 403 && reason in setOf(
            "insufficientPermissions",
            "ACCESS_TOKEN_SCOPE_INSUFFICIENT",
            "insufficientScopes",
        )

    val isStorageQuotaExceeded: Boolean
        get() = httpCode == 403 && reason == "storageQuotaExceeded"

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromResponse(httpCode: Int, body: String): DriveApiException {
            var reason: String? = null
            var message: String? = null
            runCatching {
                val error = json.parseToJsonElement(body).jsonObject["error"]?.jsonObject
                message = error?.get("message")?.jsonPrimitive?.contentOrNull
                val errorsReason = (error?.get("errors") as? JsonArray)
                    ?.firstOrNull()?.jsonObject?.get("reason")?.jsonPrimitive?.contentOrNull
                val detailsReason = (error?.get("details") as? JsonArray)
                    ?.mapNotNull { (it as? JsonObject)?.get("reason")?.jsonPrimitive?.contentOrNull }
                    ?.firstOrNull()
                // Prefer the machine-readable detail reason (e.g. SERVICE_DISABLED) when present.
                reason = detailsReason ?: errorsReason
            }
            return DriveApiException(
                httpCode = httpCode,
                reason = reason,
                message = "Google Drive HTTP $httpCode" +
                    (reason?.let { " ($it)" } ?: "") +
                    (message?.let { ": $it" } ?: ""),
            )
        }
    }
}
