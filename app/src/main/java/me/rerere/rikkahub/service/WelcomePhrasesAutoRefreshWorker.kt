package me.rerere.rikkahub.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import me.rerere.rikkahub.data.datastore.SettingsStore
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.uuid.Uuid

class WelcomePhrasesAutoRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val settingsStore: SettingsStore by inject()
    private val welcomePhrasesService: WelcomePhrasesService by inject()

    override suspend fun doWork(): Result {
        val settingsSnapshot = settingsStore.settingsFlow.value
        if (settingsSnapshot.init) return Result.success()

        val assistantId = inputData.getString(KEY_ASSISTANT_ID)
            ?.let { runCatching { Uuid.parse(it) }.getOrNull() }
            ?: settingsSnapshot.assistantId

        Log.i(TAG, "auto refresh attempt ${runAttemptCount + 1}/$MAX_RETRY_COUNT, assistantId=$assistantId")

        val status = welcomePhrasesService.refreshForAssistantIfNeeded(
            assistantId = assistantId,
            maxAttempts = 1,
        )

        return when (status) {
            WelcomePhrasesRefreshStatus.Failed -> {
                Log.w(TAG, "auto refresh failed on attempt ${runAttemptCount + 1}/$MAX_RETRY_COUNT")
                if (runAttemptCount < MAX_RETRY_COUNT - 1) Result.retry() else Result.failure()
            }
            else -> Result.success()
        }
    }

    companion object {
        private const val TAG = "WelcomePhrasesWorker"
        const val KEY_ASSISTANT_ID = "assistantId"
        private const val MAX_RETRY_COUNT = 8
    }
}
