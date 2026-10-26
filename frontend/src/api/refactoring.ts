import { api } from "./api";
import type { RefactoringSettings } from "../types/refactoring";

const endpoint = (repoKey: string) => `/api/repositories/${encodeURIComponent(repoKey)}/refactoring/settings`;
export const getRefactoringSettings = (repoKey: string) => api.get<RefactoringSettings>(endpoint(repoKey));
export const updateRefactoringSettings = (repoKey: string, patch: RefactoringSettings) => api.patch<RefactoringSettings>(endpoint(repoKey), patch);
