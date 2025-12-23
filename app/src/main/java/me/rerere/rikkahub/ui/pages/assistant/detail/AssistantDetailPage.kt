package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ai.McpPicker
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.ai.ReasoningButton
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.components.ui.TagsInput
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.components.ui.DebouncedTextField
import me.rerere.rikkahub.utils.toFixed
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt
import me.rerere.rikkahub.data.model.Tag as DataTag
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.Icon

@Composable
fun AssistantDetailPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )
    val scope = rememberCoroutineScope()

    val mcpServerConfigs by vm.mcpServerConfigs.collectAsStateWithLifecycle()
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val memories by vm.memories.collectAsStateWithLifecycle()
    val providers by vm.providers.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val snackbarMessage by vm.snackbarMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearSnackbarMessage()
        }
    }

    fun onUpdate(assistant: Assistant) {
        vm.update(assistant)
    }

    data class TabItem(val title: String, val content: @Composable () -> Unit)

    val tabItems = buildList {
        // Profile - Identity and appearance
        add(TabItem("Profile") {
            AssistantProfileSubPage(
                assistant = assistant,
                tags = tags,
                onUpdate = { onUpdate(it) },
                vm = vm
            )
        })

        // Model - All generation settings
        add(TabItem("Model") {
            AssistantModelSubPage(
                assistant = assistant,
                providers = providers,
                onUpdate = { onUpdate(it) }
            )
        })

        // Prompt
        add(TabItem(stringResource(R.string.assistant_page_tab_prompt)) {
            AssistantPromptSubPage(
                assistant = assistant,
                onUpdate = { onUpdate(it) },
                vm = vm
            )
        })

        // Memory
        add(TabItem(stringResource(R.string.assistant_page_tab_memory)) {
            val embeddingProgress by vm.embeddingProgress.collectAsStateWithLifecycle()
            val estimatedMemoryCapacity by vm.estimatedMemoryCapacity.collectAsStateWithLifecycle()
            val needsEmbeddingRegeneration by vm.needsEmbeddingRegeneration.collectAsStateWithLifecycle()
            val retrievalResults by vm.retrievalResults.collectAsStateWithLifecycle()
            AssistantMemorySettings(
                assistant = assistant,
                memories = memories,
                onUpdateAssistant = { onUpdate(it) },
                onDeleteMemory = { vm.deleteMemory(it) },
                onAddMemory = { vm.addMemory(it) },
                onUpdateMemory = { vm.updateMemory(it) },
                onRegenerateEmbeddings = { vm.regenerateEmbeddings() },
                embeddingProgress = embeddingProgress,
                onTestRetrieval = { vm.testRetrieval(it) },
                retrievalResults = retrievalResults,
                assistantDetailVM = vm,
                estimatedMemoryCapacity = estimatedMemoryCapacity,
                needsEmbeddingRegeneration = needsEmbeddingRegeneration
            )
        })

        // Tools - Search, Local Tools, MCP combined
        add(TabItem("Tools") {
            AssistantToolsSubPage(
                assistant = assistant,
                onUpdate = { onUpdate(it) },
                vm = vm,
                mcpServerConfigs = mcpServerConfigs
            )
        })
        
        // Advanced - Notifications and custom request headers
        add(TabItem("Advanced") {
            AssistantAdvancedSubPage(
                assistant = assistant,
                onUpdate = { onUpdate(it) }
            )
        })
    }

    val pagerState = rememberPagerState { tabItems.size }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = assistant.name.ifBlank {
                            stringResource(R.string.assistant_page_default_assistant)
                        },
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    BackButton()
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            // PixelPlay-style animated tabs with ScrollableTabRow
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surface,
                edgePadding = 12.dp,
                indicator = { tabPositions ->
                    if (pagerState.currentPage < tabPositions.size) {
                        androidx.compose.material3.TabRowDefaults.PrimaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                            height = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                divider = {}
            ) {
                tabItems.fastForEachIndexed { index, item ->
                    me.rerere.rikkahub.ui.components.ui.TabAnimation(
                        index = index,
                        title = item.title,
                        selectedIndex = pagerState.currentPage,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } }
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (pagerState.currentPage == index) 
                                androidx.compose.ui.text.font.FontWeight.Bold 
                            else 
                                androidx.compose.ui.text.font.FontWeight.Medium
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                beyondViewportPageCount = 1, // Preload adjacent pages for fluid tab switching
                key = { index -> tabItems.getOrNull(index)?.title ?: index }
            ) { page ->
                tabItems.getOrNull(page)?.content?.invoke()
            }
        }
    }

}

