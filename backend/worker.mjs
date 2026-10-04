import { handleRequest } from './translation.mjs';
import { reserveD1Quota } from './quota.mjs';

export default {
  async fetch(request, env) {
    return handleRequest(request, {
      apiKey: env.GEMINI_API_KEY,
      model: env.GEMINI_MODEL ?? 'gemini-3.5-flash-lite',
      consumeQuota: () => reserveD1Quota(env.DB),
      checkDatabase: () => env.DB.prepare('SELECT day FROM translation_usage LIMIT 1').all(),
    });
  },
};
