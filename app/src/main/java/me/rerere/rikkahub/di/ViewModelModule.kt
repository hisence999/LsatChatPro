package me.rerere.rikkahub.di

import me.rerere.rikkahub.ui.pages.assistant.AssistantVM
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantDetailVM
import me.rerere.rikkahub.ui.pages.assistant.scheduled.AssistantScheduledTaskEditVM
import me.rerere.rikkahub.ui.pages.assistant.scheduled.AssistantScheduledTasksVM
import me.rerere.rikkahub.ui.pages.backup.BackupVM
import me.rerere.rikkahub.ui.pages.chat.ChatVM
import me.rerere.rikkahub.ui.pages.imggen.ImgGenVM
import me.rerere.rikkahub.ui.pages.logs.RequestLogsVM
import me.rerere.rikkahub.ui.pages.logs.RequestLogDetailVM
import me.rerere.rikkahub.ui.pages.setting.SettingVM
import me.rerere.rikkahub.ui.pages.share.handler.ShareHandlerVM
import me.rerere.rikkahub.ui.pages.menu.MenuVM
import me.rerere.rikkahub.ui.pages.translator.TranslatorVM
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModel<ChatVM> { params ->
        ChatVM(
            id = params.get(),
            context = get(),
            settingsStore = get(),
            conversationRepo = get(),
            chatService = get(),
            updateChecker = get(),
            analytics = get(),
            appScope = get()
        )
    }
    viewModelOf(::SettingVM)
    viewModelOf(::AssistantVM)
    viewModel<AssistantDetailVM> {
        AssistantDetailVM(
            id = it.get(),
            settingsStore = get(),
            memoryRepository = get(),
            conversationRepository = get(),
            context = get(),
            chatEpisodeDAO = get(),
            providerManager = get(),
        )
    }
    viewModelOf(::TranslatorVM)
    viewModel<ShareHandlerVM> {
        ShareHandlerVM(
            text = it.get(),
            settingsStore = get(),
        )
    }
    viewModelOf(::BackupVM)
    viewModelOf(::ImgGenVM)
    viewModelOf(::RequestLogsVM)
    viewModel<RequestLogDetailVM> {
        RequestLogDetailVM(
            id = it.get(),
            requestLogManager = get(),
        )
    }
    viewModelOf(::MenuVM)

    viewModel<AssistantScheduledTasksVM> {
        AssistantScheduledTasksVM(
            assistantId = it.get(),
            settingsStore = get(),
            taskDao = get(),
            scheduler = get(),
        )
    }

    viewModel<AssistantScheduledTaskEditVM> {
        AssistantScheduledTaskEditVM(
            assistantId = it.get(),
            taskId = it.getOrNull(),
            taskDao = get(),
            scheduler = get(),
        )
    }
}
