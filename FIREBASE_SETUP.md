# VELORA Firebase integration

This branch prepares the native Android app for Firebase. The Firebase project and OAuth configuration are intentionally not committed because they must be created and authorized by the project owner.

## Android identity
- Application ID: `com.aurevon.velora`
- Android namespace: `com.smartvoice.intelligence`

## Included SDKs
- Firebase Authentication: Google-only account authentication.
- Credential Manager + Google ID: native Google account picker.
- Cloud Firestore: future per-user sync for structured app data (tasks, events, notes, preferences).
- Firebase Cloud Messaging: push notifications.
- Firebase Crashlytics: crash diagnostics.
- Firebase Remote Config: remotely managed feature flags and safe configuration.
- Firebase App Check Play Integrity: app attestation for Firebase services.

Firebase Storage is deliberately not included. Audio recordings and transcripts remain on-device unless the owner explicitly approves a separate upload feature.

## Owner setup still required
1. Create a Firebase project named VELORA in the owner's Google account.
2. Register an Android app with package `com.aurevon.velora`.
3. Download `google-services.json` into `android/app/google-services.json` (do not commit it to a public repository).
4. Add the SHA-1 and SHA-256 fingerprints for the signing certificate(s) used by debug/release builds in Firebase project settings.
5. Enable Google in Authentication > Sign-in method and set the support email.
6. Download the updated `google-services.json` after enabling Google so it contains the OAuth client configuration.

When `android/app/google-services.json` exists, the Gradle build automatically applies the Google Services and Crashlytics plugins. Without it, the codebase remains build-configurable and no fake Firebase project identifiers are used.

## Security and implementation constraints
- Use Firebase Authentication UID as the per-user data boundary.
- Firestore rules must enforce `request.auth.uid == userId`; never rely on client-side filtering alone.
- Do not upload recordings, audio files, or transcripts by default.
- Do not place provider API secrets in the Android app.
- App Check enforcement must be enabled only after the production Play Integrity configuration is registered and verified.
- Remote Config must not be used to deliver secrets.
- Analytics is intentionally omitted until a separate privacy decision is made.

## Build note
Firebase SDK dependencies are added, but live authentication and cloud operations cannot be exercised until the Firebase project, Google provider, OAuth client configuration, and signing fingerprints are supplied. This setup document is not a claim that sign-in is already functional.
