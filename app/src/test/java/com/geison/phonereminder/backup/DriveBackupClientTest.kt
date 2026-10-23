package com.geison.phonereminder.backup

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.URLDecoder

class DriveBackupClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: DriveBackupClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = DriveBackupClient(baseUrl = server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun uploadCreatesMultipartFileWhenNoBackupExists() {
        server.enqueue(json("""{"files":[]}"""))
        server.enqueue(json("""{"id":"new-id"}"""))

        val id = client.uploadBackup("token-1", "Backup.txt", "text/plain", "hello".toByteArray())

        assertEquals("new-id", id)
        val list = server.takeRequest()
        assertEquals("GET", list.method)
        assertEquals("Bearer token-1", list.getHeader("Authorization"))
        val query = URLDecoder.decode(list.requestUrl!!.queryParameter("q"), "UTF-8")
        assertTrue(query, query.contains("name = 'Backup.txt'"))
        assertTrue(query, query.contains("trashed = false"))
        assertEquals("modifiedTime desc", list.requestUrl!!.queryParameter("orderBy"))

        val create = server.takeRequest()
        assertEquals("POST", create.method)
        assertEquals("/upload/drive/v3/files", create.requestUrl!!.encodedPath)
        assertEquals("multipart", create.requestUrl!!.queryParameter("uploadType"))
        assertTrue(create.getHeader("Content-Type")!!.startsWith("multipart/related; boundary="))
        val body = create.body.readUtf8()
        assertTrue(body, body.contains(""""name":"Backup.txt""""))
        assertTrue(body, body.contains("hello"))
    }

    @Test
    fun uploadOverwritesExistingBackupWithPatchOverride() {
        server.enqueue(json("""{"files":[{"id":"abc","name":"Backup.txt","modifiedTime":"2026-10-01T10:00:00Z"}]}"""))
        server.enqueue(json("""{"id":"abc"}"""))

        val id = client.uploadBackup("t", "Backup.txt", "text/plain", "v2".toByteArray())

        assertEquals("abc", id)
        server.takeRequest()
        val update = server.takeRequest()
        assertEquals("POST", update.method)
        assertEquals("PATCH", update.getHeader("X-HTTP-Method-Override"))
        assertEquals("/upload/drive/v3/files/abc", update.requestUrl!!.encodedPath)
        assertEquals("media", update.requestUrl!!.queryParameter("uploadType"))
        assertEquals("v2", update.body.readUtf8())
    }

    @Test
    fun downloadReturnsMediaBytes() {
        server.enqueue(MockResponse().setBody("Smart Random Reminder export"))

        val bytes = client.download("t", "abc")

        assertEquals("Smart Random Reminder export", bytes.decodeToString())
        val request = server.takeRequest()
        assertEquals("/drive/v3/files/abc", request.requestUrl!!.encodedPath)
        assertEquals("media", request.requestUrl!!.queryParameter("alt"))
    }

    @Test
    fun findReturnsNullWhenNoFiles() {
        server.enqueue(json("""{"files":[]}"""))
        assertNull(client.findBackupFile("t", "Backup.txt"))
    }

    @Test
    fun driveApiDisabledErrorIsRecognised() {
        server.enqueue(
            json(
                """
                {"error":{"code":403,"message":"Google Drive API has not been used in project 123 before or it is disabled.",
                "errors":[{"reason":"accessNotConfigured"}],"status":"PERMISSION_DENIED",
                "details":[{"@type":"type.googleapis.com/google.rpc.ErrorInfo","reason":"SERVICE_DISABLED"}]}}
                """.trimIndent(),
            ).setResponseCode(403),
        )

        try {
            client.findBackupFile("t", "Backup.txt")
            fail("Expected DriveApiException")
        } catch (error: DriveApiException) {
            assertEquals(403, error.httpCode)
            assertTrue(error.isApiDisabled)
            assertEquals(GoogleDriveFailure.DRIVE_API_DISABLED, GoogleDriveFailure.classify(error))
        }
    }

    @Test
    fun unauthorizedErrorIsRecognised() {
        server.enqueue(json("""{"error":{"code":401,"message":"Invalid Credentials","errors":[{"reason":"authError"}]}}""").setResponseCode(401))

        try {
            client.download("t", "abc")
            fail("Expected DriveApiException")
        } catch (error: DriveApiException) {
            assertTrue(error.isUnauthorized)
        }
    }

    private fun json(body: String) = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
