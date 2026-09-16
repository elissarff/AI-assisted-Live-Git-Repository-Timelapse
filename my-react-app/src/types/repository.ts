export type RegisteredRepository = {
  repoKey: string;
  name: string;
  fullName?: string;
  remoteUrl?: string;
  defaultBranch?: string;
  monitoringType?: string;
  visibility?: string;
  headSha?: string;
  lastProcessedSha?: string;
};

export type RegisterRepositoryRequest = {
  remoteUrl: string;
  fullName: string;
  providerRepositoryId: number;
  monitoringType: "GITHUB_APP";
  visibility: "PRIVATE" | "PUBLIC";
  installationId: number;
};
