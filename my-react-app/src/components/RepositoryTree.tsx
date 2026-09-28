import type { FileChange, RepositoryTreeNode } from "../types/git";

type Props = {
  node: RepositoryTreeNode;
  onFileClick: (node: RepositoryTreeNode, change?: FileChange) => void;
  selectedPath?: string;
  changes?: FileChange[];
  depth?: number;
};

function changeForPath(path: string, changes: FileChange[]) {
  return changes.find((change) => change.newPath === path || change.oldPath === path);
}

function parentPath(path: string) {
  const slash = path.lastIndexOf("/");
  return slash === -1 ? "" : path.slice(0, slash);
}

function deletedDirectlyUnder(path: string, changes: FileChange[]) {
  return changes.filter(
    (change) => change.changeType === "DELETE" && change.oldPath && parentPath(change.oldPath) === path,
  );
}

function directoryHasChanges(path: string, changes: FileChange[]) {
  const prefix = path ? `${path}/` : "";
  return changes.some((change) =>
    Boolean(change.newPath?.startsWith(prefix) || change.oldPath?.startsWith(prefix)),
  );
}

export default function RepositoryTree({ node, onFileClick, selectedPath, changes = [], depth = 0 }: Props) {
  const isDirectory = node.type === "DIRECTORY";
  const isRoot = depth === 0 && !node.name;
  const change = !isDirectory ? changeForPath(node.path, changes) : undefined;
  const directoryChanged = isDirectory && directoryHasChanges(node.path, changes);
  const changeClass = change ? `change-${change.changeType.toLowerCase()}` : "";

  return (
    <div>
      {!isRoot && (
        <div
          className={`tree-row ${changeClass} ${directoryChanged ? "directory-changed" : ""}`}
          style={{ paddingLeft: `${depth * 18}px` }}
        >
          {isDirectory ? (
            <span className="directory-label"><span aria-hidden="true">▾</span> {node.name}/</span>
          ) : (
            <button
              type="button"
              className={`file-link ${selectedPath === node.path ? "selected" : ""}`}
              onClick={() => onFileClick(node, change)}
              disabled={node.type === "SUBMODULE"}
              title={node.path}
            >
              <span className="file-icon" aria-hidden="true">•</span> {node.name}
              {change && <span className={`change-badge ${changeClass}`}>{change.changeType}</span>}
            </button>
          )}
        </div>
      )}
      {node.children?.map((child) => (
        <RepositoryTree
          key={`${child.type}:${child.path}`}
          node={child}
          onFileClick={onFileClick}
          selectedPath={selectedPath}
          changes={changes}
          depth={isRoot ? depth : depth + 1}
        />
      ))}
      {isDirectory && deletedDirectlyUnder(node.path, changes).map((change) => {
        const path = change.oldPath!;
        const name = path.split("/").pop() ?? path;
        const ghostDepth = isRoot ? depth : depth + 1;
        return (
          <div
            key={`deleted:${path}`}
            className="tree-row change-delete deleted-ghost"
            style={{ paddingLeft: `${ghostDepth * 18}px` }}
            title={`${path} was deleted in this commit`}
          >
            <button
              type="button"
              className={`file-link deleted-file ${selectedPath === path ? "selected" : ""}`}
              onClick={() => onFileClick({ name, path, type: "FILE", children: [] }, change)}
            >
              <span className="file-icon" aria-hidden="true">×</span> {name}
              <span className="change-badge change-delete">DELETE</span>
            </button>
          </div>
        );
      })}
    </div>
  );
}
