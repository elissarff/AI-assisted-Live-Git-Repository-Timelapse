import "./RepositoryPage.scss";
import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import { getRepository, syncRepository } from "../api/repositories";
import { getBranches, getCommit, getCommits, getFile, getTree, getMetrics } from "../api/commits";
import RepositoryTree from "../components/RepositoryTree";
import FileDiff from "../components/FileDiff";
import TimeLine from "../components/TimeLine";
import TimelineFilters from "../components/TimelineFilters";
import CommitSummary from "../components/CommitSummary";
import RepositoryHeader from "../components/RepositoryHeader";
import MetricsExplorer from "../components/MetricsExplorer";
import type { Repository } from "../types/repository";
import type { Commit, CommitDetail, FileChange, FileContent, RepositoryTree as Tree, RepositoryTreeNode, RepositoryMetrics } from "../types/git";

const BASE_PLAY_INTERVAL_MS = 1200;

export default function RepositoryPage() {
  const { repoKey } = useParams();
  const [repository, setRepository] = useState<Repository | null>(null);
  const [commits, setCommits] = useState<Commit[]>([]);
  const [branches, setBranches] = useState<string[]>([]);
  const [branch, setBranch] = useState("");
  const [switchingBranch, setSwitchingBranch] = useState(false);
  const [index, setIndex] = useState(0);
  const [authors, setAuthors] = useState<string[]>([]);
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [messageQuery, setMessageQuery] = useState("");
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
  // A complete, last-known-good file frame remains visible while the next one loads.
  const [displayedFileFrame, setDisplayedFileFrame] = useState<{
    path: string; sha: string; change: FileChange | null;
    after: FileContent | null; before: FileContent | null; unavailable: boolean;
  } | null>(null);
  const [syncing, setSyncing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [metrics, setMetrics] = useState<RepositoryMetrics | null>(null);
  const [metricsOpen, setMetricsOpen] = useState(false);
  const metricsSectionRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!metricsOpen) return;
    const frame = requestAnimationFrame(() => {
      metricsSectionRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
    });
    return () => cancelAnimationFrame(frame);
  }, [metricsOpen]);

  const workspaceRef = useRef<HTMLElement>(null);
  const dragStart = useRef<{x:number;width:number;total:number}|null>(null);
  const [treeWidth,setTreeWidth] = useState(25);
  const [treeHidden,setTreeHidden] = useState(false);
  const treeWidthRef = useRef(treeWidth);
  const frameCache = useRef(new Map<string,{detail:CommitDetail;tree:Tree}>());
  const fileCache = useRef(new Map<string,FileContent|null>());
  const filteredIndices = useMemo(() => commits.flatMap((commit, i) => {
    const date = commit.timestamp.slice(0, 10);
    return (authors.length === 0 || authors.includes(commit.author)) &&
      (!fromDate || date >= fromDate) && (!toDate || date <= toDate) &&
      commit.message.toLowerCase().includes(messageQuery.trim().toLowerCase()) ? [i] : [];
  }), [commits, authors, fromDate, toDate, messageQuery]);
  const visibleCommits = useMemo(() => filteredIndices.map(i => commits[i]), [commits, filteredIndices]);
  const visibleIndex = filteredIndices.indexOf(index);
  const selected = commits[index] ?? null;
  const setVisibleIndex = (next: number) => {
    const target = filteredIndices[next];
    if (target !== undefined) setIndex(target);
  };
  // fix: only filter changes should interrupt playback, not each advancing commit.
  useEffect(() => {
    setPlaying(false);
    setIndex(current => filteredIndices.length && !filteredIndices.includes(current)
      ? filteredIndices[filteredIndices.length - 1] : current);
  }, [filteredIndices]);

  const loadHistory = useCallback(async (repositoryId: string, preferSha?: string) => {
    const history = await getCommits(repositoryId, branch || undefined);
    setCommits(history);
    if (!history.length) {
      setIndex(0);
      return;
    }
    const preferred = preferSha ? history.findIndex((c) => c.sha === preferSha) : -1;
    setIndex(preferred >= 0 ? preferred : history.length - 1);
  }, [branch]);

  useEffect(() => {
    if (!repoKey) {
      setError("Missing repository key.");
      return;
    }
    setError(null);
    Promise.all([getRepository(repoKey), getBranches(repoKey)])
      .then(async ([repo, availableBranches]) => {
        const initialBranch = availableBranches.includes(repo.branch) ? repo.branch : availableBranches[0] || "";
        const [history, repositoryMetrics] = await Promise.all([
          getCommits(repoKey, initialBranch || undefined),
          getMetrics(repoKey, initialBranch || undefined)
        ]);
        setBranches(availableBranches);
        setBranch(initialBranch);
        setRepository(repo as Repository);
        setCommits(history);
        setIndex(history.length ? history.length - 1 : 0);
        setMetrics(repositoryMetrics);
      })
      .catch((err) => setError(err instanceof Error ? err.message : "Could not load repository."));
  }, [repoKey]);

  const changeBranch = async (nextBranch: string) => {
    if (!repoKey || nextBranch === branch) return;
    setSwitchingBranch(true);
    setPlaying(false);
    setError(null);
    try {
      const [history, branchMetrics] = await Promise.all([
        getCommits(repoKey, nextBranch), getMetrics(repoKey, nextBranch)
      ]);
      setBranch(nextBranch);
      setMetrics(branchMetrics);
      setCommits(history);
      setIndex(history.length ? history.length - 1 : 0);
      setDetail(null);
      setTree(null);
      setDisplayedFileFrame(null);
      setSelectedPath(null);
      setSelectedChange(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not switch branch.");
    } finally {
      setSwitchingBranch(false);
    }
  };

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
    if (!playing || filteredIndices.length < 2) return;
    const timer = window.setInterval(() => {
      setIndex(current => {
        const position = filteredIndices.indexOf(current);
        if (position < 0 || position >= filteredIndices.length - 1) {
          setPlaying(false);
          return current;
        }
        return filteredIndices[position + 1];
      });
    }, BASE_PLAY_INTERVAL_MS / speed);
    return () => window.clearInterval(timer);
  }, [playing, filteredIndices, speed]);

  // File selection is intentionally independent from the timeline. Advancing
  // commits changes the version being displayed, not which file the user chose.
  const openFile = useCallback((node: RepositoryTreeNode, explicitChange?: FileChange) => {
    if (node.type === "DIRECTORY") return;
    setSelectedPath(node.path);
    setDisplayedFileFrame(previous => previous?.path === node.path ? previous : null);
    setSelectedChange(explicitChange ?? null);
  }, []);

  const backToTree = () => {
    updateTreeWidth(treeWidthRef.current || 25);
    setSelectedPath(null);
    setSelectedChange(null);
    setFile(null);
    setBeforeFile(null);
    setFileUnavailable(false);
    setDisplayedFileFrame(null);
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
      const unavailable = !after && !before && change?.changeType !== "DELETE";
      setFileUnavailable(unavailable);
      setDisplayedFileFrame({path: effectivePath, sha: selected.sha, change, after, before, unavailable});
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
  const updateTreeWidth = (next: number) => {
    const width = Math.max(0, Math.min(75, next));
    treeWidthRef.current = width;
    setTreeWidth(width);
    setTreeHidden(width === 0);
  };
  const startResize = (event: React.PointerEvent<HTMLDivElement>) => {
    if (event.button !== 0) return;
    const total = workspaceRef.current?.getBoundingClientRect().width;
    if (!total) return;
    dragStart.current = { x: event.clientX, width: treeWidthRef.current, total };
    event.currentTarget.setPointerCapture(event.pointerId);
    event.preventDefault();
  };
  const moveResize = (event: React.PointerEvent<HTMLDivElement>) => {
    if (!dragStart.current) return;
    const { x, width, total } = dragStart.current;
    const next = width + (event.clientX - x) * 100 / total;
    updateTreeWidth(next < 2 ? 0 : next);
  };
  const stopResize = () => { dragStart.current = null; };

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
      setMetrics(await getMetrics(repoKey, branch || undefined));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not synchronize repository.");
    } finally {
      setSyncing(false);
    }
  };

  return (
    <main className="page extraction-page">
      <section className="extraction-card">
        <StableHeader
          repository={repository}
          branches={branches}
          branch={branch}
          switchingBranch={switchingBranch}
          onBranchChange={changeBranch}
          metrics={metrics}
          syncing={syncing}
          onSync={sync}
          metricsOpen={metricsOpen}
          onExploreMetrics={() => {
            if (!metricsOpen) setMetricsOpen(true);
            else metricsSectionRef.current?.scrollIntoView({ behavior: "smooth", block: "start" });
          }}
        />

        {error && <p className="error">{error}</p>}
        {!repository && !error && <p>Loading repository...</p>}

        {repository && (
          <>
            <section className="card timelapse-bottom" style={{padding: "0"}}>
              <TimeLine
                filters={<TimelineFilters
              allCommits={commits} matchedCount={visibleCommits.length}
              authors={authors} onAuthorsChange={setAuthors}
              fromDate={fromDate} onFromDateChange={setFromDate}
              toDate={toDate} onToDateChange={setToDate}
              messageQuery={messageQuery} onMessageQueryChange={setMessageQuery}
            />}
                commits={visibleCommits}
                currentIndex={Math.max(0, visibleIndex)}
                playing={playing}
                speed={speed}
                onIndexChange={setVisibleIndex}
                onPlayingChange={setPlaying}
                onSpeedChange={setSpeed}
              />

              <CommitSummary commit={selected} detail={detail?.sha === selected?.sha ? detail : null} />
            </section>

            <section ref={workspaceRef} className={`workspace-resizable ${!selectedPath ? "tree-only" : ""} ${treeHidden ? "tree-hidden" : ""}`} style={{"--tree-width":`${treeWidth}%`} as React.CSSProperties}>
              {treeHidden && selectedPath && <button type="button" className="restore-tree" onClick={()=>updateTreeWidth(25)}>☰ Show repository tree</button>}
              <div className="workspace-columns">
              <div className="card workspace-tree">
                <div className="viewport-heading">
                  <div><h2>Repository tree</h2><p className="muted">Select a file to follow across commits.</p></div>
                  <div className="tree-heading-actions">
                    {loadingFrame && <span className="muted">Updating…</span>}
                    {selectedPath && <button type="button" className="tree-collapse" onClick={()=>updateTreeWidth(0)} title="Hide repository tree">«</button>}

                    {selectedPath && <button type="button" className="tree-collapse" onClick={backToTree} title="Back to tree">»</button>}
                  </div>
                </div>
                {tree ? (
                  <RepositoryTree node={tree.root} onFileClick={openFile} selectedPath={selectedPath ?? undefined} changes={detail?.sha === selected?.sha ? detail.files : []} />
                ) : <p className="muted">{loadingFrame ? "Loading commit…" : "No tree loaded."}</p>}
              </div>
              {selectedPath && <div className="workspace-resize-handle" role="separator" aria-label="Resize repository tree" aria-orientation="vertical" tabIndex={0} onPointerDown={startResize} onPointerMove={moveResize} onPointerUp={stopResize} onLostPointerCapture={stopResize} onDoubleClick={()=>updateTreeWidth(18)} onKeyDown={event=>{if(event.key==="ArrowLeft"||event.key==="ArrowRight"){event.preventDefault();updateTreeWidth(treeWidthRef.current+(event.key==="ArrowRight"?5:-5));}}}><span /></div>}
              {selectedPath && <div className="card workspace-diff">
                <div className="viewport-heading">
                  <div><h2>{selectedPath}</h2><p className="muted">File viewer</p></div>
                  {selectedPath && <button type="button" className="secondary-button clear-file" onClick={backToTree}>Back to tree</button>}
                </div>
                {displayedFileFrame?.path === selectedPath ? (
                  <div className="file-frame-content" aria-busy={loadingFrame || loadingFile || displayedFileFrame.sha !== selected?.sha}>
                    {displayedFileFrame.change ? (
                      <>
                        <p className="muted"><strong>{displayedFileFrame.change.changeType}</strong> · +{displayedFileFrame.change.additions} / -{displayedFileFrame.change.deletions}
                        {displayedFileFrame.change.changeType === "RENAME" && displayedFileFrame.change.oldPath ? ` · from ${displayedFileFrame.change.oldPath}` : ""}</p>
                        <FileDiff path={selectedPath ?? undefined} change={displayedFileFrame.change} beforeContent={displayedFileFrame.before?.content} afterContent={displayedFileFrame.after?.content} />
                      </>
                    ) : displayedFileFrame.after ? (
                      <><p className="muted">Unchanged in this commit · {displayedFileFrame.after.size} bytes · {displayedFileFrame.after.commitSha.slice(0, 12)}</p>
                      <FileDiff path={selectedPath ?? undefined} content={displayedFileFrame.after.content} binary={displayedFileFrame.after.binary} /></>
                    ) : displayedFileFrame.unavailable ? (
                      <div className="file-unavailable"><h3>File unavailable at this commit</h3><p className="muted">Playback will continue and the file will appear when it exists.</p></div>
                    ) : <p className="muted">No file content at this commit.</p>}
                  </div>
                ) : <p className="muted">{loadingFrame || loadingFile ? "Loading selected file…" : "Preparing file viewer…"}</p>}
              </div>}
              </div>
            </section>
            {metrics && metricsOpen && <div ref={metricsSectionRef} className="metrics-details-anchor"><MetricsExplorer metrics={metrics} commits={commits} onClose={() => setMetricsOpen(false)} /></div>}
          </>
        )}
      </section>
    </main>
  );
}

const StableHeader = memo(RepositoryHeader);
