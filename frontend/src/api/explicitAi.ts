import { api } from "./api";
import type { AiCommitStatus } from "./aiAnalysis";
export type Classification = { sha: string; summary: string; category: string; tags: string[] };
export type Categories = { status: string; error?: string; total: number; classified: number; commits: Record<string, Classification> };
const base = (repo: string) => `/api/repositories/${encodeURIComponent(repo)}/ai`;
export const getCategories = (repo: string, branch: string) => api.get<Categories>(`${base(repo)}/categories?branch=${encodeURIComponent(branch)}`);
export const categorize = (repo: string, branch: string) => api.post<Categories>(`${base(repo)}/categories?branch=${encodeURIComponent(branch)}`);
export const explain = (repo: string, type: string, sha: string, path?: string, from?: string, to?: string, fromSha?: string, toSha?: string) => {
  const query = new URLSearchParams({ type, sha });
  if (path) query.set("path", path);
  if (from) query.set("from", from);
  if (to) query.set("to", to);
  if (fromSha) query.set("fromSha", fromSha);
  if (toSha) query.set("toSha", toSha);
  return api.post<AiCommitStatus>(`${base(repo)}/explain?${query}`);
};
