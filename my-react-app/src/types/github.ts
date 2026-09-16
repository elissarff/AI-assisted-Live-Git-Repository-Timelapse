export type GitHubRepository = {
  providerRepositoryId: number;
  fullName: string;
  name: string;
  privateRepository: boolean;
  defaultBranch: string;
  cloneUrl: string;
  installationId: number;
  accountId: number;
  accountLogin: string;
};

export type ConnectedInstallation = {
  installationId: number;
  accountId: number;
  accountLogin: string;
};
