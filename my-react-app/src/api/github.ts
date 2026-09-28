import { api } from "./api";
import type {
  GitHubInstallation,
  GitHubRepository,
} from "../types/github";

export function connectInstallation(
  installationId: number
): Promise<GitHubInstallation> {
  return api.post<GitHubInstallation>(
    `/api/github/installations/${installationId}/connect`
  );
}

export function getInstallationRepositories(
  installationId: number
): Promise<GitHubRepository[]> {
  return api.get<GitHubRepository[]>(
    `/api/github/installations/${installationId}/repositories`
  );
}