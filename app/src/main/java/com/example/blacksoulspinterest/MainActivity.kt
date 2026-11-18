package com.example.blacksoulspinterest

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var syncer: RepositoryAssetSync

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        syncer = RepositoryAssetSync(filesDir)
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .addPathHandler("/repo/", WebViewAssetLoader.InternalStoragePathHandler(this, filesDir))
            .build()

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.loadsImagesAutomatically = true
            webChromeClient = WebChromeClient()
            addJavascriptInterface(PageBridge(), "Android")
            webViewClient = object : WebViewClientCompat() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest
                ) = assetLoader.shouldInterceptRequest(request.url)
            }
        }
        setContentView(webView)
        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html")
        lifecycleScope.launch {
            val alreadySynced = withContext(Dispatchers.IO) {
                File(filesDir, "repo/gallery.json").exists()
            }
            if (!alreadySynced) startSync()
        }
    }

    private inner class PageBridge {
        @JavascriptInterface fun syncNow() = runOnUiThread { startSync() }
        @JavascriptInterface fun pageReady() {
            lifecycleScope.launch {
                val json = withContext(Dispatchers.IO) { syncer.readGalleryJson() }
                webView.evaluateJavascript("window.setGalleryData($json)", null)
            }
        }
    }

    private fun startSync() {
        lifecycleScope.launch {
            setStatus("Menghubungkan ke GitGud…")
            try {
                val result = withContext(Dispatchers.IO) { syncer.sync { done, total, name ->
                    runOnUiThread {
                        val safe = JSONObject.quote(name)
                        webView.evaluateJavascript(
                            "window.setSyncProgress($done,$total,$safe)", null
                        )
                    }
                } }
                webView.evaluateJavascript("window.setGalleryData($result)", null)
                setStatus("Selesai • ${JSONArray(result).length()} gambar")
            } catch (e: Exception) {
                setStatus("Gagal mengambil aset: ${e.message ?: "Periksa koneksi"}")
            }
        }
    }

    private fun setStatus(message: String) {
        webView.evaluateJavascript("window.setStatus(${JSONObject.quote(message)})", null)
    }

    override fun onDestroy() {
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }
}

private class RepositoryAssetSync(private val appFiles: File) {
    private val project = "Astrologeos%2Fblack_souls"
    private val repoRoot = File(appFiles, "repo")
    private val picturesDir = File(repoRoot, "Graphics/Pictures")

    fun readGalleryJson(): String {
        val file = File(repoRoot, "gallery.json")
        return if (file.exists()) file.readText() else "[]"
    }

    fun sync(progress: (Int, Int, String) -> Unit): String {
        repoRoot.mkdirs()
        picturesDir.mkdirs()

        // GitLab-compatible API: discover the repository's actual default branch.
        val projectInfo = JSONObject(getText("https://gitgud.io/api/v4/projects/$project"))
        val branch = projectInfo.optString("default_branch").ifBlank { "master" }

        val paths = mutableListOf<String>()
        var page = 1
        while (true) {
            val api = "https://gitgud.io/api/v4/projects/$project/repository/tree" +
                "?path=Graphics%2FPictures&recursive=true&per_page=100&page=$page&ref=" +
                enc(branch)
            val arr = JSONArray(getText(api))
            if (arr.length() == 0) break
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                if (item.optString("type") == "blob") {
                    val path = item.optString("path")
                    val lower = path.lowercase()
                    if (lower.endsWith(".png") || lower.endsWith(".jpg") ||
                        lower.endsWith(".jpeg") || lower.endsWith(".webp") ||
                        lower.endsWith(".gif")) paths.add(path)
                }
            }
            if (arr.length() < 100) break
            page++
        }

        val output = JSONArray()
        val total = paths.size
        paths.forEachIndexed { index, path ->
            val relative = path.removePrefix("Graphics/Pictures/")
            val target = File(picturesDir, relative)
            target.parentFile?.mkdirs()
            if (!target.exists() || target.length() == 0L) {
                val rawUrl = "https://gitgud.io/Astrologeos/black_souls/-/raw/" +
                    enc(branch) + "/" + path.split("/").joinToString("/") { enc(it) }
                try { download(rawUrl, target) } catch (_: Exception) { target.delete() }
            }
            if (target.exists() && target.length() > 0) {
                val entry = JSONObject()
                entry.put("name", target.nameWithoutExtension)
                entry.put("path", "/repo/Graphics/Pictures/" +
                    relative.split("/").joinToString("/") { enc(it) })
                entry.put("filename", target.name)
                output.put(entry)
            }
            progress(index + 1, total, target.name)
        }
        File(repoRoot, "gallery.json").writeText(output.toString())
        return output.toString()
    }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun getText(address: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 30000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "BlackSoulsPinterest-Android/1.0")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                throw IllegalStateException("HTTP $code ${error?.take(180) ?: ""}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
    }

    private fun download(address: String, destination: File) {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 60000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "BlackSoulsPinterest-Android/1.0")
        }
        try {
            if (connection.responseCode !in 200..299)
                throw IllegalStateException("HTTP ${connection.responseCode}")
            connection.inputStream.use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
        } finally { connection.disconnect() }
    }
}
