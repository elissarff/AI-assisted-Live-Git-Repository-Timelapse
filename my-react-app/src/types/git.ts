export type Commit = {
  sha: string;
  message: string;
  author: string;
  timestamp: string;
};

export type FileChangeType = "ADD" | "MODIFY" | "DELETE" | "RENAME" | "COPY";

export type DiffLine = {
  type?: string;
  content?: string;
  oldLineNumber?: number | null;
  newLineNumber?: number | null;
  oldLine?: number | null;
  newLine?: number | null;
  line?: string;
};

export type DiffHunk = {
  header?: string;
  oldStart?: number;
  oldLines?: number;
  newStart?: number;
  newLines?: number;
  lines?: Array<DiffLine | string>;
  content?: string;
};

export type FileChange = {
  oldPath: string | null;
  newPath: string | null;
  changeType: FileChangeType;
  additions: number;
  deletions: number;
  binary: boolean;
  hunks: DiffHunk[];
};

export type CommitDetail = {
  sha: string;
  shortSha: string;
  message: string;
  author: { name?: string; email?: string } | null;
  timestamp: string;
  parentShas: string[];
  filesChanged: number;
  additions: number;
  deletions: number;
  files: FileChange[];
};

export type RepositoryTreeNode = {
  name: string;
  path: string;
  type: "DIRECTORY" | "FILE" | "SYMLINK" | "SUBMODULE" | string;
  children: RepositoryTreeNode[];
};

export type RepositoryTree = {
  commitSha: string;
  root: RepositoryTreeNode;
};

export type FileContent = {
  path: string;
  commitSha: string;
  size: number;
  binary: boolean;
  content: string | null;
};
