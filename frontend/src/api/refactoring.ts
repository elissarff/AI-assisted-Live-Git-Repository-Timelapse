import { api } from "./api";
import type { RefactoringSettings, RefactoringJob } from "../types/refactoring";

const endpoint = (repoKey: string) => `/api/repositories/${encodeURIComponent(repoKey)}/refactoring/settings`;
export const getRefactoringSettings = (repoKey: string) => api.get<RefactoringSettings>(endpoint(repoKey));
export const updateRefactoringSettings = (repoKey: string, patch: RefactoringSettings) => api.patch<RefactoringSettings>(endpoint(repoKey), patch);

const analysisEndpoint = (repoKey: string, sha: string) => `/api/repositories/${encodeURIComponent(repoKey)}/refactorings/${encodeURIComponent(sha)}`;
export const getRefactoringJob = (repoKey: string, sha: string) => api.get<RefactoringJob>(analysisEndpoint(repoKey, sha));
export const analyzeRefactoring = (repoKey: string, sha: string) => api.post<RefactoringJob>(analysisEndpoint(repoKey, sha));
export const retryRefactoring = (repoKey: string, sha: string) => api.post<RefactoringJob>(`${analysisEndpoint(repoKey, sha)}/retry`);
