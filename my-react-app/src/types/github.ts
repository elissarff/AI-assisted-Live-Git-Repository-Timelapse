export type GitHubInstallation = {
  installationId: number;
  accountId: number;
  accountLogin: string;
};

export type GitHubRepository = {
  providerRepositoryId: number;
  fullName: string;
  name: string;
  privateRepository: boolean;
  defaultBranch: string;
  cloneUrl: string;
  htmlUrl: string;
};