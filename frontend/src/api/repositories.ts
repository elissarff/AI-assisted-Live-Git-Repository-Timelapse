import { api } from "./api";
import type {
  CreateRepositoryRequest,
  Repository,
} from "../types/repository";

export function registerRepository(
  request: CreateRepositoryRequest
): Promise<Repository> {
  return api.post<Repository>(
    "/api/repositories",
    request
  );
}

export function getRepository(
  repoKey: string
): Promise<Repository> {
  return api.get<Repository>(
    `/api/repositories/${repoKey}`
  );
}

export function syncRepository(
  repoKey: string
) {
  return api.post(
    `/api/repositories/${repoKey}/sync`
  );
}