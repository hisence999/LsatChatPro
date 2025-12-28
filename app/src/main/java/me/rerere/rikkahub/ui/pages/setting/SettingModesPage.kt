package me.rerere.rikkahub.ui.pages.setting

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Mode
import me.rerere.rikkahub.data.model.ModeAttachment
import me.rerere.rikkahub.data.model.ModeAttachmentType
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import me.rerere.rikkahub.ui.components.ui.PhysicsSwipeToDelete
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.components.ui.ToastAction
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.createChatFilesByContents
import me.rerere.rikkahub.utils.getFileNameFromUri
import me.rerere.rikkahub.utils.getFileMimeType
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState


@Composable
fun SettingModesPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val lazyListState = rememberLazyListState()
    val haptics = rememberPremiumHaptics()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val navController = LocalNavController.current
    
    var showAddDialog by remember { mutableStateOf(false) }
    var editingMode by remember { mutableStateOf<Mode?>(null) }
    
    // Track drag state for neighbor offset
    var draggingIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(0f) }
    var isUnlocked by remember { mutableStateOf(false) }
    
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // Subtract 1 to account for the description header item
        val fromIndex = from.index - 1
        val toIndex = to.index - 1
        if (fromIndex >= 0 && toIndex >= 0 && fromIndex < settings.modes.size && toIndex <= settings.modes.size) {
            val newModes = settings.modes.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
            vm.updateSettings(settings.copy(modes = newModes))
            haptics.perform(HapticPattern.Pop)
        }
    }

    Scaffold(
        topBar = {
            OneUITopAppBar(
                title = stringResource(R.string.prompt_injections_page_modes),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    BackButton()
                }
            )
        },
        bottomBar = {
            // Both FAB and tab bar at same height
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                // Centered floating tab bar
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Modes tab (selected)
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoFixHigh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        // Lorebooks tab
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    haptics.perform(HapticPattern.Tick)
                                    navController.navigate(Screen.SettingLorebooks) {
                                        popUpTo(Screen.SettingModes) { inclusive = true }
                                    }
                                }
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Book,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                
                // FAB aligned to end/right at same height
                FloatingActionButton(
                    onClick = { 
                        showAddDialog = true
                        haptics.perform(HapticPattern.Pop)
                    },
                    modifier = Modifier.align(Alignment.CenterEnd),
                    shape = AppShapes.CardLarge
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.add))
                }
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(contentPadding),
                state = lazyListState,
                contentPadding = contentPadding + PaddingValues(16.dp) + PaddingValues(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
            // Description card
            item(key = "description") {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    shape = AppShapes.CardLarge
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.modes_page_description_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.modes_page_description_text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            
            if (settings.modes.isEmpty()) {
                item(key = "empty") {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = AppShapes.CardLarge
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.modes_page_empty_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = stringResource(R.string.modes_page_empty_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = settings.modes,
                    key = { _, mode -> mode.id }
                ) { index, mode ->
                    val canDelete = settings.modes.size > 0  // Always allow deletion
                    val position = when {
                        settings.modes.size == 1 -> ItemPosition.ONLY
                        index == 0 -> ItemPosition.FIRST
                        index == settings.modes.lastIndex -> ItemPosition.LAST
                        else -> ItemPosition.MIDDLE
                    }
                    
                    // Calculate neighbor offset for physics effect
                    val neighborOffset = when {
                        draggingIndex == -1 -> 0f
                        index == draggingIndex - 1 && isUnlocked -> dragOffset * 0.15f
                        index == draggingIndex + 1 && isUnlocked -> dragOffset * 0.15f
                        else -> 0f
                    }

                    ReorderableItem(
                        state = reorderableState, 
                        key = mode.id
                    ) { isDragging ->
                        PhysicsSwipeToDelete(
                            position = position,
                            deleteEnabled = canDelete,
                            neighborOffset = neighborOffset,
                            onDragProgress = { offset, unlocked ->
                                draggingIndex = index
                                dragOffset = offset
                                isUnlocked = unlocked
                            },
                            onDragEnd = {
                                if (draggingIndex == index) {
                                    draggingIndex = -1
                                    dragOffset = 0f
                                }
                            },
                            onDelete = {
                                val deletedMode = mode
                                vm.updateSettings(
                                    settings.copy(modes = settings.modes.filter { it.id != mode.id })
                                )
                                toaster.show(
                                    message = context.getString(R.string.modes_page_deleted, mode.name.ifEmpty { context.getString(R.string.modes_page_unnamed) }),
                                    action = ToastAction(
                                        label = context.getString(R.string.undo),
                                        onClick = {
                                            vm.updateSettings(
                                                settings.copy(modes = settings.modes.toMutableList().apply {
                                                    add(index.coerceAtMost(size), deletedMode)
                                                })
                                            )
                                        }
                                    )
                                )
                            },
                            modifier = Modifier
                                .scale(if (isDragging) 0.95f else 1f)
                                .fillMaxWidth()
                        ) {
                            ModeCard(
                                mode = mode,
                                priority = index + 1,
                                onEdit = { editingMode = mode },
                                dragHandle = {
                                    IconButton(
                                        onClick = {},
                                        modifier = Modifier.longPressDraggableHandle(
                                            onDragStarted = {
                                                haptics.perform(HapticPattern.Pop)
                                            },
                                            onDragStopped = {
                                                haptics.perform(HapticPattern.Thud)
                                            }
                                        )
                                    ) {
                                        Icon(Icons.Rounded.DragIndicator, contentDescription = null)
                                    }
                                }
                            )
                        }
                    }
                }
            }
            }
            
            // Bottom fade gradient
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )
        }
    }

    // Add/Edit Mode Dialog
    if (showAddDialog || editingMode != null) {
        ModeEditorSheet(
            mode = editingMode,
            onDismiss = {
                showAddDialog = false
                editingMode = null
            },
            onSave = { savedMode ->
                if (editingMode != null) {
                    val updatedModes = settings.modes.map {
                        if (it.id == savedMode.id) savedMode else it
                    }
                    vm.updateSettings(settings.copy(modes = updatedModes))
                } else {
                    vm.updateSettings(settings.copy(modes = settings.modes + savedMode))
                }
                showAddDialog = false
                editingMode = null
            }
        )
    }
}

