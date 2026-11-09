import { api } from "./api";
import type { RefactoringSettings, RefactoringJob } from "../types/refactoring";

const endpoint = (repoKey: string) => `/api/repositories/${encodeURIComponent(repoKey)}/refactoring/settings`;
export const getRefactoringSettings = (repoKey: string) => api.get<RefactoringSettings>(endpoint(repoKey));
export const updateRefactoringSettings = (repoKey: string, patch: RefactoringSettings) => api.patch<RefactoringSettings>(endpoint(repoKey), patch);

const analysisEndpoint = (repoKey: string, sha: string) => `/api/repositories/${encodeURIComponent(repoKey)}/refactorings/${encodeURIComponent(sha)}`;
export const getRefactoringJob = (repoKey: string, sha: string) => api.get<RefactoringJob>(analysisEndpoint(repoKey, sha));
export const analyzeRefactoring = (repoKey: string, sha: string) => api.post<RefactoringJob>(analysisEndpoint(repoKey, sha));
export const retryRefactoring = (repoKey: string, sha: string) => api.post<RefactoringJob>(`${analysisEndpoint(repoKey, sha)}/retry`);

export type RefactoringCoverage = { total: number; completed: number; failed: number; pending: number; commits: Array<{commit_sha:string;status:string;refactoring_count:number}> };
export const getRefactoringCoverage = (repoKey:string) => api.get<RefactoringCoverage>(`/api/repositories/${encodeURIComponent(repoKey)}/refactorings/coverage`);
export const startRefactoringHistory = (repoKey:string, preferredSha?:string) => api.post<RefactoringCoverage>(`/api/repositories/${encodeURIComponent(repoKey)}/refactorings/history${preferredSha ? `?preferredSha=${encodeURIComponent(preferredSha)}` : ""}`);
