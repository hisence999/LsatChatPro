package me.rerere.rikkahub.ui.pages.logs

import androidx.lifecycle.ViewModel
import me.rerere.rikkahub.data.ai.AIRequestLogManager

class RequestLogDetailVM(
    id: Long,
    requestLogManager: AIRequestLogManager,
) : ViewModel() {
    val log = requestLogManager.observeById(id)
}

