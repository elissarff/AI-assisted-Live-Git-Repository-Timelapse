import "./RepositoryPage.scss";
import { memo, useCallback, useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { getRepository, syncRepository } from "../api/repositories";
import { getCommit, getCommits, getFile, getTree, getMetrics } from "../api/commits";
import RepositoryTree from "../components/RepositoryTree";
import FileDiff from "../components/FileDiff";
import TimeLine from "../components/TimeLine";
import CommitSummary from "../components/CommitSummary";
import RepositoryHeader from "../components/RepositoryHeader";
import type { Repository } from "../types/repository";
import type { Commit, CommitDetail, FileChange, FileContent, RepositoryTree as Tree, RepositoryTreeNode, RepositoryMetrics } from "../types/git";

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
  const [metrics, setMetrics] = useState<RepositoryMetrics | null>(null);
  const [metricsOpen, setMetricsOpen] = useState(false);

  const workspaceRef = useRef<HTMLElement>(null);
  const dragStart = useRef<{x:number;width:number;total:number}|null>(null);
  const [treeWidth,setTreeWidth] = useState(18);
  const [treeHidden,setTreeHidden] = useState(false);
  const frameCache = useRef(new Map<string,{detail:CommitDetail;tree:Tree}>());
  const fileCache = useRef(new Map<string,FileContent|null>());
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
    Promise.all([getRepository(repoKey), getCommits(repoKey), getMetrics(repoKey)])
      .then(([repo, history, repositoryMetrics]) => {
        setRepository(repo as Repository);
        setCommits(history);
        setIndex(history.length ? history.length - 1 : 0);
        setMetrics(repositoryMetrics);
      })
      .catch((err) => setError(err instanceof Error ? err.message : "Could not load repository."));
  }, [repoKey]);

  useEffect(() => {
    if (!repoKey || !selected) return;
    let cancelled = false;
    const key = `${repoKey}:${selected.sha}`;
    const cached = frameCache.current.get(key);
    if (cached) { setDetail(cached.detail); setTree(cached.tree); setLoadingFrame(false); return; }
    setLoadingFrame(true);
    Promise.all([getCommit(repoKey,selected.sha),getTree(repoKey,selected.sha)])
      .then(([nextDetail,nextTree]) => {
        frameCache.current.set(key,{detail:nextDetail,tree:nextTree});
        if (!cancelled) {setDetail(nextDetail);setTree(nextTree);}
      })
      .catch(err => {if (!cancelled) setError(err instanceof Error ? err.message : "Could not load commit.");})
      .finally(() => {if (!cancelled) setLoadingFrame(false);});
    return () => {cancelled=true;};
  },[repoKey,selected?.sha]);

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
  const openFile = useCallback((node: RepositoryTreeNode, explicitChange?: FileChange) => {
    if (node.type === "DIRECTORY") return;
    setSelectedPath(node.path);
    setSelectedChange(explicitChange ?? null);
  }, []);

  const backToTree = () => {
    setSelectedPath(null);
    setSelectedChange(null);
    setFile(null);
    setBeforeFile(null);
    setFileUnavailable(false);
  };

  useEffect(() => {
    if (!repoKey || !selected || !selectedPath || !detail || detail.sha !== selected.sha) return;

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
    setLoadingFile(true);

    const parentSha = detail.parentShas?.[0];
    const afterPath = change?.newPath ?? effectivePath;
    const beforePath = change?.oldPath ?? effectivePath;

    Promise.all([
      change?.changeType === "DELETE"
        ? Promise.resolve(null)
        : cachedFile(repoKey, selected.sha, afterPath).catch(() => null),
      !parentSha || change?.changeType === "ADD"
        ? Promise.resolve(null)
        : cachedFile(repoKey, parentSha, beforePath).catch(() => null),
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

  const cachedFile = (id:string,sha:string,path:string) => {
    const key=`${id}:${sha}:${path}`;
    if (fileCache.current.has(key)) return Promise.resolve(fileCache.current.get(key) ?? null);
    return getFile(id,sha,path).then(value=>{fileCache.current.set(key,value);return value;});
  };
  const startResize = (event:React.PointerEvent<HTMLDivElement>) => {
    const total=workspaceRef.current?.getBoundingClientRect().width;
    if (!total) return;
    dragStart.current={x:event.clientX,width:treeWidth,total};
    event.currentTarget.setPointerCapture(event.pointerId);
  };
  const moveResize = (event:React.PointerEvent<HTMLDivElement>) => {
    if (!dragStart.current) return;
    const next=dragStart.current.width+(event.clientX-dragStart.current.x)*100/dragStart.current.total;
    if (next<10) {setTreeHidden(true);return;}
    setTreeHidden(false);setTreeWidth(Math.max(18,Math.min(75,next)));
  };

  const sync = async () => {
    if (!repoKey) return;
    setSyncing(true);
    setPlaying(false);
    setError(null);
    try {
      await syncRepository(repoKey);
      frameCache.current.clear();fileCache.current.clear();
      const repo = await getRepository(repoKey);
      setRepository(repo as Repository);
      await loadHistory(repoKey, repo.headSha);
      setMetrics(await getMetrics(repoKey));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not synchronize repository.");
    } finally {
      setSyncing(false);
    }
  };

  return (
    <main className="page extraction-page">
      <section className="extraction-card">
        <StableHeader repository={repository} syncing={syncing} onSync={sync} />

        {error && <p className="error">{error}</p>}
        {!repository && !error && <p>Loading repository...</p>}

        {repository && (
          <>
            {metrics && (
              <section className="card metrics-panel">
                <div className="metrics-heading">
                  <div><h2>Repository metrics</h2><p className="muted">Git-derived evolution metrics only. no LLM analysis.</p></div>
                  <button type="button" className="secondary-button" onClick={() => setMetricsOpen(v => !v)}>{metricsOpen ? "Hide details" : "Explore metrics"}</button>
                </div>
                <div className="metric-cards">
                  <div><strong>{metrics.totalCommits.toLocaleString()}</strong><span>Commits</span></div>
                  <div><strong>{metrics.contributorCount.toLocaleString()}</strong><span>Contributors</span></div>
                  <div><strong>{metrics.repositoryAgeDays.toLocaleString()}d</strong><span>Repository age</span></div>
                  <div><strong>{metrics.codeChurn.toLocaleString()}</strong><span>Code churn</span></div>
                  <div><strong>+{metrics.totalAdditions.toLocaleString()}</strong><span>Lines added</span></div>
                  <div><strong>-{metrics.totalDeletions.toLocaleString()}</strong><span>Lines removed</span></div>
                </div>
                {metricsOpen && (
                  <div className="metrics-grid">
                    <div className="metric-table-wrap"><h3>Activity over time</h3><table className="metric-table"><thead><tr><th>Month</th><th>Commits</th><th>Churn</th></tr></thead><tbody>{metrics.activityOverTime.map(x => <tr key={x.period}><td>{x.period}</td><td>{x.commits}</td><td>+{x.additions} / -{x.deletions}</td></tr>)}</tbody></table></div>
                    <div className="metric-table-wrap"><h3>Contributor activity</h3><table className="metric-table"><thead><tr><th>Contributor</th><th>Commits</th><th>Churn</th></tr></thead><tbody>{metrics.contributors.slice(0,15).map(x => <tr key={x.name}><td>{x.name}</td><td>{x.commits}</td><td>{x.churn.toLocaleString()}</td></tr>)}</tbody></table></div>
                    <div className="metric-table-wrap"><h3>Frequently changed files / hotspots</h3><table className="metric-table"><thead><tr><th>File</th><th>Changes</th><th>Contributors</th><th>Churn</th></tr></thead><tbody>{metrics.files.slice(0,20).map(x => <tr key={x.path}><td><code>{x.path}</code></td><td>{x.changes}</td><td>{x.contributorCount}</td><td>{x.churn.toLocaleString()}</td></tr>)}</tbody></table></div>
                    <div className="metric-table-wrap"><h3>Frequently changing directories</h3><table className="metric-table"><thead><tr><th>Directory</th><th>Changes</th><th>Churn</th></tr></thead><tbody>{metrics.directories.slice(0,20).map(x => <tr key={x.path}><td><code>{x.path}</code></td><td>{x.changes}</td><td>{x.churn.toLocaleString()}</td></tr>)}</tbody></table></div>
                    <div className="metric-table-wrap metric-wide"><h3>Files changed together</h3><table className="metric-table"><thead><tr><th>File A</th><th>File B</th><th>Commits together</th></tr></thead><tbody>{metrics.filesChangedTogether.slice(0,20).map((x,i) => <tr key={`${x.firstPath}-${x.secondPath}-${i}`}><td><code>{x.firstPath}</code></td><td><code>{x.secondPath}</code></td><td>{x.commitsTogether}</td></tr>)}</tbody></table></div>
                  </div>
                )}
              </section>
            )}
            <section className="card timelapse-bottom" style={{padding: "0"}}>
              <TimeLine
                commits={commits}
                currentIndex={index}
                playing={playing}
                speed={speed}
                onIndexChange={setIndex}
                onPlayingChange={setPlaying}
                onSpeedChange={setSpeed}
              />

              <CommitSummary commit={selected} detail={detail?.sha === selected?.sha ? detail : null} />
            </section>

            <section ref={workspaceRef} className={`workspace-resizable ${!selectedPath ? "tree-only" : ""} ${treeHidden ? "tree-hidden" : ""}`} style={{"--tree-width":`${treeWidth}%`} as React.CSSProperties}>
              {treeHidden && <button type="button" className="restore-tree" onClick={()=>{setTreeHidden(false);setTreeWidth(34);}}>☰ Show repository tree</button>}
              <div className="workspace-columns">
              <div className="card workspace-tree">
                <div className="viewport-heading">
                  <div><h2>Repository tree</h2><p className="muted">Select a file to follow across commits.</p></div>
                  <div className="tree-heading-actions">{loadingFrame && <span className="muted">Updating…</span>}<button type="button" className="tree-collapse" onClick={()=>setTreeHidden(true)} title="Hide repository tree">«</button></div>
                </div>
                {tree ? (
                  <RepositoryTree node={tree.root} onFileClick={openFile} selectedPath={selectedPath ?? undefined} changes={detail?.sha === selected?.sha ? detail.files : []} />
                ) : <p className="muted">{loadingFrame ? "Loading commit…" : "No tree loaded."}</p>}
              </div>
              {selectedPath && !treeHidden && <div className="workspace-resize-handle" role="separator" aria-label="Resize repository tree" aria-orientation="vertical" tabIndex={0} onPointerDown={startResize} onPointerMove={moveResize} onPointerUp={()=>{dragStart.current=null;}} onLostPointerCapture={()=>{dragStart.current=null;}} onDoubleClick={()=>setTreeWidth(34)} onKeyDown={event=>{if(event.key==="ArrowLeft"||event.key==="ArrowRight"){event.preventDefault();setTreeWidth(w=>Math.max(18,Math.min(75,w+(event.key==="ArrowRight"?5:-5))));}}}><span /></div>}
              {selectedPath && <div className="card workspace-diff">
                <div className="viewport-heading">
                  <div><h2>{selectedPath}</h2><p className="muted">File viewer</p></div>
                  {selectedPath && <button type="button" className="secondary-button clear-file" onClick={backToTree}>Back to tree</button>}
                </div>
                {(loadingFrame || loadingFile || detail?.sha !== selected?.sha) ? <p className="muted">Loading this file at the selected commit…</p>
                : selectedChange ? (
                  <>
                    <p className="muted"><strong>{selectedChange.changeType}</strong> · +{selectedChange.additions} / -{selectedChange.deletions}
                    {selectedChange.changeType === "RENAME" && selectedChange.oldPath ? ` · from ${selectedChange.oldPath}` : ""}</p>
                    <FileDiff change={selectedChange} beforeContent={beforeFile?.content} afterContent={file?.content} />
                  </>
                ) : file ? (
                  <><p className="muted">Unchanged in this commit · {file.size} bytes · {file.commitSha.slice(0, 12)}</p>
                  <FileDiff content={file.content} binary={file.binary} /></>
                ) : fileUnavailable ? (
                  <div className="file-unavailable"><h3>File unavailable at this commit</h3><p className="muted">Playback will continue and the file will appear when it exists.</p></div>
                ) : <p className="muted">No file content at this commit.</p>}
              </div>}
              </div>
            </section>

            
          </>
        )}
      </section>
    </main>
  );
}

const StableHeader = memo(RepositoryHeader);
