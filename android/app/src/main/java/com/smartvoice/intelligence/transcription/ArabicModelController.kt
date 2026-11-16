package com.aurevon.velora.transcription

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

sealed interface ArabicModelInstallState {
    data object NotInstalled : ArabicModelInstallState
    data object Ready : ArabicModelInstallState
    data class Downloading(val progressPercent: Int?) : ArabicModelInstallState
    data class Failed(val message: String) : ArabicModelInstallState
}

/**
 * Owns the Arabic speech model installation lifecycle and exposes observable state.
 * Downloading and extraction remain local; the installer validates archive paths and size.
 */
class ArabicModelController(filesDirectory: File) {
    private val installer = ArabicModelInstaller(filesDirectory)

    private val _state = MutableStateFlow<ArabicModelInstallState>(
        if (installer.isInstalled()) ArabicModelInstallState.Ready
        else ArabicModelInstallState.NotInstalled
    )
    val state: StateFlow<ArabicModelInstallState> = _state.asStateFlow()

    val installedDirectory: File
        get() = installer.installedDirectory

    fun refresh() {
        _state.value = if (installer.isInstalled()) {
            ArabicModelInstallState.Ready
        } else {
            ArabicModelInstallState.NotInstalled
        }
    }

    suspend fun install() {
        if (_state.value is ArabicModelInstallState.Downloading) return
        _state.value = ArabicModelInstallState.Downloading(null)
        try {
            installer.install { progress ->
                _state.value = ArabicModelInstallState.Downloading(progress)
            }
            _state.value = ArabicModelInstallState.Ready
        } catch (error: Exception) {
            _state.value = ArabicModelInstallState.Failed(
                error.message ?: "تعذر تثبيت النموذج العربي"
            )
        }
    }
}
