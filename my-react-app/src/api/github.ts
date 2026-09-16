import { API_BASE_URL } from "../config";
import type { ConnectedInstallation, GitHubRepository } from "../types/github";

export async function connectInstallation(
  installationId: number
): Promise<ConnectedInstallation> {
  const response = await fetch(
    `${API_BASE_URL}/api/github/installations/${installationId}/connect`,
    { method: "POST" }
  );

  if (!response.ok) {
    throw new Error(
      `Could not register GitHub installation (${response.status})`
    );
  }

  return response.json();
}

export async function getInstallationRepositories(
  installationId: number
): Promise<GitHubRepository[]> {
  const response = await fetch(
    `${API_BASE_URL}/api/github/installations/${installationId}/repositories`
  );

  if (!response.ok) {
    throw new Error(
      `Could not load GitHub repositories (${response.status})`
    );
  }

  return response.json();
}
