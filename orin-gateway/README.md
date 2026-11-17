# ORIN Gateway

A standalone Vercel Node.js API project for the VELORA assistant. Set the Vercel project **Root Directory** to `orin-gateway` when the project is created. This folder is intentionally isolated from the Android Gradle project.

## Endpoints

- `GET /api/health` — reports whether the server-side provider key is configured. It never returns the key.
- `POST /api/chat` — accepts a bounded text conversation and returns one assistant message.

Example request:

```json
{
  "messages": [
    { "role": "user", "content": "اشرح لي قانون نيوتن الأول." }
  ]
}
```

## Environment variables

Configure these in the Vercel project settings only:

- `OPENROUTER_API_KEY` — required, secret.
- `ORIN_MODEL` — optional; defaults to `openai/gpt-4o-mini`.
- `ORIN_SITE_URL` — optional provider attribution URL.

Never place provider keys in the Android app, source code, GitHub files, or chat messages.

## Local checks

Requires Node.js 20 or later:

```sh
npm test
```

## Current security boundary

The endpoint validates roles and input sizes, sets a provider timeout, avoids returning upstream error bodies, and applies a small in-memory per-IP request limit. The in-memory limiter is best-effort only: serverless instances do not share memory. Before public production use, add a shared rate-limit store (for example, Redis/Upstash), stronger abuse controls, and an app integrity strategy. This gateway does not implement accounts, authentication, persistent chat storage, file uploads, or OCR.

Requests are forwarded to the configured AI provider for inference. Do not send private or sensitive content unless the user has explicitly chosen to submit it.
