export const MAX_MESSAGES = 24;
export const MAX_MESSAGE_CHARS = 12000;
export const MAX_TOTAL_CHARS = 50000;

export function validateChatRequest(body) {
  if (!body || typeof body !== "object" || Array.isArray(body)) {
    return { ok: false, error: "Invalid request body." };
  }

  if (!Array.isArray(body.messages) || body.messages.length === 0 || body.messages.length > MAX_MESSAGES) {
    return { ok: false, error: `messages must contain 1 to ${MAX_MESSAGES} items.` };
  }

  let totalChars = 0;
  const messages = [];

  for (const item of body.messages) {
    if (!item || typeof item !== "object" || !["user", "assistant"].includes(item.role)) {
      return { ok: false, error: "Each message must have role user or assistant." };
    }
    if (typeof item.content !== "string" || item.content.trim().length === 0) {
      return { ok: false, error: "Message content must be a non-empty string." };
    }

    const content = item.content.trim();
    if (content.length > MAX_MESSAGE_CHARS) {
      return { ok: false, error: `Each message is limited to ${MAX_MESSAGE_CHARS} characters.` };
    }
    totalChars += content.length;
    messages.push({ role: item.role, content });
  }

  if (totalChars > MAX_TOTAL_CHARS) {
    return { ok: false, error: `Conversation is limited to ${MAX_TOTAL_CHARS} characters.` };
  }

  if (messages[messages.length - 1].role !== "user") {
    return { ok: false, error: "The last message must be from the user." };
  }

  return { ok: true, messages };
}
