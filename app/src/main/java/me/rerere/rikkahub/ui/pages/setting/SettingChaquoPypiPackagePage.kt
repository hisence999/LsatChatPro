package me.rerere.rikkahub.ui.pages.setting

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.repository.ChaquoPypiRepository
import me.rerere.rikkahub.data.repository.PythonWheelInstaller
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import okhttp3.OkHttpClient
import org.koin.compose.koinInject
import java.io.File
import java.util.Locale

@Composable
fun SettingChaquoPypiPackagePage(packageName: String) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val toaster = LocalToaster.current
    val haptics = rememberPremiumHaptics()

    val okHttpClient: OkHttpClient = koinInject()
    val repo = remember(okHttpClient) { ChaquoPypiRepository(okHttpClient) }
    val wheelInstaller = remember { PythonWheelInstaller(context) }

    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var wheels by remember { mutableStateOf<List<ChaquoPypiRepository.WheelIndexEntry>>(emptyList()) }

    var selectedWheel by remember { mutableStateOf<ChaquoPypiRepository.WheelIndexEntry?>(null) }
    var installing by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            loading = true
            errorMessage = null
            val result = runCatching { repo.listWheels(packageName) }
            wheels = result.getOrDefault(emptyList())
            errorMessage = result.exceptionOrNull()?.message
            loading = false
        }
    }

    LaunchedEffect(packageName) {
        refresh()
    }

    val filtered = remember(wheels, query) {
        val q = query.trim().lowercase(Locale.getDefault())
        val base = if (q.isBlank()) {
            wheels
        } else {
            wheels.filter { it.fileName.lowercase(Locale.getDefault()).contains(q) }
        }

        val sdkInt = Build.VERSION.SDK_INT
        val supportedAbis = Build.SUPPORTED_ABIS.toList()
        base.sortedWith(
            compareByDescending<ChaquoPypiRepository.WheelIndexEntry> { entry ->
                val parsed = ChaquoPypiRepository.WheelFilename.parse(entry.fileName)
                analyzeCompatibility(
                    parsed = parsed,
                    sdkInt = sdkInt,
                    supportedAbis = supportedAbis,
                ).ok
            }
                .thenByDescending { it.lastModified?.trim().orEmpty() }
                .thenBy { it.fileName }
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            OneUITopAppBar(
                title = packageName,
                scrollBehavior = scrollBehavior,
                expandedTitleHorizontalPadding = 32.dp,
                navigationIcon = { BackButton() },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(
                top = 16.dp,
                bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.python_wheels_online_fetch_search_wheels_placeholder)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }

            if (loading) {
                item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            errorMessage?.takeIf { it.isNotBlank() }?.let { msg ->
                item(key = "error") {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        SettingGroupItem(
                            title = stringResource(R.string.python_wheels_online_fetch_wheels_load_failed_title),
                            subtitle = msg,
                            onClick = { refresh() },
                        )
                    }
                }
            }

            if (!loading && errorMessage.isNullOrBlank() && filtered.isEmpty()) {
                item(key = "empty") {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        SettingGroupItem(
                            title = stringResource(R.string.python_wheels_online_fetch_wheels_empty),
                            onClick = null,
                        )
                    }
                }
            }

            items(items = filtered, key = { it.fileName }) { item ->
                val parsed = remember(item.fileName) { ChaquoPypiRepository.WheelFilename.parse(item.fileName) }
                val compat = remember(item.fileName) {
                    analyzeCompatibility(
                        parsed = parsed,
                        sdkInt = Build.VERSION.SDK_INT,
                        supportedAbis = Build.SUPPORTED_ABIS.toList(),
                    )
                }

                val title = parsed?.version?.takeIf { it.isNotBlank() } ?: item.fileName
                val subtitle = buildString {
                    val parts = mutableListOf<String>()
                    parsed?.pythonTag?.takeIf { it.isNotBlank() }?.let(parts::add)
                    parsed?.platformTag?.takeIf { it.isNotBlank() }?.let(parts::add)
                    item.sizeLabel?.takeIf { it.isNotBlank() }?.let(parts::add)
                    item.lastModified?.takeIf { it.isNotBlank() }?.let(parts::add)
                    append(parts.joinToString(" · "))
                    if (!compat.ok) {
                        append("\n")
                        append(stringResource(R.string.python_wheels_online_fetch_incompatible_hint))
                    }
                }.takeIf { it.isNotBlank() }

                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SettingGroupItem(
                        title = title,
                        subtitle = subtitle,
                        onClick = { selectedWheel = item }
                    )
                }
            }
        }
    }

    selectedWheel?.let { wheel ->
        val parsed = remember(wheel.fileName) { ChaquoPypiRepository.WheelFilename.parse(wheel.fileName) }
        val compat = remember(wheel.fileName) {
            analyzeCompatibility(
                parsed = parsed,
                sdkInt = Build.VERSION.SDK_INT,
                supportedAbis = Build.SUPPORTED_ABIS.toList(),
            )
        }

        AlertDialog(
            onDismissRequest = {
                if (!installing) selectedWheel = null
            },
            title = { Text(stringResource(R.string.python_wheels_online_fetch_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = wheel.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (!compat.ok) {
                        Text(
                            text = stringResource(R.string.python_wheels_online_fetch_install_incompatible_note),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        text = stringResource(R.string.python_wheels_risk_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (installing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                            Text(
                                text = stringResource(R.string.python_wheels_online_fetch_working),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !installing,
                    onClick = {
                        haptics.perform(HapticPattern.Thud)
                        val target = wheel
                        installing = true
                        scope.launch {
                            val report = runCatching {
                                withContext(Dispatchers.IO) {
                                    val tempDir = File(context.cacheDir, "python/wheels/online").apply { mkdirs() }
                                    val tempFile = File(tempDir, target.fileName)
                                    runCatching { if (tempFile.exists()) tempFile.delete() }
                                    repo.downloadWheel(target.url, tempFile)
                                    try {
                                        wheelInstaller.importFromFiles(listOf(tempFile))
                                    } finally {
                                        runCatching { tempFile.delete() }
                                    }
                                }
                            }.getOrElse { e ->
                                toaster.show(message = e.message ?: context.getString(R.string.unknown))
                                null
                            }

                            installing = false
                            selectedWheel = null
                            if (report == null) return@launch

                            val message = context.getString(
                                R.string.python_wheels_import_summary,
                                report.success.size,
                                report.duplicated.size,
                                report.failed.size,
                            )
                            toaster.show(message = message)
                        }
                    }
                ) { Text(stringResource(R.string.python_wheels_online_fetch_action)) }
            },
            dismissButton = {
                TextButton(
                    enabled = !installing,
                    onClick = { selectedWheel = null }
                ) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

private data class WheelCompatibility(
    val ok: Boolean,
)

private fun analyzeCompatibility(
    parsed: ChaquoPypiRepository.WheelFilename?,
    sdkInt: Int,
    supportedAbis: List<String>,
): WheelCompatibility {
    if (parsed == null) return WheelCompatibility(ok = true)

    val requiredCpTag = "cp313"
    val pythonTags = parsed.pythonTag.split('.').filter { it.isNotBlank() }
    val abiTags = parsed.abiTag.split('.').filter { it.isNotBlank() }
    val platformTags = parsed.platformTag.split('.').filter { it.isNotBlank() }

    val pythonOk = pythonTags.any { it == requiredCpTag } || pythonTags.any { it.startsWith("py3") }
    if (!pythonOk) return WheelCompatibility(ok = false)

    val abiOk = abiTags.any { tag ->
        tag == "none" || tag == requiredCpTag || tag == "abi3"
    }
    if (!abiOk) return WheelCompatibility(ok = false)

    val abiMap = supportedAbis.mapNotNull { it.toWheelAbiTagOrNull() }.toSet()
    for (tag in platformTags) {
        if (tag == "any") return WheelCompatibility(ok = true)
        val m = Regex("""android_(\d+)_([a-z0-9_]+)""").matchEntire(tag) ?: continue
        val minApi = m.groupValues[1].toIntOrNull() ?: continue
        val abi = m.groupValues[2]
        if (minApi <= sdkInt && abi in abiMap) return WheelCompatibility(ok = true)
    }

    return WheelCompatibility(ok = false)
}

private fun String.toWheelAbiTagOrNull(): String? = when (trim()) {
    "arm64-v8a" -> "arm64_v8a"
    "armeabi-v7a" -> "armeabi_v7a"
    "x86_64" -> "x86_64"
    "x86" -> "x86"
    else -> null
}
