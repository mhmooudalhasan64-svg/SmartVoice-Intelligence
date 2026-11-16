# VELORA Remote Configuration

VELORA uses Firebase Remote Config for server-controlled settings of capabilities
that already exist in the installed Android app. Remote Config changes settings;
it does not download, compile, or execute new application code.

## Parameters

| Parameter | Default | Purpose |
| --- | --- | --- |
| `voice_message_analysis_enabled` | `true` | Temporarily enable or disable voice-message analysis. |
| `background_model_downloads_enabled` | `true` | Enable or pause starting new Arabic model downloads. |
| `maintenance_mode` | `false` | Reserved for a future maintenance screen; currently informational only. |
| `announcement_title` | empty | Reserved for a future in-app announcement. |
| `announcement_body` | empty | Reserved for a future in-app announcement. |
| `latest_version_name` | `0.1.0` | Reserved for an in-app update notice. |
| `minimum_supported_version_code` | `1` | Reserved for a future compatibility check. |

## Runtime behavior

- Defaults are applied before fetching, so the app retains safe behavior offline.
- The last activated values are cached by the Firebase SDK.
- Debug builds may fetch immediately; release builds use a one-hour minimum interval.
- A failed fetch does not block startup.
- Voice analysis and new model-download actions honor their respective flags.
- An already-running WorkManager download is not cancelled by changing the flag.

## Publishing changes

Create these parameters in Firebase Console → Remote Config using the exact keys
above, then publish. Values affect compatible installed app versions after their
next successful Remote Config fetch.

## Release boundaries

Remote Config can update content, feature flags, announcements, and server-driven
configuration. Changes to Kotlin/Android code, screens compiled into the APK,
permissions, native libraries, or application identifiers still require a new
signed APK. Never place API secrets or private credentials in Remote Config.
