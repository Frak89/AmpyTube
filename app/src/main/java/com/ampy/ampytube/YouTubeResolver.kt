package com.ampy.ampytube

import android.net.Uri
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object YouTubeResolver {
    data class Source(val title:String, val uploader:String, val video:String?, val audio:String?, val thumbnail:String?)
    @Volatile private var ready = false

    fun videoId(value:String):String? {
        val input=value.trim()
        val idPattern=Regex("^[a-zA-Z0-9_-]{11}$")
        if (idPattern.matches(input)) return input
        val uri=runCatching { Uri.parse(input) }.getOrNull() ?: return null
        if (uri.scheme !in listOf("http","https")) return null
        val host=(uri.host ?: "").lowercase().removePrefix("www.").removePrefix("m.")
        val id=when(host) {
            "youtu.be" -> uri.pathSegments.firstOrNull()
            "youtube.com", "youtube-nocookie.com" -> when(uri.pathSegments.firstOrNull()) {
                "watch" -> uri.getQueryParameter("v")
                "shorts", "live", "embed" -> uri.pathSegments.getOrNull(1)
                else -> null
            }
            else -> null
        }
        return id?.takeIf { idPattern.matches(it) }
    }

    @Synchronized private fun init() {
        if(ready) return
        NewPipe.init(object: Downloader() {
            override fun execute(request:Request):Response {
                val conn=(URL(request.url()).openConnection() as HttpURLConnection).apply {
                    connectTimeout=16000
                    readTimeout=22000
                    instanceFollowRedirects=true
                    requestMethod=request.httpMethod()
                    setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36")
                    setRequestProperty("Accept-Language","it-IT,it;q=0.9,en;q=0.8")
                    request.headers().forEach { (name,values) ->
                        if(values.isNotEmpty()) setRequestProperty(name,values.joinToString(", "))
                    }
                    request.dataToSend()?.let { body ->
                        doOutput=true
                        outputStream.use { it.write(body) }
                    }
                }
                try {
                    val status=conn.responseCode
                    val body=if(request.httpMethod()=="HEAD") "" else {
                        val input=if(status>=400) conn.errorStream else conn.inputStream
                        input?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                    }
                    return Response(status, conn.responseMessage ?: "", conn.headerFields.filterKeys { it!=null }, body, conn.url.toString())
                } catch(e:Exception) {
                    throw IOException("Network/extractor error: " + e.message,e)
                } finally {
                    conn.disconnect()
                }
            }
        })
        ready=true
    }

    fun resolve(id:String):Source {
        init()
        val info=StreamInfo.getInfo("https://www.youtube.com/watch?v="+id)
        val video=info.videoStreams.filter { !it.isVideoOnly && it.content.startsWith("https://") }
            .maxByOrNull { Regex("(\\d+)").find(it.resolution)?.value?.toIntOrNull() ?: 0 }
        val audio=info.audioStreams.filter { it.content.startsWith("https://") }
            .maxByOrNull { it.averageBitrate }
        if(video==null && audio==null) throw IOException("Nessun flusso disponibile. Video protetto o estrattore da aggiornare.")
        return Source(info.name ?: "YouTube", info.uploaderName ?: "YouTube",
            video?.content, audio?.content, info.thumbnails.firstOrNull()?.url)
    }
}
