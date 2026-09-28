export type MonitoringType =
  | "POLLING"
  | "WEBHOOK"
  | "GITHUB_APP";

export type RepositoryVisibility =
  | "PUBLIC"
  | "PRIVATE";

export type CreateRepositoryRequest = {
  remoteUrl: string;

  fullName?: string;

  providerRepositoryId?: number;

  monitoringType: MonitoringType;

  visibility: RepositoryVisibility;

  installationId?: number;
};

export type Repository = {
  id: string;
  name: string;
  remoteUrl: string;

  branch: string;

  fullName?: string;
  providerRepositoryId?: number;

  monitoringType: MonitoringType;
  visibility: RepositoryVisibility;

  installationId?: number;

  headSha?: string;
};