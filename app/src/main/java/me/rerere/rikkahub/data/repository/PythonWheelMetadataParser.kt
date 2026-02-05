package me.rerere.rikkahub.data.repository

import java.io.File
import java.util.Locale

data class PythonPackageRequirement(
    val name: String,
    val extras: String? = null,
    val versionSpec: String? = null,
    val marker: String? = null,
    val raw: String,
) {
    val normalizedName: String = PythonWheelMetadataParser.normalizePackageName(name)

    val isOptional: Boolean
        get() = marker?.let { OPTIONAL_EXTRA_REGEX.containsMatchIn(it) } == true

    fun optionalExtraNameOrNull(): String? {
        val m = marker ?: return null
        val match = OPTIONAL_EXTRA_NAME_REGEX.find(m) ?: return null
        return match.groupValues.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() }
    }

    fun exactVersionOrNull(): String? {
        val spec = versionSpec?.trim().orEmpty()
        if (spec.isBlank()) return null
        val match = EXACT_VERSION_REGEX.find(spec) ?: return null
        return match.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    companion object {
        private val EXACT_VERSION_REGEX = Regex("""(?:^|,)\s*==\s*([^\s,;]+)""")
        private val OPTIONAL_EXTRA_REGEX = Regex("""\bextra\s*==""", RegexOption.IGNORE_CASE)
        private val OPTIONAL_EXTRA_NAME_REGEX = Regex("""\bextra\s*==\s*(['"])(.*?)\1""", RegexOption.IGNORE_CASE)
    }
}

object PythonWheelMetadataParser {
    fun normalizePackageName(name: String): String {
        return name
            .trim()
            .lowercase(Locale.US)
            .replace('_', '-')
            .replace('.', '-')
            .replace(' ', '-')
    }

    fun readRequiresDistFromWheel(unpackedDir: File, sysPaths: List<String>): List<PythonPackageRequirement> {
        val metadata = findMetadataFile(unpackedDir = unpackedDir, sysPaths = sysPaths) ?: return emptyList()
        val rawValues = readRequiresDistRawValues(metadataFile = metadata)
        return rawValues
            .mapNotNull(::parseRequiresDistValue)
            .distinctBy { req ->
                buildString {
                    append(req.normalizedName)
                    req.extras?.let { append("|extras=").append(it) }
                    req.versionSpec?.let { append("|spec=").append(it) }
                    req.marker?.let { append("|marker=").append(it) }
                }
            }
    }

    fun parseRequiresDistValue(value: String): PythonPackageRequirement? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null

        val parts = trimmed.split(";", limit = 2)
        val reqPart = parts.getOrNull(0)?.trim().orEmpty()
        val marker = parts.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
        if (reqPart.isBlank()) return null

        val nameMatch = NAME_REGEX.find(reqPart) ?: return null
        val name = nameMatch.groupValues.getOrNull(1)?.trim().orEmpty()
        if (name.isBlank()) return null

        var rest = reqPart.substring(nameMatch.range.last + 1).trim()

        var extras: String? = null
        if (rest.startsWith("[")) {
            val end = rest.indexOf(']')
            if (end > 0) {
                extras = rest.substring(1, end).trim().takeIf { it.isNotBlank() }
                rest = rest.substring(end + 1).trim()
            }
        }

        var versionSpec: String? = null
        if (rest.startsWith("(")) {
            val end = rest.indexOf(')')
            if (end > 0) {
                versionSpec = rest.substring(1, end).trim().takeIf { it.isNotBlank() }
                rest = rest.substring(end + 1).trim()
            }
        } else if (rest.isNotBlank()) {
            versionSpec = rest.trim().takeIf { it.isNotBlank() }
            rest = ""
        }

        return PythonPackageRequirement(
            name = name,
            extras = extras,
            versionSpec = versionSpec,
            marker = marker,
            raw = trimmed,
        )
    }

    private fun findMetadataFile(unpackedDir: File, sysPaths: List<String>): File? {
        val candidates = buildList<File> {
            add(unpackedDir)
            for (p in sysPaths) add(File(p))
        }.distinctBy { it.absolutePath }

        val distInfoDir = candidates
            .asSequence()
            .filter { it.isDirectory }
            .flatMap { dir -> dir.listFiles().orEmpty().asSequence() }
            .firstOrNull { it.isDirectory && it.name.endsWith(".dist-info") }

        return distInfoDir?.let { File(it, "METADATA") }?.takeIf { it.isFile }
    }

    private fun readRequiresDistRawValues(metadataFile: File): List<String> {
        val out = mutableListOf<StringBuilder>()
        var current: StringBuilder? = null

        runCatching {
            metadataFile.useLines { lines ->
                for (line in lines) {
                    if (line.startsWith("Requires-Dist:", ignoreCase = true)) {
                        val value = line.substringAfter(":", "").trim()
                        current = StringBuilder(value)
                        out += current!!
                        continue
                    }

                    val canContinue = current != null && (line.startsWith(" ") || line.startsWith("\t"))
                    if (canContinue) {
                        val continuation = line.trim()
                        if (continuation.isNotBlank()) current!!.append(' ').append(continuation)
                    } else {
                        current = null
                    }
                }
            }
        }

        return out.map { it.toString() }.filter { it.isNotBlank() }
    }

    private val NAME_REGEX = Regex("""^([A-Za-z0-9][A-Za-z0-9._-]*)""")
}
