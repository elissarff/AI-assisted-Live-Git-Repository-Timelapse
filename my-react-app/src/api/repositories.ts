import { API_BASE_URL } from "../config";
import type {
  RegisterRepositoryRequest,
  RegisteredRepository,
} from "../types/repository";

export async function registerRepository(
  request: RegisterRepositoryRequest
): Promise<RegisteredRepository> {
  const response = await fetch(`${API_BASE_URL}/api/repositories`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    const message = await response.text();
    throw new Error(
      message || `Repository registration failed (${response.status})`
    );
  }

  return response.json();
}

export async function getRepository(
  repoKey: string
): Promise<RegisteredRepository> {
  const response = await fetch(`${API_BASE_URL}/api/repositories/${repoKey}`);

  if (!response.ok) {
    throw new Error(`Could not load repository (${response.status})`);
  }

  return response.json();
}
