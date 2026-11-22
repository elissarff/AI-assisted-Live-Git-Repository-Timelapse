import { api } from "./api";
export type AiCommitExplanation = {
  summary: string;
  purpose: string;
  category: string;
  significance: string;
  affectedComponents: string[];
  tags: string[];
};
export type AiCommitStatus = { status: "NOT_REQUESTED" | "PENDING" | "RUNNING" | "COMPLETED" | "FAILED"; error?: string; analysis?: AiCommitExplanation };
const url = (repo: string, sha: string) => `/api/repositories/${encodeURIComponent(repo)}/ai/commits/${encodeURIComponent(sha)}`;
export const getAiCommitAnalysis = (repo: string, sha: string) => api.get<AiCommitStatus>(url(repo, sha));
export const requestAiCommitAnalysis = (repo: string, sha: string, retry = false) => api.post<AiCommitStatus>(`${url(repo, sha)}/analyze?retry=${retry}`);
