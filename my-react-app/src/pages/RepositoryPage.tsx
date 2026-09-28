import { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { getRepository, syncRepository } from "../api/repositories";
import { getCommit, getCommits, getFile, getTree } from "../api/commits";
import RepositoryTree from "../components/RepositoryTree";
import FileDiff from "../components/FileDiff";
import TimeLine from "../components/TimeLine";
import type { Repository } from "../types/repository";
import type { Commit, CommitDetail, FileChange, FileContent, RepositoryTree as Tree, RepositoryTreeNode } from "../types/git";

const BASE_PLAY_INTERVAL_MS = 1200;

export default function RepositoryPage() {
  const { repoKey } = useParams();
  const [repository, setRepository] = useState<Repository | null>(null);
  const [commits, setCommits] = useState<Commit[]>([]);
  const [index, setIndex] = useState(0);
  const [detail, setDetail] = useState<CommitDetail | null>(null);
  const [tree, setTree] = useState<Tree | null>(null);
  const [file, setFile] = useState<FileContent | null>(null);
  const [beforeFile, setBeforeFile] = useState<FileContent | null>(null);
  const [selectedPath, setSelectedPath] = useState<string | null>(null);
  const [selectedChange, setSelectedChange] = useState<FileChange | null>(null);
  const [playing, setPlaying] = useState(false);
  const [speed, setSpeed] = useState(1);
  const [loadingFrame, setLoadingFrame] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const selected = commits[index] ?? null;
  console.log(selectedChange);
  

  const loadHistory = useCallback(async (repositoryId: string, preferSha?: string) => {
    const history = await getCommits(repositoryId);
    setCommits(history);
    if (!history.length) {
      setIndex(0);
      return;
    }
    const preferred = preferSha ? history.findIndex((c) => c.sha === preferSha) : -1;
    setIndex(preferred >= 0 ? preferred : history.length - 1);
  }, []);

  useEffect(() => {
    if (!repoKey) {
      setError("Missing repository key.");
      return;
    }
    setError(null);
    Promise.all([getRepository(repoKey), getCommits(repoKey)])
      .then(([repo, history]) => {
        setRepository(repo as Repository);
        setCommits(history);
        setIndex(history.length ? history.length - 1 : 0);
      })
      .catch((err) => setError(err instanceof Error ? err.message : "Could not load repository."));
  }, [repoKey]);

  useEffect(() => {
    if (!repoKey || !selected) {
      setDetail(null);
      setTree(null);
      setFile(null);
      setBeforeFile(null);
      setSelectedPath(null);
      setSelectedChange(null);
      return;
    }
    let cancelled = false;
    setLoadingFrame(true);
    setFile(null);
    setBeforeFile(null);
    setSelectedPath(null);
    setSelectedChange(null);
    setError(null);
    Promise.all([getCommit(repoKey, selected.sha), getTree(repoKey, selected.sha)])
      .then(([commitDetail, repositoryTree]) => {
        if (!cancelled) {
          setDetail(commitDetail);
          setTree(repositoryTree);
        }
      })
      .catch((err) => !cancelled && setError(err instanceof Error ? err.message : "Could not load commit."))
      .finally(() => !cancelled && setLoadingFrame(false));
    return () => { cancelled = true; };
  }, [repoKey, selected?.sha]);

  useEffect(() => {
    if (!playing || commits.length < 2) return;
    const timer = window.setInterval(() => {
      setIndex((current) => {
        if (current >= commits.length - 1) {
          setPlaying(false);
          return current;
        }
        return current + 1;
      });
    }, BASE_PLAY_INTERVAL_MS / speed);
    return () => window.clearInterval(timer);
  }, [playing, commits.length, speed]);

  const openFile = async (node: RepositoryTreeNode, explicitChange?: FileChange) => {
    if (!repoKey || !selected || node.type === "DIRECTORY") return;
    const change = explicitChange ?? detail?.files.find(
      (item) => item.newPath === node.path || item.oldPath === node.path,
    ) ?? null;

    setSelectedPath(node.path);
    setSelectedChange(change);
    setFile(null);
    setBeforeFile(null);
    setError(null);

    try {
      const parentSha = detail?.parentShas?.[0];
      const afterPath = change?.newPath ?? node.path;
      const beforePath = change?.oldPath ?? node.path;

      // Load both real Git blobs. Added lines come from the selected commit;
      // deleted lines come from its first parent.
      const [after, before] = await Promise.all([
        change?.changeType === "DELETE"
          ? Promise.resolve(null)
          : getFile(repoKey, selected.sha, afterPath),
        !parentSha || change?.changeType === "ADD"
          ? Promise.resolve(null)
          : getFile(repoKey, parentSha, beforePath),
      ]);

      setFile(after);
      setBeforeFile(before);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load file versions.");
    }
  };

  const sync = async () => {
    if (!repoKey) return;
    setSyncing(true);
    setPlaying(false);
    setError(null);
    try {
      await syncRepository(repoKey);
      const repo = await getRepository(repoKey);
      setRepository(repo as Repository);
      await loadHistory(repoKey, repo.headSha);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not synchronize repository.");
    } finally {
      setSyncing(false);
    }
  };

  return (
    <main className="page extraction-page">
      <section className="card extraction-card">
        <Link to="/repositories">Back to repositories</Link>
        <h1>{repository?.name ?? "Repository Git Extraction"}</h1>
        <p className="muted">Basic extraction UI only. The selected commit drives both the repository tree and file reads.</p>

        {error && <p className="error">{error}</p>}
        {!repository && !error && <p>Loading repository...</p>}

        {repository && (
          <>
            <div className="toolbar">
              <button onClick={sync} disabled={syncing}>{syncing ? "Syncing..." : "Sync repository"}</button>
              <span>Branch: <strong>{repository.branch || "-"}</strong></span>
              <span>HEAD: <code>{repository.headSha || "-"}</code></span>
            </div>

            <hr />
            <h2>Repository timelapse</h2>
            <p className="muted">Replay the repository from its earliest extracted commit to the current state. Scrub to inspect any frame.</p>
            <TimeLine
              commits={commits}
              currentIndex={index}
              playing={playing}
              speed={speed}
              onIndexChange={setIndex}
              onPlayingChange={setPlaying}
              onSpeedChange={setSpeed}
            />

            {selected && (
              <section className="extract-section">
                <h2>Selected commit</h2>
                <p><code>{selected.sha}</code></p>
                <p><strong>{selected.message}</strong></p>
                <p>{selected.author} - {new Date(selected.timestamp).toLocaleString()}</p>
                {loadingFrame && <p>Extracting commit + tree...</p>}

                {detail && (
                  <>
                    <p>Files changed: {detail.filesChanged} | +{detail.additions} / -{detail.deletions}</p>
                    <h3>Changes</h3>
                    {detail.files.length === 0 ? <p>No file changes.</p> : (
                      <ul className="change-list">
                        {detail.files.map((change, i) => (
                          <li key={`${change.oldPath}-${change.newPath}-${i}`}>
                            <strong>{change.changeType}</strong>{" "}
                            <code>{change.changeType === "RENAME" ? `${change.oldPath} -> ${change.newPath}` : (change.newPath || change.oldPath)}</code>{" "}
                            (+{change.additions}/-{change.deletions}){change.binary ? " [binary]" : ""}
                          </li>
                        ))}
                      </ul>
                    )}
                  </>
                )}
              </section>
            )}

            <div className="extract-grid">
              <section className="extract-section">
                <h2>Repository tree at commit</h2>
                <p className="muted">Click a file to request its content at this exact SHA.</p>
                {tree ? <RepositoryTree node={tree.root} onFileClick={openFile} selectedPath={selectedPath ?? undefined} changes={detail?.files ?? []} /> : <p>No tree loaded.</p>}
              </section>

              <section className="extract-section">
                <h2>{selectedChange ? "File evolution at commit" : "File at commit"}</h2>
                {!selectedPath ? <p>Select a file from the tree.</p> : (
                  <>
                    <p><code>{selectedPath}</code></p>
                    {selectedChange ? (
                      <>
                        <p className="muted">
                          <strong>{selectedChange.changeType}</strong> · +{selectedChange.additions} / -{selectedChange.deletions}
                          {selectedChange.changeType === "RENAME" && selectedChange.oldPath ? ` · from ${selectedChange.oldPath}` : ""}
                        </p>
                        <FileDiff
                          change={selectedChange}
                          beforeContent={beforeFile?.content}
                          afterContent={file?.content}
                        />
                      </>
                    ) : file ? (
                      <>
                        <p className="muted">{file.size} bytes | {file.binary ? "binary" : "text"} | {file.commitSha.slice(0, 12)}</p>
                        {file.binary ? <p>Binary content is not displayed.</p> : <pre className="file-content">{file.content ?? ""}</pre>}
                      </>
                    ) : <p>Loading file...</p>}
                  </>
                )}
              </section>
            </div>
          </>
        )}
      </section>
    </main>
  );
}
