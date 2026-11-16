package com.aurevon.velora.config

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Remote, server-controlled settings for capabilities that are already present
 * in the installed app. This deliberately does not download or execute code.
 *
 * Firebase defaults keep the app functional when Remote Config is unavailable.
 */
data class VeloraRemoteSettings(
    val voiceMessageAnalysisEnabled: Boolean = true,
    val backgroundModelDownloadsEnabled: Boolean = true,
    val maintenanceMode: Boolean = false,
    val announcementTitle: String = "",
    val announcementBody: String = "",
    val latestVersionName: String = "0.1.0",
    val minimumSupportedVersionCode: Long = 1L
)

class RemoteConfigRepository {
    private val remoteConfig = FirebaseRemoteConfig.getInstance()

    private val _settings = MutableStateFlow(VeloraRemoteSettings())
    val settings: StateFlow<VeloraRemoteSettings> = _settings.asStateFlow()


    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        val defaults = mapOf(
            KEY_VOICE_ANALYSIS to true,
            KEY_BACKGROUND_DOWNLOADS to true,
            KEY_MAINTENANCE_MODE to false,
            KEY_ANNOUNCEMENT_TITLE to "",
            KEY_ANNOUNCEMENT_BODY to "",
            KEY_LATEST_VERSION to "0.1.0",
            KEY_MIN_VERSION_CODE to 1L
        )

        try {
            remoteConfig.setDefaultsAsync(defaults).await()
            val settings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(
                    if (com.aurevon.velora.BuildConfig.DEBUG) 0 else 3600
                )
                .build()
            remoteConfig.setConfigSettingsAsync(settings).await()
            remoteConfig.fetchAndActivate().await()
            _settings.value = readSettings()
            true
        } catch (_: Exception) {
            // Retain the last known settings; built-in defaults remain safe offline.
            _settings.value = readSettings()
            false
        }
    }

    private fun readSettings() = VeloraRemoteSettings(
        voiceMessageAnalysisEnabled = remoteConfig.getBoolean(KEY_VOICE_ANALYSIS),
        backgroundModelDownloadsEnabled = remoteConfig.getBoolean(KEY_BACKGROUND_DOWNLOADS),
        maintenanceMode = remoteConfig.getBoolean(KEY_MAINTENANCE_MODE),
        announcementTitle = remoteConfig.getString(KEY_ANNOUNCEMENT_TITLE),
        announcementBody = remoteConfig.getString(KEY_ANNOUNCEMENT_BODY),
        latestVersionName = remoteConfig.getString(KEY_LATEST_VERSION),
        minimumSupportedVersionCode = remoteConfig.getLong(KEY_MIN_VERSION_CODE)
    )

    companion object {
        const val KEY_VOICE_ANALYSIS = "voice_message_analysis_enabled"
        const val KEY_BACKGROUND_DOWNLOADS = "background_model_downloads_enabled"
        const val KEY_MAINTENANCE_MODE = "maintenance_mode"
        const val KEY_ANNOUNCEMENT_TITLE = "announcement_title"
        const val KEY_ANNOUNCEMENT_BODY = "announcement_body"
        const val KEY_LATEST_VERSION = "latest_version_name"
        const val KEY_MIN_VERSION_CODE = "minimum_supported_version_code"
    }
}
