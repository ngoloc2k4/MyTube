package vn.lobie.mytube.data.remote.newpipe

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException

class NewPipeDownloader(
    private val client: OkHttpClient = OkHttpClient()
) : Downloader() {

    @Throws(IOException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBody = if (dataToSend != null && dataToSend.isNotEmpty()) {
            dataToSend.toRequestBody()
        } else if (httpMethod.equals("POST", ignoreCase = true)) {
            ByteArray(0).toRequestBody()
        } else {
            null
        }

        val requestBuilder = okhttp3.Request.Builder()
            .url(url)
            .method(httpMethod, requestBody)

        headers?.forEach { (name, values) ->
            values.forEach { value ->
                requestBuilder.addHeader(name, value)
            }
        }

        val response = client.newCall(requestBuilder.build()).execute()
        return response.use { resp ->
            val responseBody = resp.body?.string().orEmpty()
            val responseHeaders = resp.headers.toMultimap()

            Response(
                resp.code,
                resp.message,
                responseHeaders,
                responseBody,
                resp.request.url.toString()
            )
        }
    }
}
