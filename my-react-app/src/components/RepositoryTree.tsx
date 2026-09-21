import type { RepositoryTreeNode } from "../types/git";

type Props = {
  node: RepositoryTreeNode;
  onFileClick: (node: RepositoryTreeNode) => void;
  selectedPath?: string;
  depth?: number;
};

export default function RepositoryTree({ node, onFileClick, selectedPath, depth = 0 }: Props) {
  const isDirectory = node.type === "DIRECTORY";
  const isRoot = depth === 0 && !node.name;

  return (
    <div>
      {!isRoot && (
        <div className="tree-row" style={{ paddingLeft: `${depth * 18}px` }}>
          {isDirectory ? (
            <span>DIR&nbsp; {node.name}/</span>
          ) : (
            <button
              type="button"
              className={`file-link ${selectedPath === node.path ? "selected" : ""}`}
              onClick={() => onFileClick(node)}
              disabled={node.type === "SUBMODULE"}
              title={node.path}
            >
              FILE {node.name}
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
          depth={isRoot ? depth : depth + 1}
        />
      ))}
    </div>
  );
}
