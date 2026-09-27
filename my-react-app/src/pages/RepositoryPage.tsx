import { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { getRepository, syncRepository } from "../api/repositories";
import { getCommit, getCommits, getFile, getTree } from "../api/commits";
import RepositoryTree from "../components/RepositoryTree";
import TimeLine from "../components/TimeLine";
import type { Repository } from "../types/repository";
import type { Commit, CommitDetail, FileContent, RepositoryTree as Tree, RepositoryTreeNode } from "../types/git";

const BASE_PLAY_INTERVAL_MS = 1200;

export default function RepositoryPage() {
  const { repoKey } = useParams();
  const [repository, setRepository] = useState<Repository | null>(null);
  const [commits, setCommits] = useState<Commit[]>([]);
  const [index, setIndex] = useState(0);
  const [detail, setDetail] = useState<CommitDetail | null>(null);
  const [tree, setTree] = useState<Tree | null>(null);
  const [file, setFile] = useState<FileContent | null>(null);
  const [playing, setPlaying] = useState(false);
  const [speed, setSpeed] = useState(1);
  const [loadingFrame, setLoadingFrame] = useState(false);
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
      setFile(null);
      return;
    }
    let cancelled = false;
    setLoadingFrame(true);
    setFile(null);
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

  const openFile = async (node: RepositoryTreeNode) => {
    if (!repoKey || !selected || node.type === "DIRECTORY") return;
    setError(null);
    try {
      setFile(await getFile(repoKey, selected.sha, node.path));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not load file.");
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
                {tree ? <RepositoryTree node={tree.root} onFileClick={openFile} selectedPath={file?.path} changes={detail?.files ?? []} /> : <p>No tree loaded.</p>}
              </section>

              <section className="extract-section">
                <h2>File at commit</h2>
                {!file ? <p>Select a file from the tree.</p> : (
                  <>
                    <p><code>{file.path}</code></p>
                    <p className="muted">{file.size} bytes | {file.binary ? "binary" : "text"} | {file.commitSha.slice(0, 12)}</p>
                    {file.binary ? <p>Binary content is not displayed.</p> : <pre className="file-content">{file.content ?? ""}</pre>}
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