@Composable
private fun ModeCard(
    mode: Mode,
    priority: Int,
    onEdit: () -> Unit,
    dragHandle: @Composable () -> Unit
) {
    Card(
        onClick = onEdit,
        colors = CardDefaults.cardColors(
            containerColor = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = AppShapes.CardLarge
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = {
                Text(
                    text = mode.name.ifEmpty { stringResource(R.string.modes_page_unnamed) },
                    style = MaterialTheme.typography.titleMedium
                )
            },
            supportingContent = {
                Text(
                    text = mode.prompt.take(50) + if (mode.prompt.length > 50) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            },
            leadingContent = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = priority.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            },
            trailingContent = dragHandle
        )
    }
}

@Composable
internal fun ModeEditorSheet(
    mode: Mode?,
    onDismiss: () -> Unit,
    onSave: (Mode) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    
    var name by remember(mode) { mutableStateOf(mode?.name ?: "") }
    var prompt by remember(mode) { mutableStateOf(mode?.prompt ?: "") }
    var defaultEnabled by remember(mode) { mutableStateOf(mode?.defaultEnabled ?: false) }
    var injectionPosition by remember(mode) { 
        mutableStateOf(mode?.injectionPosition ?: InjectionPosition.AFTER_SYSTEM) 
    }
    var depth by remember(mode) { mutableStateOf(mode?.depth ?: 0) }
    var attachments by remember(mode) { mutableStateOf(mode?.attachments ?: emptyList()) }
    
    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val localUris = context.createChatFilesByContents(uris)
            val newAttachments = localUris.map { uri ->
                ModeAttachment(
                    url = uri.toString(),
                    type = ModeAttachmentType.IMAGE
                )
            }
            attachments = attachments + newAttachments
        }
    }
    
    // Video picker launcher
    val videoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val localUris = context.createChatFilesByContents(uris)
            val newAttachments = localUris.map { uri ->
                ModeAttachment(
                    url = uri.toString(),
                    type = ModeAttachmentType.VIDEO
                )
            }
            attachments = attachments + newAttachments
        }
    }
    
    // Audio picker launcher
    val audioPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val localUris = context.createChatFilesByContents(uris)
            val newAttachments = localUris.map { uri ->
                ModeAttachment(
                    url = uri.toString(),
                    type = ModeAttachmentType.AUDIO
                )
            }
            attachments = attachments + newAttachments
        }
    }
    
    // Document picker launcher
    val documentPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val newAttachments = uris.mapNotNull { uri ->
                val fileName = context.getFileNameFromUri(uri) ?: "file"
                val mime = context.getFileMimeType(uri) ?: "application/octet-stream"
                val localUri = context.createChatFilesByContents(listOf(uri)).firstOrNull()
                localUri?.let {
                    ModeAttachment(
                        url = it.toString(),
                        type = ModeAttachmentType.DOCUMENT,
                        fileName = fileName,
                        mime = mime
                    )
                }
            }
            attachments = attachments + newAttachments
        }
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(
                    if (mode != null) R.string.modes_page_edit_mode 
                    else R.string.modes_page_add_mode
                ),
                style = MaterialTheme.typography.titleLarge
            )

            FormItem(
                label = { Text(stringResource(R.string.modes_page_name)) }
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.modes_page_name_placeholder)) }
                )
            }

            FormItem(
                label = { Text(stringResource(R.string.modes_page_prompt)) }
            ) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    placeholder = { Text(stringResource(R.string.modes_page_prompt_placeholder)) }
                )
            }
            
            // Attachments section
            FormItem(
                label = { Text(stringResource(R.string.modes_page_attachments)) }
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Display current attachments
                    if (attachments.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            attachments.forEach { attachment ->
                                ModeAttachmentItem(
                                    attachment = attachment,
                                    onRemove = {
                                        attachments = attachments.filter { it != attachment }
                                    }
                                )
                            }
                        }
                    }
                    
                    // Add attachment buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { imagePickerLauncher.launch("image/*") }
                        ) {
                            Text(stringResource(R.string.modes_page_add_image))
                        }
                        OutlinedButton(
                            onClick = { documentPickerLauncher.launch(arrayOf("*/*")) }
                        ) {
                            Text(stringResource(R.string.modes_page_add_file))
                        }
                    }
                }
            }

            FormItem(
                label = { Text(stringResource(R.string.modes_page_injection_position)) }
            ) {
                Select(
                    options = InjectionPosition.entries,
                    selectedOption = injectionPosition,
                    onOptionSelected = { injectionPosition = it },
                    optionToString = { position ->
                        when (position) {
                            InjectionPosition.BEFORE_SYSTEM -> stringResource(R.string.injection_position_before_system)
                            InjectionPosition.AFTER_SYSTEM -> stringResource(R.string.injection_position_after_system)
                            InjectionPosition.TOP_OF_CHAT -> stringResource(R.string.injection_position_top_of_chat)
                            InjectionPosition.BEFORE_LATEST -> stringResource(R.string.injection_position_before_latest)
                            InjectionPosition.AT_DEPTH -> stringResource(R.string.injection_position_at_depth)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            AnimatedVisibility(
                visible = injectionPosition == InjectionPosition.AT_DEPTH
            ) {
                FormItem(
                    label = { Text(stringResource(R.string.modes_page_depth)) }
                ) {
                    OutlinedTextField(
                        value = depth.toString(),
                        onValueChange = { depth = it.toIntOrNull() ?: 0 },
                        modifier = Modifier.width(80.dp),
                        singleLine = true
                    )
                }
            }

            // Default enabled toggle
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(R.string.modes_page_default_enabled)) },
                supportingContent = { Text(stringResource(R.string.modes_page_default_enabled_desc)) },
                trailingContent = {
                    HapticSwitch(
                        checked = defaultEnabled,
                        onCheckedChange = { defaultEnabled = it }
                    )
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        val savedMode = (mode ?: Mode()).copy(
                            name = name,
                            prompt = prompt,
                            defaultEnabled = defaultEnabled,
                            injectionPosition = injectionPosition,
                            depth = depth,
                            attachments = attachments
                        )
                        onSave(savedMode)
                    },
                    enabled = name.isNotBlank() && (prompt.isNotBlank() || attachments.isNotEmpty())
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

@Composable
private fun ModeAttachmentItem(
    attachment: ModeAttachment,
    onRemove: () -> Unit
) {
    Box {
        when (attachment.type) {
            ModeAttachmentType.IMAGE -> {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 4.dp
                ) {
                    AsyncImage(
                        model = attachment.url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            ModeAttachmentType.VIDEO -> {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 4.dp
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.VideoLibrary, null)
                    }
                }
            }
            ModeAttachmentType.AUDIO -> {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 4.dp
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.AudioFile, null)
                    }
                }
            }
            ModeAttachmentType.DOCUMENT -> {
                Surface(
                    modifier = Modifier
                        .height(48.dp)
                        .widthIn(max = 128.dp),
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 4.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.AttachFile, 
                            null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = attachment.fileName.ifEmpty { "File" },
                            style = MaterialTheme.typography.bodySmall,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        
        // Remove button
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = null,
            modifier = Modifier
                .clip(CircleShape)
                .size(20.dp)
                .clickable { onRemove() }
                .align(Alignment.TopEnd)
                .background(MaterialTheme.colorScheme.secondary),
            tint = MaterialTheme.colorScheme.onSecondary
        )
    }
}

