import { validateChatRequest } from "../lib/validation.js";

const WINDOW_MS = 60_000;
const MAX_REQUESTS_PER_WINDOW = 20;
const ipBuckets = new Map();

const SYSTEM_PROMPT = [
  "You are ORIN, the AI assistant inside VELORA by AUREVON.",
  "Respond in the language and dialect used by the user; use clear Modern Standard Arabic when appropriate.",
  "Be accurate, structured, and explicit about uncertainty. Never claim to have read a file or performed an action unless its content or result was actually provided.",
  "You are a general-purpose assistant for explanation, analysis, writing, translation, study support, mathematics, and programming.",
].join(" ");

function getClientIp(request) {
  const forwarded = request.headers["x-forwarded-for"];
  if (typeof forwarded === "string" && forwarded.length > 0) {
    return forwarded.split(",")[0].trim().slice(0, 80);
  }
  return String(request.socket?.remoteAddress || "unknown").slice(0, 80);
}

function isRateLimited(ip) {
  const now = Date.now();
  for (const [key, bucket] of ipBuckets) {
    if (now - bucket.startedAt >= WINDOW_MS) ipBuckets.delete(key);
  }
  const bucket = ipBuckets.get(ip);
  if (!bucket || now - bucket.startedAt >= WINDOW_MS) {
    ipBuckets.set(ip, { startedAt: now, count: 1 });
    return false;
  }
  if (bucket.count >= MAX_REQUESTS_PER_WINDOW) return true;
  bucket.count += 1;
  return false;
}

export default async function handler(request, response) {
  response.setHeader("Cache-Control", "no-store");
  response.setHeader("X-Content-Type-Options", "nosniff");

  if (request.method !== "POST") {
    response.setHeader("Allow", "POST");
    return response.status(405).json({ error: "Method not allowed." });
  }

  const apiKey = process.env.OPENROUTER_API_KEY;
  if (!apiKey) {
    return response.status(503).json({ error: "ORIN service is not configured yet." });
  }

  const ip = getClientIp(request);
  if (isRateLimited(ip)) {
    return response.status(429).json({ error: "Too many requests. Please try again shortly." });
  }

  const validated = validateChatRequest(request.body);
  if (!validated.ok) {
    return response.status(400).json({ error: validated.error });
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 45_000);

  try {
    const upstream = await fetch("https://openrouter.ai/api/v1/chat/completions", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${apiKey}`,
        "Content-Type": "application/json",
        "HTTP-Referer": process.env.ORIN_SITE_URL || "https://velora.aurevon.app",
        "X-Title": "VELORA ORIN",
      },
      body: JSON.stringify({
        model: process.env.ORIN_MODEL || "openai/gpt-4o-mini",
        messages: [
          { role: "system", content: SYSTEM_PROMPT },
          ...validated.messages,
        ],
        temperature: 0.4,
        max_tokens: 1800,
      }),
      signal: controller.signal,
    });

    if (!upstream.ok) {
      // Do not expose provider response bodies or credentials to the app.
      const status = upstream.status === 429 ? 503 : 502;
      return response.status(status).json({
        error: upstream.status === 429
          ? "AI provider is busy. Please try again shortly."
          : "The AI provider could not complete the request.",
      });
    }

    const data = await upstream.json();
    const answer = data?.choices?.[0]?.message?.content;
    if (typeof answer !== "string" || answer.trim().length === 0) {
      return response.status(502).json({ error: "The AI provider returned an empty response." });
    }

    return response.status(200).json({
      message: { role: "assistant", content: answer.trim() },
      model: data.model || process.env.ORIN_MODEL || "openai/gpt-4o-mini",
    });
  } catch (error) {
    const timedOut = error?.name === "AbortError";
    return response.status(timedOut ? 504 : 502).json({
      error: timedOut ? "The AI request timed out." : "Unable to reach the AI provider.",
    });
  } finally {
    clearTimeout(timeout);
  }
}
