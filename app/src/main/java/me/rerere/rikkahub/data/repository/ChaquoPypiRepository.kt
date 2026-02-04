package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.common.http.await
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class ChaquoPypiRepository(
    private val client: OkHttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    data class PackageIndexEntry(
        val name: String,
        val url: String,
    )

    data class WheelIndexEntry(
        val fileName: String,
        val url: String,
        val lastModified: String?,
        val sizeLabel: String?,
    )

    suspend fun listPackages(): List<PackageIndexEntry> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(baseUrl).get().build()
        val response = client.newCall(request).await()
        response.use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val html = resp.body?.string().orEmpty()
            parseRootIndexHtml(baseUrl = baseUrl.ensureEndsWithSlash(), html = html)
        }
    }

    suspend fun listWheels(packageName: String): List<WheelIndexEntry> = withContext(Dispatchers.IO) {
        val normalized = normalizePackageDirName(packageName)
        if (normalized.isBlank()) return@withContext emptyList()

        val packageUrl = joinUrl(baseUrl.ensureEndsWithSlash(), "$normalized/")
        val request = Request.Builder().url(packageUrl).get().build()
        val response = client.newCall(request).await()
        response.use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val html = resp.body?.string().orEmpty()
            parseIndexHtml(packageUrl = packageUrl, html = html)
        }
    }

    suspend fun resolveBestWheel(
        packageName: String,
        version: String?,
        pythonVersionMajorMinor: String,
        preferredAbis: List<String>,
        sdkInt: Int,
    ): WheelIndexEntry = withContext(Dispatchers.IO) {
        val entries = listWheels(packageName)
        if (entries.isEmpty()) error("未找到该依赖或没有可用的 .whl")

        val cpTag = pythonVersionMajorMinor.toCpTagOrNull() ?: error("Python 版本不支持：$pythonVersionMajorMinor")
        val preferredAbiTags = preferredAbis.mapNotNull { it.toWheelAbiTagOrNull() }
        if (preferredAbiTags.isEmpty()) error("当前设备不支持的 ABI：${preferredAbis.joinToString()}")

        val desiredVersion = version?.trim()?.takeIf { it.isNotBlank() }?.normalizeWheelVersionOrNull()
        val candidates = entries.mapNotNull { entry ->
            val parsed = WheelFilename.parse(entry.fileName) ?: return@mapNotNull null
            if (!desiredVersion.isNullOrBlank() && parsed.version != desiredVersion) return@mapNotNull null
            val match = computeCompatibility(
                parsed = parsed,
                lastModified = entry.lastModified,
                requiredCpTag = cpTag,
                preferredAbiTags = preferredAbiTags,
                sdkInt = sdkInt,
            ) ?: return@mapNotNull null
            Candidate(entry = entry, parsed = parsed, match = match)
        }

        if (candidates.isEmpty()) {
            val hint = buildString {
                append("未找到可安装的 .whl")
                desiredVersion?.let { append("（版本：$it）") }
                append("。")
            }
            error(hint)
        }

        candidates.maxWith(
            compareBy<Candidate> { it.match.lastModifiedKey }
                .thenBy { it.match.platformScore }
                .thenBy { it.match.pythonScore }
                .thenBy { it.match.androidApiScore }
                .thenBy { it.match.preferredAbiIndex }
        ).entry
    }

    suspend fun downloadWheel(url: String, outFile: File, maxBytes: Long = DEFAULT_MAX_DOWNLOAD_BYTES): Long =
        withContext(Dispatchers.IO) {
            outFile.parentFile?.mkdirs()
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).await()
            response.use { resp ->
                if (!resp.isSuccessful) error("下载失败：HTTP ${resp.code}")
                val body = resp.body ?: error("下载失败：空响应")

                val contentLength = body.contentLength()
                if (contentLength > 0 && contentLength > maxBytes) error("文件过大（>${maxBytes / 1024 / 1024}MB）")

                var total = 0L
                body.byteStream().use { input ->
                    FileOutputStream(outFile).use { out ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            out.write(buffer, 0, read)
                            total += read
                            if (total > maxBytes) error("文件过大（>${maxBytes / 1024 / 1024}MB）")
                        }
                    }
                }
                total
            }
        }

    private data class Candidate(
        val entry: WheelIndexEntry,
        val parsed: WheelFilename,
        val match: MatchScore,
    )

    private data class MatchScore(
        val lastModifiedKey: String,
        val platformScore: Int,
        val pythonScore: Int,
        val androidApiScore: Int,
        val preferredAbiIndex: Int,
    )

    private fun computeCompatibility(
        parsed: WheelFilename,
        lastModified: String?,
        requiredCpTag: String,
        preferredAbiTags: List<String>,
        sdkInt: Int,
    ): MatchScore? {
        val pythonTags = parsed.pythonTag.split('.').filter { it.isNotBlank() }
        val abiTags = parsed.abiTag.split('.').filter { it.isNotBlank() }
        val platformTags = parsed.platformTag.split('.').filter { it.isNotBlank() }

        val pythonScore = when {
            pythonTags.any { it == requiredCpTag } -> 2
            pythonTags.any { it.startsWith("py3") } -> 1
            else -> 0
        }
        if (pythonScore == 0) return null

        val abiOk = abiTags.any { tag ->
            tag == "none" || tag == requiredCpTag || tag == "abi3"
        }
        if (!abiOk) return null

        var bestPlatformScore = -1
        var bestAndroidApi = -1
        var bestAbiIndex = Int.MIN_VALUE

        for (tag in platformTags) {
            if (tag == "any") {
                bestPlatformScore = maxOf(bestPlatformScore, 0)
                bestAbiIndex = maxOf(bestAbiIndex, -1)
                continue
            }
            val androidMatch = ANDROID_PLATFORM_REGEX.matchEntire(tag) ?: continue
            val minApi = androidMatch.groupValues[1].toIntOrNull() ?: continue
            val abi = androidMatch.groupValues[2]
            val abiIndex = preferredAbiTags.indexOf(abi)
            if (abiIndex == -1) continue
            if (minApi > sdkInt) continue

            if (bestPlatformScore < 2) bestPlatformScore = 2
            if (minApi > bestAndroidApi) bestAndroidApi = minApi
            if (-abiIndex > bestAbiIndex) bestAbiIndex = -abiIndex
        }

        if (bestPlatformScore < 0) return null

        val lastModifiedKey = lastModified?.trim()?.takeIf { it.isNotBlank() } ?: ""
        return MatchScore(
            lastModifiedKey = lastModifiedKey,
            platformScore = bestPlatformScore,
            pythonScore = pythonScore,
            androidApiScore = bestAndroidApi,
            preferredAbiIndex = bestAbiIndex,
        )
    }

    data class WheelFilename(
        val distribution: String,
        val version: String,
        val buildTag: String?,
        val pythonTag: String,
        val abiTag: String,
        val platformTag: String,
    ) {
        companion object {
            fun parse(fileName: String): WheelFilename? {
                val trimmed = fileName.trim()
                if (!trimmed.endsWith(".whl", ignoreCase = true)) return null
                val base = trimmed.substringBeforeLast(".whl")
                val parts = base.split("-")
                if (parts.size < 5) return null

                val platformTag = parts.last()
                val abiTag = parts[parts.size - 2]
                val pythonTag = parts[parts.size - 3]
                val head = parts.subList(0, parts.size - 3)
                if (head.size < 2) return null

                val distribution = head.first()
                val version = head[1]
                val buildTag = head.getOrNull(2)

                return WheelFilename(
                    distribution = distribution,
                    version = version,
                    buildTag = buildTag,
                    pythonTag = pythonTag,
                    abiTag = abiTag,
                    platformTag = platformTag,
                )
            }
        }
    }

    private fun parseRootIndexHtml(baseUrl: String, html: String): List<PackageIndexEntry> {
        return INDEX_PACKAGE_DIR_REGEX
            .findAll(html)
            .mapNotNull { match ->
                val dir = match.groupValues.getOrNull(1)?.trim().orEmpty()
                if (dir.isBlank()) return@mapNotNull null
                val name = dir.trimEnd('/').takeIf { it.isNotBlank() } ?: return@mapNotNull null
                PackageIndexEntry(
                    name = name,
                    url = joinUrl(baseUrl, "$name/"),
                )
            }
            .distinctBy { it.name }
            .sortedBy { it.name }
            .toList()
    }

    private fun parseIndexHtml(packageUrl: String, html: String): List<WheelIndexEntry> {
        return INDEX_WHL_ROW_REGEX
            .findAll(html)
            .mapNotNull { match ->
                val rawHref = match.groupValues.getOrNull(1)?.trim().orEmpty()
                if (rawHref.isBlank()) return@mapNotNull null
                val href = rawHref.substringBefore('#')
                val fileName = href.substringAfterLast('/')
                if (!fileName.endsWith(".whl", ignoreCase = true)) return@mapNotNull null
                val url = if (href.startsWith("http://") || href.startsWith("https://")) href else joinUrl(packageUrl, href)
                val lastMod = match.groupValues.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() }
                val size = match.groupValues.getOrNull(3)?.trim()?.takeIf { it.isNotBlank() }
                WheelIndexEntry(
                    fileName = fileName,
                    url = url,
                    lastModified = lastMod,
                    sizeLabel = size,
                )
            }
            .toList()
    }

    private fun normalizePackageDirName(packageName: String): String {
        return packageName
            .trim()
            .lowercase(Locale.US)
            .replace('_', '-')
            .replace(' ', '-')
    }

    private fun String.ensureEndsWithSlash(): String = if (endsWith("/")) this else "$this/"

    private fun joinUrl(base: String, path: String): String {
        val baseHttpUrl = base.toHttpUrlOrNull()
        val resolved = baseHttpUrl?.resolve(path)?.toString()
        return resolved ?: (base.ensureEndsWithSlash() + path.removePrefix("/"))
    }

    private fun String.normalizeWheelVersionOrNull(): String? =
        trim().takeIf { it.isNotBlank() }?.replace('-', '_')

    private fun String.toCpTagOrNull(): String? {
        val parts = trim().split(".")
        val major = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return "cp$major$minor"
    }

    private fun String.toWheelAbiTagOrNull(): String? = when (trim()) {
        "arm64-v8a" -> "arm64_v8a"
        "armeabi-v7a" -> "armeabi_v7a"
        "x86_64" -> "x86_64"
        "x86" -> "x86"
        else -> null
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://chaquo.com/pypi-13.1/"
        private const val DEFAULT_MAX_DOWNLOAD_BYTES: Long = 100L * 1024L * 1024L

        private val ANDROID_PLATFORM_REGEX = Regex("""android_(\d+)_([a-z0-9_]+)""")
        private val INDEX_PACKAGE_DIR_REGEX = Regex(
            pattern = """<a\s+href="([^"/]+)/">""",
            options = setOf(RegexOption.IGNORE_CASE),
        )

        private val INDEX_WHL_ROW_REGEX = Regex(
            pattern = """<a\s+href="([^"]+?\.whl[^"]*)".*?</a>\s*</td>\s*<td\s+class="indexcollastmod">\s*([^<]*?)\s*</td>\s*<td\s+class="indexcolsize">\s*([^<]*?)\s*</td>""",
            options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
    }
}
