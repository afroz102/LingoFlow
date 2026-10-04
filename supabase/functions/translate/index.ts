import { createHandler } from "./handler.ts";

function projectKey(dictionaryName: string, legacyName: string): string {
  const raw = Deno.env.get(dictionaryName);
  if (raw) {
    try {
      const keys = JSON.parse(raw);
      const value = keys.default ?? Object.values(keys)[0];
      if (typeof value === "string" && value) return value;
    } catch { /* Fall back to the platform's legacy key, if present. */ }
  }
  return Deno.env.get(legacyName) ?? "";
}

Deno.serve(createHandler({
  supabaseUrl: Deno.env.get("SUPABASE_URL") ?? "",
  anonKey: projectKey("SUPABASE_PUBLISHABLE_KEYS", "SUPABASE_ANON_KEY"),
  serviceKey: projectKey("SUPABASE_SECRET_KEYS", "SUPABASE_SERVICE_ROLE_KEY"),
  geminiKey: Deno.env.get("GEMINI_API_KEY") ?? "",
  model: Deno.env.get("GEMINI_MODEL") ?? "gemini-3.5-flash-lite",
}));
