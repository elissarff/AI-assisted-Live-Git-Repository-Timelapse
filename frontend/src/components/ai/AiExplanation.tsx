import { createPortal } from "react-dom";
import { useEffect, useRef, useState } from "react";
import { explain } from "../../api/explicitAi";
import type { AiCommitStatus } from "../../api/aiAnalysis";
import { ApiError } from "../../api/api";
import "./ExplicitAi.scss";
type Props = { repoKey: string; sha: string; type: "COMMIT" | "FILE_SNAPSHOT" | "REPOSITORY" | "PERIOD" | "FILE_CHANGE" | "FILE_EVOLUTION"; fromSha?: string; toSha?: string; from?: string; to?: string; path?: string; label?: string };
export default function AiExplanation({ repoKey, sha, type, path, from, to, fromSha, toSha, label = "Explain" }: Props) {
  const [state, setState] = useState<AiCommitStatus>();
  const [busy, setBusy] = useState(false);
  const [open, setOpen] = useState(false);
  const version = useRef(0);
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => { if (open && dialog.current && !dialog.current.open) dialog.current.showModal(); }, [open]);
  useEffect(() => {
    ++version.current; setState(undefined); setBusy(false); setOpen(false);
    return () => { ++version.current; };
  }, [repoKey, sha, type, path, from, to, fromSha, toSha]);
  const run = async () => {
    if (state?.analysis) { setOpen(v => !v); return; }
    const current = ++version.current;
    setBusy(true); setOpen(true);
    try {
      const response = await explain(repoKey, type, sha, path, from, to, fromSha, toSha);
      if (version.current === current) setState(response);
    } catch (error) {
      if (version.current === current) {
        const data = error instanceof ApiError ? error.data as { message?: string } : undefined;
        setState({ status: "FAILED", error: data?.message ?? "Unable to explain. Try again." });
      }
    } finally { if (version.current === current) setBusy(false); }
  };
  const a = state?.analysis;
  return <span className="ai-explanation">
    <button type="button" className="ai-action" disabled={busy} onClick={() => void run()} aria-expanded={open}>{busy ? "Explaining…" : `✦ ${label}`}</button>
    {open && createPortal(<dialog ref={dialog} className="ai-explanation-popover" aria-label={label} onCancel={() => setOpen(false)}>
      <strong>{label.replace("Explain ", "")}</strong>
      {type === "PERIOD" && <p className="ai-muted">{fromSha ? `${fromSha.slice(0, 8)} → ${toSha?.slice(0, 8)}` : `${from || "Start"} – ${to || "Latest"} · UTC`}</p>}
      <button type="button" className="ai-close" aria-label="Close explanation" onClick={() => setOpen(false)}>×</button>
      {busy && <p role="status">Explaining…</p>}
      {state?.error && <p role="alert">{state.error} <button className="ai-action" onClick={() => void run()} disabled={busy} aria-label="Retry explanation">↻</button></p>}
      {a && <><p>{a.summary}</p><p>{a.purpose}</p>{type !== "COMMIT" && a.affectedComponents.length > 0 && <p className="ai-muted">{a.affectedComponents.join(" · ")}</p>}<p className="ai-muted">{a.category}{a.tags.length > 0 ? ` · ${a.tags.join(" · ")}` : ""}</p><small>AI interpretation · Verify against code</small></>}
    </dialog>, document.body)}
  </span>;
}
