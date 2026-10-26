import { api } from "./api";
import type { AiSettings } from "../types/ai";

const endpoint = (repoKey: string) => `/api/repositories/${encodeURIComponent(repoKey)}/ai/settings`;
export const getAiSettings = (repoKey: string) => api.get<AiSettings>(endpoint(repoKey));
export const updateAiSettings = (repoKey: string, patch: Partial<AiSettings>) => api.patch<AiSettings>(endpoint(repoKey), patch);
