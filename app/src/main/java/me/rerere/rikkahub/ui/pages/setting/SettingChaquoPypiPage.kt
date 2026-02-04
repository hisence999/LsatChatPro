package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.repository.ChaquoPypiRepository
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import okhttp3.OkHttpClient
import org.koin.compose.koinInject
import java.util.Locale

@Composable
fun SettingChaquoPypiPage() {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    val navController = LocalNavController.current
    val okHttpClient: OkHttpClient = koinInject()
    val repo = remember(okHttpClient) { ChaquoPypiRepository(okHttpClient) }

    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var packages by remember { mutableStateOf<List<ChaquoPypiRepository.PackageIndexEntry>>(emptyList()) }

    fun refresh() {
        scope.launch {
            loading = true
            errorMessage = null
            val result = runCatching { repo.listPackages() }
            packages = result.getOrDefault(emptyList())
            errorMessage = result.exceptionOrNull()?.message
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    val filtered = remember(packages, query) {
        val q = query.trim().lowercase(Locale.getDefault())
        if (q.isBlank()) {
            packages
        } else {
            packages.filter { it.name.lowercase(Locale.getDefault()).contains(q) }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            OneUITopAppBar(
                title = stringResource(R.string.python_wheels_online_fetch_title),
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
            item(key = "risk") {
                Text(
                    text = stringResource(R.string.python_wheels_risk_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.python_wheels_online_fetch_search_packages_placeholder)) },
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
                            title = stringResource(R.string.python_wheels_online_fetch_packages_load_failed_title),
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
                            title = stringResource(R.string.python_wheels_online_fetch_packages_empty),
                            onClick = null,
                        )
                    }
                }
            }

            items(
                items = filtered,
                key = { it.name },
            ) { pkg ->
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SettingGroupItem(
                        title = pkg.name,
                        onClick = { navController.navigate(Screen.SettingChaquoPypiPackage(packageName = pkg.name)) },
                    )
                }
            }
        }
    }
}
