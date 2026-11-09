import "./RepositoryPage.scss";
import { memo, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import { getRepository, syncRepository } from "../api/repositories";
import { getBranches, getCommit, getCommits, getFile, getTree, getMetricSummary, getMetric, type MetricName, type MetricSummary } from "../api/commits";
import RepositoryTree from "../components/RepositoryTree";
import FileDiff from "../components/FileDiff";
import TimeLine from "../components/TimeLine";
import TimelineFilters from "../components/TimelineFilters";
import CommitSummary from "../components/CommitSummary";
import RepositoryHeader from "../components/RepositoryHeader";
import AiSettingsPanel from "../components/ai/AiSettingsPanel";
import MetricsExplorer from "../components/MetricsExplorer";

import { getRefactoringJob, analyzeRefactoring, retryRefactoring, getRefactoringSettings, updateRefactoringSettings } from "../api/refactoring";
import type { RefactoringJob } from "../types/refactoring";
import type { Repository } from "../types/repository";
import type { Commit, CommitDetail, FileChange, FileContent, RepositoryTree as Tree, RepositoryTreeNode, RepositoryMetrics } from "../types/git";

const BASE_PLAY_INTERVAL_MS = 1200;

export default function RepositoryPage() {
  const { repoKey } = useParams();
  // One repository-persisted preference controls both detection and visualization.
  const [showRefactorings, setShowRefactorings] = useState(false);
  const [refactoringPreferenceLoading, setRefactoringPreferenceLoading] = useState(true);
  const [refactoringPreferenceSaving, setRefactoringPreferenceSaving] = useState(false);
  const [refactoringPreferenceError, setRefactoringPreferenceError] = useState<string | null>(null);
  useEffect(() => {
    let active = true;
    setShowRefactorings(false);
    setRefactoringPreferenceLoading(true);
    setRefactoringPreferenceError(null);
    if (!repoKey) { setRefactoringPreferenceLoading(false); return; }
    getRefactoringSettings(repoKey).then(settings => {
      if (active) setShowRefactorings(settings.enabled);
    }).catch(() => {
      if (active) setRefactoringPreferenceError("Could not load refactoring preference");
    }).finally(() => { if (active) setRefactoringPreferenceLoading(false); });
    return () => { active = false; };
  }, [repoKey]);
  const changeRefactoringMode = async (enabled: boolean) => {
    if (!repoKey || refactoringPreferenceSaving) return;
    setRefactoringPreferenceSaving(true);
    setRefactoringPreferenceError(null);
    try {
      const saved = await updateRefactoringSettings(repoKey, { enabled });
      setShowRefactorings(saved.enabled);
    } catch {
      setRefactoringPreferenceError("Could not save refactoring preference");
    } finally { setRefactoringPreferenceSaving(false); }
  };
  const [refactoringJob, setRefactoringJob] = useState<RefactoringJob | null>(null);
  const [refactoringLoading, setRefactoringLoading] = useState(false);
  const [refactoringError, setRefactoringError] = useState<string | null>(null);
  const [focus, setFocus] = useState<{sha:string;path:string;line:number;side:"before"|"after"}|null>(null);
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
  const [metrics, setMetrics] = useState<MetricSummary | null>(null);
  const [details, setDetails] = useState<Partial<RepositoryMetrics>>({});
  const [metricErrors, setMetricErrors] = useState<Partial<Record<MetricName, string>>>({});
  const requestVersion = useRef(0);
  const detailCache = useRef(new Map<string, Partial<RepositoryMetrics>>());
  const metricNames: MetricName[] = ["activityOverTime", "contributors", "files", "directories", "filesChangedTogether"];
  const loadSummary = (id: string, selectedBranch: string, version: number) => {
    getMetricSummary(id, selectedBranch || undefined).then(value => {
      if (requestVersion.current === version) setMetrics(value);
    }).catch(() => { if (requestVersion.current === version) setMetrics(null); });
  };
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

  // Cache completed/absent analyses so timeline playback never repeatedly queries the same SHA.
  const refactoringCache = useRef(new Map<string, RefactoringJob | null>());
  const refactoringRequest = useRef(0);
  const selectedRefactoringKey = repoKey && selected?.sha ? `${repoKey}:${selected.sha}` : null;

  useEffect(() => {
    const version = ++refactoringRequest.current;
    if (!showRefactorings || !selectedRefactoringKey || !repoKey || !selected?.sha) {
      setRefactoringJob(null);
      setRefactoringLoading(false);
      return;
    }
    if (refactoringCache.current.has(selectedRefactoringKey)) {
      setRefactoringJob(refactoringCache.current.get(selectedRefactoringKey) ?? null);
      setRefactoringError(null);
      setRefactoringLoading(false);
      return;
    }
    setRefactoringJob(null);
    setRefactoringError(null);
    setRefactoringLoading(true);
    void getRefactoringJob(repoKey, selected.sha).then(job => {
      if (version !== refactoringRequest.current) return;
      const result = job ?? null;
      refactoringCache.current.set(selectedRefactoringKey, result);
      setRefactoringJob(result);
    }).catch((err: unknown) => {
      if (version !== refactoringRequest.current) return;
      if (err instanceof Error && "status" in err && err.status === 404) {
        refactoringCache.current.set(selectedRefactoringKey, null);
      } else {
        setRefactoringError(err instanceof Error ? err.message : "Could not load refactorings");
      }
    }).finally(() => {
      if (version === refactoringRequest.current) setRefactoringLoading(false);
    });
    return () => { ++refactoringRequest.current; };
  }, [selectedRefactoringKey, showRefactorings]);

  // Poll only a real pending/running job, never an absent, completed or failed analysis.
  useEffect(() => {
    if (!showRefactorings || !repoKey || !selected?.sha || !selectedRefactoringKey ||
        refactoringJob?.commit_sha !== selected.sha ||
        (refactoringJob.status !== "PENDING" && refactoringJob.status !== "RUNNING")) return;
    let active = true;
    const timer = window.setTimeout(() => {
      void getRefactoringJob(repoKey, selected.sha).then(job => {
        if (!active) return;
        const result = job ?? null;
        refactoringCache.current.set(selectedRefactoringKey, result);
        setRefactoringJob(result);
      }).catch((err: unknown) => {
        if (active) setRefactoringError(err instanceof Error ? err.message : "Could not refresh analysis");
      });
    }, 5000);
    return () => { active = false; window.clearTimeout(timer); };
  }, [showRefactorings, repoKey, selected?.sha, selectedRefactoringKey, refactoringJob]);

  const runRefactoring = async (retry=false) => {
    if (!repoKey || !selected || !selectedRefactoringKey) return;
    const sha = selected.sha;
    const key = selectedRefactoringKey;
    const version = refactoringRequest.current;
    setRefactoringLoading(true); setRefactoringError(null);
    try {
      const job = await (retry ? retryRefactoring(repoKey, sha) : analyzeRefactoring(repoKey, sha));
      refactoringCache.current.set(key, job);
      if (version === refactoringRequest.current) setRefactoringJob(job);
    } catch (err) {
      if (version === refactoringRequest.current) setRefactoringError(err instanceof Error ? err.message : "Analysis request failed");
    } finally {
      if (version === refactoringRequest.current) setRefactoringLoading(false);
    }
  };
  const currentRefactorings = showRefactorings && refactoringJob?.commit_sha === selected?.sha && refactoringJob?.status === "COMPLETED" ? (refactoringJob.refactorings ?? []) : [];
  const refactoredPaths = [...new Set(currentRefactorings.flatMap(r=>[...r.before_locations,...r.after_locations].map(l=>l.filePath).filter((v):v is string=>Boolean(v))))];
  const refactoringTypesByPath = Object.fromEntries(refactoredPaths.map(path => [path, [...new Set(currentRefactorings.filter(r => [...r.before_locations, ...r.after_locations].some(l => l.filePath === path)).map(r => r.type))]]));
  const highlightsForFile = (path:string) => currentRefactorings.flatMap(r=>[...r.before_locations.map(l=>({...l,side:"before" as const})),...r.after_locations.map(l=>({...l,side:"after" as const}))]).filter(l=>l.filePath===path && l.startLine != null).map(l=>({startLine:l.startLine!,endLine:l.endLine ?? l.startLine!,side:l.side}));
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
        const version = ++requestVersion.current;
        setBranches(availableBranches);
        setBranch(initialBranch);
        setRepository(repo as Repository);
        setMetrics(null);
        setDetails({});
        loadSummary(repoKey, initialBranch, version);
        const history = await getCommits(repoKey, initialBranch || undefined);
        if (requestVersion.current !== version) return;
        setCommits(history);
        setIndex(history.length ? history.length - 1 : 0);
      })
      .catch((err) => setError(err instanceof Error ? err.message : "Could not load repository."));
  }, [repoKey]);

  useEffect(() => {
    if (!metricsOpen || !repoKey) return;
    const version = requestVersion.current;
    const cacheKey = `${repoKey}:${branch}`;
    const cached = detailCache.current.get(cacheKey) ?? {};
    setDetails(cached);
    setMetricErrors({});
    metricNames.forEach(name => {
      if (cached[name] !== undefined) return;
      getMetric(repoKey, name, branch || undefined).then(result => {
        if (requestVersion.current !== version) return;
        const next = { ...detailCache.current.get(cacheKey), [name]: result };
        detailCache.current.set(cacheKey, next);
        setDetails(previous => ({ ...previous, [name]: result }));
      }).catch(err => {
        if (requestVersion.current === version) setMetricErrors(previous => ({ ...previous, [name]: err instanceof Error ? err.message : "Could not load metric" }));
      });
    });
  }, [metricsOpen, repoKey, branch]);

  const changeBranch = async (nextBranch: string) => {
    if (!repoKey || nextBranch === branch) return;
    setSwitchingBranch(true);
    setPlaying(false);
    setError(null);
    try {
      const version = ++requestVersion.current;
      setBranch(nextBranch);
      setMetrics(null);
      setDetails({});
      setMetricErrors({});
      loadSummary(repoKey, nextBranch, version);
      const history = await getCommits(repoKey, nextBranch);
      if (requestVersion.current !== version) return;
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
      const version = ++requestVersion.current;
      setMetrics(null);
      setDetails({});
      detailCache.current.clear();
      loadSummary(repoKey, branch, version);
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
          aiControl={repoKey && repository ? <AiSettingsPanel repoKey={repoKey} /> : undefined}
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
                refactoringMarkers={showRefactorings ? new Set(visibleCommits.flatMap(c => { const j=refactoringCache.current.get(`${repoKey}:${c.sha}`); return j?.status === "COMPLETED" && j.refactorings?.length ? [c.sha] : []; })) : undefined}
                refactoringControl={<div className={`refactoring-compact ${showRefactorings ? "refactoring-mode-active" : ""}`}><label><input type="checkbox" checked={showRefactorings} disabled={refactoringPreferenceLoading || refactoringPreferenceSaving || !!refactoringPreferenceError} aria-label="Enable refactoring detection and visualization for this repository" onChange={e=>void changeRefactoringMode(e.target.checked)} /> <span>◆ Refactoring mode</span></label>{refactoringPreferenceLoading && <span>Loading preference…</span>}{refactoringPreferenceSaving && <span>Saving…</span>}{refactoringPreferenceError && <span role="alert" className="error">{refactoringPreferenceError} <button type="button" onClick={()=>{ if(repoKey) { setRefactoringPreferenceError(null); setRefactoringPreferenceLoading(true); void getRefactoringSettings(repoKey).then(v=>setShowRefactorings(v.enabled)).catch(()=>setRefactoringPreferenceError("Could not load refactoring preference")).finally(()=>setRefactoringPreferenceLoading(false)); } }}>Retry</button></span>}{showRefactorings && <><button type="button" onClick={()=>void runRefactoring()} disabled={refactoringLoading || refactoringJob?.status === "RUNNING" || refactoringJob?.status === "PENDING"}>Analyze commit</button>{refactoringLoading && <span>Loading…</span>}{refactoringError && <span role="alert" className="error" title={refactoringError}>Error</span>}{refactoringJob?.status === "FAILED" && <button type="button" onClick={()=>void runRefactoring(true)}>Retry</button>}{currentRefactorings.map(r=><button type="button" key={r.id} className="refactoring-bubble" title={r.description} onClick={()=>{const loc=r.after_locations[0] ?? r.before_locations[0];if(loc?.filePath){setSelectedPath(loc.filePath);setFocus({sha:selected!.sha,path:loc.filePath,line:loc.startLine ?? 1,side:"after"});}}}>{r.type.replaceAll("_"," ")}</button>)}</>}</div>}
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
                  <RepositoryTree node={tree.root} onFileClick={openFile} selectedPath={selectedPath ?? undefined} changes={detail?.sha === selected?.sha ? detail.files : []} refactoredPaths={refactoredPaths} refactoringTypes={refactoringTypesByPath} />
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
                        <FileDiff path={selectedPath ?? undefined} highlights={highlightsForFile(selectedPath)} refactorings={currentRefactorings.filter(r=>[...r.before_locations,...r.after_locations].some(l=>l.filePath===selectedPath))} focusLine={focus?.sha === displayedFileFrame.sha && focus.path === selectedPath ? focus.line : undefined} focusSide={focus?.side} change={displayedFileFrame.change} beforeContent={displayedFileFrame.before?.content} afterContent={displayedFileFrame.after?.content} />
                      </>
                    ) : displayedFileFrame.after ? (
                      <><p className="muted">Unchanged in this commit · {displayedFileFrame.after.size} bytes · {displayedFileFrame.after.commitSha.slice(0, 12)}</p>
                      <FileDiff path={selectedPath ?? undefined} highlights={highlightsForFile(selectedPath)} refactorings={currentRefactorings.filter(r=>[...r.before_locations,...r.after_locations].some(l=>l.filePath===selectedPath))} focusLine={focus?.sha === displayedFileFrame.sha && focus.path === selectedPath ? focus.line : undefined} focusSide={focus?.side} content={displayedFileFrame.after.content} binary={displayedFileFrame.after.binary} /></>
                    ) : displayedFileFrame.unavailable ? (
                      <div className="file-unavailable"><h3>File unavailable at this commit</h3><p className="muted">Playback will continue and the file will appear when it exists.</p></div>
                    ) : <p className="muted">No file content at this commit.</p>}
                  </div>
                ) : <p className="muted">{loadingFrame || loadingFile ? "Loading selected file…" : "Preparing file viewer…"}</p>}
              </div>}
              </div>
            </section>
            {metricsOpen && <div ref={metricsSectionRef} className="metrics-details-anchor"><MetricsExplorer metrics={metrics} details={details} errors={metricErrors} commits={commits} onClose={() => setMetricsOpen(false)} /></div>}
          </>
        )}
      </section>
    </main>
  );
}

const StableHeader = memo(RepositoryHeader);
