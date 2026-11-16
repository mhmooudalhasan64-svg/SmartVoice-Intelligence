package com.aurevon.velora.transcription

import android.content.Context
import androidx.lifecycle.Observer
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

sealed interface ArabicModelInstallState {
    data object NotInstalled : ArabicModelInstallState
    data object Ready : ArabicModelInstallState
    data class Downloading(val progressPercent: Int?) : ArabicModelInstallState
    data class Failed(val message: String) : ArabicModelInstallState
}

/**
 * Observes a persistent WorkManager download rather than tying the HTTP transfer
 * to the Compose screen's coroutine scope.
 */
class ArabicModelController(context: Context) {
    private val appContext = context.applicationContext
    private val installer = ArabicModelInstaller(appContext.filesDir)
    private val workManager = WorkManager.getInstance(appContext)

    private val _state = MutableStateFlow<ArabicModelInstallState>(
        if (installer.isInstalled()) ArabicModelInstallState.Ready
        else ArabicModelInstallState.NotInstalled
    )
    val state: StateFlow<ArabicModelInstallState> = _state.asStateFlow()

    private val workObserver = Observer<List<WorkInfo>> { workInfos ->
        if (installer.isInstalled()) {
            _state.value = ArabicModelInstallState.Ready
            return@Observer
        }

        val work = workInfos.firstOrNull()
        val progress = work?.progress?.getInt(
            ArabicModelDownloadWorker.KEY_PROGRESS,
            -1
        )?.takeIf { it in 0..100 }
        _state.value = when (work?.state) {
            WorkInfo.State.ENQUEUED,
            WorkInfo.State.RUNNING,
            WorkInfo.State.BLOCKED -> {
                ArabicModelInstallState.Downloading(progress)
            }
            WorkInfo.State.FAILED -> ArabicModelInstallState.Failed(
                work?.outputData?.getString(ArabicModelDownloadWorker.KEY_ERROR)
                    ?: "تعذر تنزيل نموذج العربية"
            )
            WorkInfo.State.SUCCEEDED -> {
                if (installer.isInstalled()) ArabicModelInstallState.Ready
                else ArabicModelInstallState.Failed("انتهى التنزيل لكن ملفات النموذج غير مكتملة")
            }
            WorkInfo.State.CANCELLED, null -> ArabicModelInstallState.NotInstalled
        }
    }

    private val workLiveData = workManager.getWorkInfosForUniqueWorkLiveData(
        ArabicModelDownloadWorker.UNIQUE_WORK_NAME
    )

    init {
        workLiveData.observeForever(workObserver)
    }

    val installedDirectory: java.io.File
        get() = installer.installedDirectory

    fun refresh() {
        if (installer.isInstalled()) {
            _state.value = ArabicModelInstallState.Ready
        }
        // WorkManager's observer restores the in-progress state after app relaunch.
    }

    suspend fun install() {
        if (installer.isInstalled()) {
            _state.value = ArabicModelInstallState.Ready
            return
        }
        if (_state.value is ArabicModelInstallState.Downloading) return

        _state.value = ArabicModelInstallState.Downloading(null)
        val request = OneTimeWorkRequestBuilder<ArabicModelDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30L,
                TimeUnit.SECONDS
            )
            .addTag("velora-arabic-model")
            .build()

        try {
            workManager.enqueueUniqueWork(
                ArabicModelDownloadWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        } catch (error: Exception) {
            _state.value = ArabicModelInstallState.Failed(
                error.message ?: "تعذر بدء تنزيل النموذج"
            )
        }
    }

    fun close() {
        workLiveData.removeObserver(workObserver)
    }
}
