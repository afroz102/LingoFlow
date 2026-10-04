package com.lingoflow.instanttranslate.provider.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.net.HttpURLConnection
import java.net.URL

internal data class HttpResponse(val status: Int, val body: String)

internal fun interface BackendHttp {
    suspend fun translate(body: String): HttpResponse
}

/** No credentials or user identity are sent to the translation backend. */
internal class UrlConnectionHttp(private val baseUrl: String) : BackendHttp {
    override suspend fun translate(body: String): HttpResponse = runInterruptible(Dispatchers.IO) {
        val connection = URL("${baseUrl.trimEnd('/')}/v1/translate").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 8_000
            connection.readTimeout = 22_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            val bytes = body.toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 131_072) throw java.io.IOException("Response too large")
                    output.write(buffer, 0, count)
                }
                output.toString("UTF-8")
            }.orEmpty()
            HttpResponse(status, response)
        } finally { connection.disconnect() }
    }
}
