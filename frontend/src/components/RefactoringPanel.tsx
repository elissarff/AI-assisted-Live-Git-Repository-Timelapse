import { useMemo, useState } from "react";
import type { RefactoringJob, RefactoringLocation } from "../types/refactoring";
import "./RefactoringPanel.scss";

type Props = {
  job: RefactoringJob | null;
  loading: boolean;
  error: string | null;
  onAnalyze: () => void;
  onRetry: () => void;
  onNavigate: (sha: string, location: RefactoringLocation, side: "before" | "after") => void;
};

export default function RefactoringPanel({ job, loading, error, onAnalyze, onRetry, onNavigate }: Props) {

  const [type, setType] = useState("ALL");
  const types = useMemo(() => [...new Set((job?.refactorings ?? []).map(r => r.type))].sort(), [job]);
  const filtered = (job?.refactorings ?? []).filter(r => type === "ALL" || r.type === type);
  
  return <section className="refactoring-panel" aria-busy={loading}>
    <div className="refactoring-heading"><div><h3>Structural refactorings</h3><p>RefactoringMiner evidence for the selected commit</p></div><button type="button" onClick={onAnalyze} disabled={loading || job?.status === "RUNNING" || job?.status === "PENDING"}>Analyze commit</button></div>
    {loading && <p role="status">Loading refactorings…</p>}
    {error && <p role="alert" className="error">{error}</p>}
    {!loading && !job && !error && <p>No analysis yet. Run analysis to detect structural changes.</p>}
    {job && <><p role="status">Status: <strong>{job.status}</strong>{job.status === "RUNNING" || job.status === "PENDING" ? " · Detection in progress…" : ""}</p>
      {job.status === "FAILED" && <div role="alert"><p>{job.last_error || "Analysis failed."}</p><button type="button" onClick={onRetry}>Retry analysis</button></div>}
      {job.status === "COMPLETED" && <><label>Type <select value={types.includes(type) ? type : "ALL"} onChange={e => setType(e.target.value)}><option value="ALL">All types</option>{types.map(t => <option key={t} value={t}>{t}</option>)}</select></label>
        {!filtered.length ? <p>{type === "ALL" ? "No structural refactorings detected in this commit." : "No refactorings match this filter."}</p> : <ul>{filtered.map(r => <li key={r.id}><strong>{r.type}</strong><p>{r.description}</p><div className="refactoring-locations">{(["before", "after"] as const).map(side => (side === "before" ? r.before_locations : r.after_locations).map((loc, i) => <button type="button" key={`${side}-${i}`} onClick={() => onNavigate(side === "before" ? r.parent_sha : job.commit_sha, loc, side)}>{side === "before" ? "Before" : "After"}: {loc.filePath || "Unknown file"} {loc.startLine ? `:${loc.startLine}–${loc.endLine ?? loc.startLine}` : ""}</button>))}</div></li>)}</ul>}</>}
    </>}
  </section>;
}