@Composable
private fun AssistantBasicSettings(
    assistant: Assistant,
    providers: List<ProviderSetting>,
    tags: List<DataTag>,
    onUpdate: (Assistant) -> Unit,
    vm: AssistantDetailVM,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            UIAvatar(
                value = assistant.avatar,
                name = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
                onUpdate = { avatar ->
                    onUpdate(
                        assistant.copy(
                            avatar = avatar
                        )
                    )
                },
                modifier = Modifier.size(80.dp)
            )
        }

        Card(
            shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge,
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = if (me.rerere.rikkahub.ui.theme.LocalDarkMode.current) androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow else androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FormItem(
                    label = {
                        Text(stringResource(R.string.assistant_page_name))
                    },
                ) {
                    DebouncedTextField(
                        value = assistant.name,
                        onValueChange = {
                            onUpdate(
                                assistant.copy(
                                    name = it
                                )
                            )
                        },
                        stateKey = assistant.id,
                        modifier = Modifier.fillMaxWidth()
                    )
                }



                FormItem(
                    label = {
                        Text(stringResource(R.string.assistant_page_tags))
                    },
                ) {
                    TagsInput(
                        value = assistant.tags,
                        tags = tags,
                        onValueChange = { tagIds, tags ->
                            vm.updateTags(tagIds, tags)
                        },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                FormItem(
                    label = {
                        Text(stringResource(R.string.assistant_page_use_assistant_avatar))
                    },
                    description = {
                        Text(stringResource(R.string.assistant_page_use_assistant_avatar_desc))
                    },
                    tail = {
                        Switch(
                            checked = assistant.useAssistantAvatar,
                            onCheckedChange = {
                                onUpdate(
                                    assistant.copy(
                                        useAssistantAvatar = it
                                    )
                                )
                            }
                        )
                    }
                )
            }
        }

        Card(
            shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge,
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = if (me.rerere.rikkahub.ui.theme.LocalDarkMode.current) androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow else androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FormItem(
                    label = {
                        Text(stringResource(R.string.assistant_page_chat_model))
                    },
                    description = {
                        Text(stringResource(R.string.assistant_page_chat_model_desc))
                    },
                    content = {
                        ModelSelector(
                            modelId = assistant.chatModelId,
                            providers = providers,
                            type = ModelType.CHAT,
                            onSelect = {
                                onUpdate(
                                    assistant.copy(
                                        chatModelId = it.id
                                    )
                                )
                            },
                        )
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                FormItem(
                    label = {
                        Text("Background Model")
                    },
                    description = {
                        Text("Model used for background tasks like spontaneous notifications.")
                    },
                    content = {
                        ModelSelector(
                            modelId = assistant.backgroundModelId,
                            providers = providers,
                            type = ModelType.CHAT,
                            onSelect = {
                                onUpdate(
                                    assistant.copy(
                                        backgroundModelId = it.id
                                    )
                                )
                            },
                        )
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // Search Mode dropdown
                val settings by vm.settings.collectAsStateWithLifecycle()
                val chatModel = remember(assistant.chatModelId, providers) {
                    providers.flatMap { it.models }.find { it.id == assistant.chatModelId }
                }
                val supportsBuiltInSearch = chatModel != null && 
                    me.rerere.ai.registry.ModelRegistry.GEMINI_SERIES.match(chatModel.modelId)
                
                FormItem(
                    label = {
                        Text("Search Mode")
                    },
                    description = {
                        Text("Configure web search for this assistant.")
                    }
                ) {
                    var expanded by remember { mutableStateOf(false) }
                    val currentSearchMode = assistant.searchMode
                    
                    val displayText = when (currentSearchMode) {
                        is me.rerere.rikkahub.data.model.AssistantSearchMode.Off -> "Off"
                        is me.rerere.rikkahub.data.model.AssistantSearchMode.BuiltIn -> "Off" // Legacy: treat as Off
                        is me.rerere.rikkahub.data.model.AssistantSearchMode.Provider -> {
                            settings.searchServices.getOrNull(currentSearchMode.index)?.let {
                                me.rerere.search.SearchServiceOptions.TYPES[it::class] ?: "Provider ${currentSearchMode.index + 1}"
                            } ?: "Provider ${currentSearchMode.index + 1}"
                        }
                    }
                    
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = displayText,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            // Off option
                            DropdownMenuItem(
                                text = { Text("Off") },
                                onClick = {
                                    onUpdate(assistant.copy(searchMode = me.rerere.rikkahub.data.model.AssistantSearchMode.Off))
                                    expanded = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = null
                                    )
                                }
                            )
                            
                            // Configured search providers
                            settings.searchServices.forEachIndexed { index, service ->
                                val serviceName = me.rerere.search.SearchServiceOptions.TYPES[service::class] ?: "Provider ${index + 1}"
                                DropdownMenuItem(
                                    text = { Text(serviceName) },
                                    onClick = {
                                        onUpdate(assistant.copy(searchMode = me.rerere.rikkahub.data.model.AssistantSearchMode.Provider(index)))
                                        expanded = false
                                    },
                                    leadingIcon = {
                                        me.rerere.rikkahub.ui.components.ui.AutoAIIcon(
                                            name = serviceName,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                
                // Prefer Built-in Search Toggle
                FormItem(
                    label = {
                        Text("Prefer Built-in Search")
                    },
                    description = {
                        Text("Falls back to selected provider above if not supported.")
                    }
                ) {
                    Switch(
                        checked = assistant.preferBuiltInSearch,
                        onCheckedChange = {
                            onUpdate(assistant.copy(preferBuiltInSearch = it))
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                FormItem(
                    label = {
                        Text(stringResource(R.string.assistant_page_temperature))
                    },
                    tail = {
                        Switch(
                            checked = assistant.temperature != null,
                            onCheckedChange = { enabled ->
                                onUpdate(
                                    assistant.copy(
                                        temperature = if (enabled) 1.0f else null
                                    )
                                )
                            }
                        )
                    }
                ) {
                    if (assistant.temperature != null) {
                        Slider(
                            value = assistant.temperature,
                            onValueChange = {
                                onUpdate(
                                    assistant.copy(
                                        temperature = it.toFixed(2).toFloatOrNull() ?: 0.6f
                                    )
                                )
                            },
                            valueRange = 0f..2f,
                            steps = 19,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val currentTemperature = assistant.temperature
                            val tagType = when (currentTemperature) {
                                in 0.0f..0.3f -> TagType.INFO
                                in 0.3f..1.0f -> TagType.SUCCESS
                                in 1.0f..1.5f -> TagType.WARNING
                                in 1.5f..2.0f -> TagType.ERROR
                                else -> TagType.ERROR
                            }
                            Tag(
                                type = TagType.INFO
                            ) {
                                Text(
                                    text = "$currentTemperature"
                                )
                            }

                            Tag(
                                type = tagType
                            ) {
                                Text(
                                    text = when (currentTemperature) {
                                        in 0.0f..0.3f -> stringResource(R.string.assistant_page_strict)
                                        in 0.3f..1.0f -> stringResource(R.string.assistant_page_balanced)
                                        in 1.0f..1.5f -> stringResource(R.string.assistant_page_creative)
                                        in 1.5f..2.0f -> stringResource(R.string.assistant_page_chaotic)
                                        else -> "?"
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                FormItem(
                    label = {
                        Text(stringResource(R.string.assistant_page_top_p))
                    },
                    description = {
                        Text(
                            text = buildAnnotatedString {
                                append(stringResource(R.string.assistant_page_top_p_warning))
                            }
                        )
                    },
                    tail = {
                        Switch(
                            checked = assistant.topP != null,
                            onCheckedChange = { enabled ->
                                onUpdate(
                                    assistant.copy(
                                        topP = if (enabled) 1.0f else null
                                    )
                                )
                            }
                        )
                    }
                ) {
                    assistant.topP?.let { topP ->
                        Slider(
                            value = topP,
                            onValueChange = {
                                onUpdate(
                                    assistant.copy(
                                        topP = it.toFixed(2).toFloatOrNull() ?: 1.0f
                                    )
                                )
                            },
                            valueRange = 0f..1f,
                            steps = 0,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = stringResource(
                                R.string.assistant_page_top_p_value,
                                topP.toString()
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Spacer(modifier = Modifier.height(8.dp))
                FormItem(
                    label = {
                        Text("Token Limit")
                    },
                    description = {
                        Text("Maximum tokens for context (System + Chat + Memories).")
                    }
                ) {
                    val smartMinTokenUsage by vm.smartMinTokenUsage.collectAsStateWithLifecycle()
                    val estimatedAllocation by vm.estimatedAllocation.collectAsStateWithLifecycle()
                    
                    // Local state for editing - allows any input while typing
                    var localValue by remember(assistant.maxTokenUsage) { 
                        mutableStateOf(assistant.maxTokenUsage.toString()) 
                    }
                    
                    // Commit value when focus is lost - apply minimum if needed
                    fun commitValue() {
                        val parsedValue = localValue.toIntOrNull() ?: 0
                        val finalValue = parsedValue.coerceAtLeast(smartMinTokenUsage)
                        localValue = finalValue.toString()
                        onUpdate(assistant.copy(maxTokenUsage = finalValue))
                    }

                    OutlinedTextField(
                        value = localValue,
                        onValueChange = { localValue = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (!focusState.isFocused) {
                                    commitValue()
                                }
                            },
                        label = { Text("Tokens (Min: $smartMinTokenUsage)") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = { commitValue() }
                        ),
                        supportingText = {
                            Column {
                                Text(estimatedAllocation)
                                val currentValue = localValue.toIntOrNull() ?: 0
                                if (currentValue < smartMinTokenUsage) {
                                    Text(
                                        text = "Warning: Below recommended minimum ($smartMinTokenUsage)",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        },
                        isError = (localValue.toIntOrNull() ?: 0) < smartMinTokenUsage
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                FormItem(
                    label = {
                        Text(stringResource(R.string.assistant_page_stream_output))
                    },
                    description = {
                        Text(stringResource(R.string.assistant_page_stream_output_desc))
                    },
                    tail = {
                        Switch(
                            checked = assistant.streamOutput,
                            onCheckedChange = {
                                onUpdate(
                                    assistant.copy(
                                        streamOutput = it
                                    )
                                )
                            }
                        )
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                FormItem(
                    label = {
                        Text(stringResource(R.string.assistant_page_thinking_budget))
                    },
                ) {
                    ReasoningButton(
                        reasoningTokens = assistant.thinkingBudget ?: 0,
                        onUpdateReasoningTokens = { tokens ->
                            onUpdate(
                                assistant.copy(
                                    thinkingBudget = tokens
                                )
                            )
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                FormItem(
                    label = {
                        Text(stringResource(R.string.assistant_page_max_tokens))
                    },
                    description = {
                        Text(stringResource(R.string.assistant_page_max_tokens_desc))
                    }
                ) {
                    OutlinedTextField(
                        value = assistant.maxTokens?.toString() ?: "",
                        onValueChange = { text ->
                            val tokens = if (text.isBlank()) {
                                null
                            } else {
                                text.toIntOrNull()?.takeIf { it > 0 }
                            }
                            onUpdate(
                                assistant.copy(
                                    maxTokens = tokens
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(stringResource(R.string.assistant_page_max_tokens_no_limit))
                        },
                        supportingText = {
                            if (assistant.maxTokens != null) {
                                Text(stringResource(R.string.assistant_page_max_tokens_limit, assistant.maxTokens))
                            } else {
                                Text(stringResource(R.string.assistant_page_max_tokens_no_token_limit))
                            }
                        }
                    )
                }
            }
        }

        BackgroundPicker(
            background = assistant.background,
            onUpdate = { background ->
                onUpdate(
                    assistant.copy(
                        background = background
                    )
                )
            }
        )
    }
}

@Composable
private fun AssistantCustomRequestSettings(
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CustomHeaders(
            headers = assistant.customHeaders,
            onUpdate = {
                onUpdate(
                    assistant.copy(
                        customHeaders = it
                    )
                )
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        CustomBodies(
            customBodies = assistant.customBodies,
            onUpdate = {
                onUpdate(
                    assistant.copy(
                        customBodies = it
                    )
                )
            }
        )
    }
}

@Composable
private fun AssistantMcpSettings(
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit,
    mcpServerConfigs: List<McpServerConfig>
) {
    McpPicker(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        assistant = assistant,
        servers = mcpServerConfigs,
        onUpdateAssistant = onUpdate,
    )
}

