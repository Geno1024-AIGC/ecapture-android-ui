package com.geno1024.aigc.ecaptureui.ecapture

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

@Serializable
data class GitHubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    val size: Long = 0,
)

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val assets: List<GitHubAsset> = emptyList(),
)

class ReleaseClient(
    private val http: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun latest(repoApiUrl: String): GitHubRelease = withContext(Dispatchers.IO) {
        val api = repoApiUrl.trimEnd('/')
            .replace("https://github.com/", "https://api.github.com/repos/")
            .let { if (it.contains("/releases")) it.substringBefore("/releases") + "/releases/latest" else "$it/releases/latest" }
        val req = Request.Builder().url(api).header("Accept", "application/vnd.github+json").build()
        http.newCall(req).execute().use { resp ->
            check(resp.isSuccessful) { "GitHub API ${resp.code}: ${resp.message}" }
            json.decodeFromString(GitHubRelease.serializer(), resp.body.string())
        }
    }

    suspend fun download(url: String, target: File, onProgress: (Long, Long) -> Unit = { _, _ -> }) = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        val req = Request.Builder().url(url).build()
        http.newCall(req).execute().use { resp ->
            check(resp.isSuccessful) { "Download ${resp.code}: ${resp.message}" }
            val body = resp.body
            val total = body.contentLength()
            FileOutputStream(target).use { out ->
                val buf = ByteArray(8192)
                var readTotal = 0L
                body.byteStream().use { input ->
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        readTotal += n
                        onProgress(readTotal, total)
                    }
                }
            }
        }
    }

    fun suggestAsset(assets: List<GitHubAsset>, abi: String): GitHubAsset? {
        val a = abi.lowercase()
        val archHints = when {
            "arm64" in a || "aarch64" in a -> listOf("android-arm64", "arm64", "aarch64")
            "armeabi-v7a" in a || "armv7" in a -> listOf("android-arm", "armv7", "arm")
            "x86_64" in a -> listOf("android-amd64", "amd64", "x86_64")
            else -> listOf(a)
        }
        return assets.firstOrNull { asset ->
            val n = asset.name.lowercase()
            "android" in n && archHints.any { it in n }
        } ?: assets.firstOrNull { asset ->
            val n = asset.name.lowercase()
            archHints.any { it in n } && !n.endsWith(".txt") && !n.endsWith(".sha256")
        }
    }
}
