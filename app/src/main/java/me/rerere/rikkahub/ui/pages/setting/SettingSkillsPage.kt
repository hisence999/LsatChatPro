package me.rerere.rikkahub.ui.pages.setting

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Skill
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import me.rerere.rikkahub.ui.components.ui.PhysicsSwipeToDelete
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.utils.SkillZipImport
import org.koin.androidx.compose.koinViewModel
import java.io.File

@Composable
fun SettingSkillsPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val toaster = LocalToaster.current
    val haptics = rememberPremiumHaptics()

    var deletingSkill by remember { mutableStateOf<Skill?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            when (val result = SkillZipImport.importFromUri(context, uri)) {
                is SkillZipImport.ImportResult.Success -> {
                    vm.updateSettings { old ->
                        old.copy(skills = old.skills + result.skills)
                    }
                    haptics.perform(HapticPattern.Success)
                    toaster.show(
                        message = context.getString(R.string.skills_import_success, result.skills.size),
                    )
                }

                is SkillZipImport.ImportResult.Error -> {
                    haptics.perform(HapticPattern.Error)
                    toaster.show(message = result.message)
                }
            }
        }
    }

    fun requestImport() {
        haptics.perform(HapticPattern.Tick)
        importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*"))
    }

    fun deleteSkill(skill: Skill) {
        scope.launch {
            // 1) Update settings first (so UI/assistant state is consistent immediately).
            vm.updateSettings { old ->
                old.copy(
                    skills = old.skills.filter { it.id != skill.id },
                    assistants = old.assistants.map { assistant ->
                        assistant.copy(enabledSkillIds = assistant.enabledSkillIds - skill.id)
                    }
                )
            }

            // 2) Remove files on IO dispatcher.
            withContext(Dispatchers.IO) {
                runCatching {
                    File(context.filesDir, "skills/${skill.id}").deleteRecursively()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            OneUITopAppBar(
                title = stringResource(R.string.skills_page_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackButton() }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { requestImport() },
                shape = AppShapes.CardLarge,
            ) {
                Icon(Icons.Rounded.Download, contentDescription = stringResource(R.string.import_label))
            }
        }
    ) { paddingValues ->
        if (settings.skills.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Icon(
                        Icons.Rounded.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = stringResource(R.string.skills_page_empty),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.skills_page_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(1.dp))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(
                    items = settings.skills,
                    key = { _, skill -> skill.id }
                ) { index, skill ->
                    val position = when {
                        settings.skills.size == 1 -> ItemPosition.ONLY
                        index == 0 -> ItemPosition.FIRST
                        index == settings.skills.lastIndex -> ItemPosition.LAST
                        else -> ItemPosition.MIDDLE
                    }

                    PhysicsSwipeToDelete(
                        position = position,
                        deleteEnabled = true,
                        onDelete = { deletingSkill = skill },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SkillCard(
                            skill = skill,
                            position = position,
                        )
                    }
                }
            }
        }

        deletingSkill?.let { skill ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { deletingSkill = null },
                title = { Text(stringResource(R.string.skills_delete_title)) },
                text = { Text(stringResource(R.string.skills_delete_desc, skill.name.ifBlank { skill.id.toString() })) },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            haptics.perform(HapticPattern.Thud)
                            deleteSkill(skill)
                            deletingSkill = null
                        }
                    ) { Text(stringResource(R.string.delete)) }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { deletingSkill = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}

@Composable
private fun SkillCard(
    skill: Skill,
    position: ItemPosition,
) {
    val cornerRadius = 28.dp
    val smallCorner = 8.dp
    val shape = when (position) {
        ItemPosition.ONLY -> androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius)
        ItemPosition.FIRST -> androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = cornerRadius, topEnd = cornerRadius,
            bottomStart = smallCorner, bottomEnd = smallCorner
        )
        ItemPosition.MIDDLE -> androidx.compose.foundation.shape.RoundedCornerShape(smallCorner)
        ItemPosition.LAST -> androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = smallCorner, topEnd = smallCorner,
            bottomStart = cornerRadius, bottomEnd = cornerRadius
        )
    }

    androidx.compose.material3.Card(
        shape = shape,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = skill.name.ifBlank { stringResource(R.string.skills_unnamed) },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (skill.description.isNotBlank()) {
                Text(
                    text = skill.description.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = skill.id.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
        }
    }
}

