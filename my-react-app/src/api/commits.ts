import { api } from "./api";
import type { Commit, CommitDetail, FileContent, RepositoryTree } from "../types/git";

const base = (repositoryId: string) =>
  `/api/repositories/${encodeURIComponent(repositoryId)}/commits`;

export function getCommits(repositoryId: string): Promise<Commit[]> {
  return api.get<Commit[]>(base(repositoryId));
}

export function getCommit(repositoryId: string, sha: string): Promise<CommitDetail> {
  return api.get<CommitDetail>(`${base(repositoryId)}/${encodeURIComponent(sha)}`);
}

export function getTree(repositoryId: string, sha: string): Promise<RepositoryTree> {
  return api.get<RepositoryTree>(`${base(repositoryId)}/${encodeURIComponent(sha)}/tree`);
}

export function getFile(
  repositoryId: string,
  sha: string,
  path: string
): Promise<FileContent> {
  return api.get<FileContent>(
    `${base(repositoryId)}/${encodeURIComponent(sha)}/file?path=${encodeURIComponent(path)}`
  );
}
