import { api } from "./api";
import type { Commit, CommitDetail, FileContent, RepositoryTree, RepositoryMetrics } from "../types/git";

const base = (repositoryId: string) =>
  `/api/repositories/${encodeURIComponent(repositoryId)}/commits`;

export function getBranches(repositoryId: string): Promise<string[]> {
  return api.get<string[]>(`${base(repositoryId)}/branches`);
}

export function getCommits(repositoryId: string, branch?: string): Promise<Commit[]> {
  return api.get<Commit[]>(`${base(repositoryId)}${branch ? `?branch=${encodeURIComponent(branch)}` : ""}`);
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

export function getMetrics(repositoryId: string, branch?: string): Promise<RepositoryMetrics> {
  return api.get<RepositoryMetrics>(`${base(repositoryId)}/metrics${branch ? `?branch=${encodeURIComponent(branch)}` : ""}`);
}

export type MetricName = "activityOverTime" | "contributors" | "files" | "directories" | "filesChangedTogether";
export type MetricSummary = Pick<RepositoryMetrics, "totalCommits" | "contributorCount" | "firstCommitAt" | "lastCommitAt" | "repositoryAgeDays" | "totalAdditions" | "totalDeletions" | "codeChurn">;
export function getMetricSummary(repositoryId: string, branch?: string): Promise<MetricSummary> {
  return api.get<MetricSummary>(`${base(repositoryId)}/metrics/summary${branch ? `?branch=${encodeURIComponent(branch)}` : ""}`);
}
export function getMetric<K extends MetricName>(repositoryId: string, name: K, branch?: string): Promise<RepositoryMetrics[K]> {
  return api.get<RepositoryMetrics[K]>(`${base(repositoryId)}/metrics/${name}${branch ? `?branch=${encodeURIComponent(branch)}` : ""}`);
}
