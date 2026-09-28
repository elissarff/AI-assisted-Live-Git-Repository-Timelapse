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
  const [loadingFile, setLoadingFile] = useState(false);
  const [fileUnavailable, setFileUnavailable] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const selected = commits[index] ?? null;

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
      return;
    }
    let cancelled = false;
    setLoadingFrame(true);
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

  // File selection is intentionally independent from the timeline. Advancing
  // commits changes the version being displayed, not which file the user chose.
  const openFile = (node: RepositoryTreeNode, explicitChange?: FileChange) => {
    if (node.type === "DIRECTORY") return;
    setSelectedPath(node.path);
    setSelectedChange(explicitChange ?? null);
  };

  const backToTree = () => {
    setSelectedPath(null);
    setSelectedChange(null);
    setFile(null);
    setBeforeFile(null);
    setFileUnavailable(false);
  };

  useEffect(() => {
    if (!repoKey || !selected || !selectedPath || !detail) return;

    let cancelled = false;
    const change = detail.files.find(
      (item) => item.newPath === selectedPath || item.oldPath === selectedPath,
    ) ?? null;

    // Follow a rename while preserving the user's logical file selection.
    const effectivePath = change?.changeType === "RENAME" && change.oldPath === selectedPath && change.newPath
      ? change.newPath
      : selectedPath;

    if (effectivePath !== selectedPath) {
      setSelectedPath(effectivePath);
      return;
    }

    setSelectedChange(change);
    setFile(null);
    setBeforeFile(null);
    setFileUnavailable(false);
    setLoadingFile(true);

    const parentSha = detail.parentShas?.[0];
    const afterPath = change?.newPath ?? effectivePath;
    const beforePath = change?.oldPath ?? effectivePath;

    Promise.all([
      change?.changeType === "DELETE"
        ? Promise.resolve(null)
        : getFile(repoKey, selected.sha, afterPath).catch(() => null),
      !parentSha || change?.changeType === "ADD"
        ? Promise.resolve(null)
        : getFile(repoKey, parentSha, beforePath).catch(() => null),
    ]).then(([after, before]) => {
      if (cancelled) return;
      setFile(after);
      setBeforeFile(before);
      setFileUnavailable(!after && !before && change?.changeType !== "DELETE");
    }).finally(() => {
      if (!cancelled) setLoadingFile(false);
    });

    return () => { cancelled = true; };
  }, [repoKey, selected?.sha, selectedPath, detail]);

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
            <section className="timelapse-bottom">
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
                <div className="commit-bottom-details">
                  <div className="commit-bottom-message">
                    <span className="timelapse-kicker">COMMIT</span>
                    <strong>{selected.message}</strong>
                  </div>
                  <code title={selected.sha}>{selected.sha.slice(0, 12)}</code>
                  <span>{selected.author}</span>
                  <span>{new Date(selected.timestamp).toLocaleString()}</span>
                  {detail && (
                    <span className="commit-stats">
                      {detail.filesChanged} files · <b>+{detail.additions}</b> / <b>-{detail.deletions}</b>
                    </span>
                  )}
                </div>
              )}
            </section>

            <section className="extract-section timelapse-viewport">
              {!selectedPath ? (
                <>
                  <div className="viewport-heading">
                    <div>
                      <h2>Repository tree</h2>
                      <p className="muted">Choose a file to follow it while the timeline continues through commits.</p>
                    </div>
                    {loadingFrame && <span className="muted">Loading commit…</span>}
                  </div>
                  {tree ? (
                    <RepositoryTree
                      node={tree.root}
                      onFileClick={openFile}
                      changes={detail?.files ?? []}
                    />
                  ) : <p>No tree loaded.</p>}
                </>
              ) : (
                <>
                  <div className="file-view-header">
                    <button type="button" className="back-to-tree" onClick={backToTree}>← Repository tree</button>
                    <code>{selectedPath}</code>
                  </div>

                  {loadingFile ? <p>Loading this file at the selected commit...</p> : selectedChange ? (
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
                      <p className="muted">Unchanged in this commit · {file.size} bytes · {file.commitSha.slice(0, 12)}</p>
                      <FileDiff content={file.content} binary={file.binary} />
                    </>
                  ) : fileUnavailable ? (
                    <div className="file-unavailable">
                      <h3>File does not exist at this point in history</h3>
                      <p className="muted">The timeline can keep playing. This file view will populate when the path exists in a later commit.</p>
                    </div>
                  ) : (
                    <p>No file content at this commit.</p>
                  )}
                </>
              )}
            </section>

            
          </>
        )}
      </section>
    </main>
  );
}
