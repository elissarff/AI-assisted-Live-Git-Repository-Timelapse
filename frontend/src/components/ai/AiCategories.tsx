import { useEffect, useRef, useState } from "react";
import { categorize, getCategories, type Categories, type Classification } from "../../api/explicitAi";
import "./ExplicitAi.scss";
type Props = { repoKey: string; branch: string; onUpdate: (value: Record<string, Classification>) => void; category: string; tag: string; onCategory: (value: string) => void; onTag: (value: string) => void };
export default function AiCategories({ repoKey, branch, onUpdate, category, tag, onCategory, onTag }: Props) {
  const [state, setState] = useState<Categories>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const version = useRef(0);
  useEffect(() => {
    const current = ++version.current;
    setState(undefined); setError(""); setBusy(false); onUpdate({}); onCategory(""); onTag("");
    void getCategories(repoKey, branch).then(value => { if (version.current === current) { setState(value); onUpdate(value.commits); } })
      .catch(() => { if (version.current === current) setError("Couldn't load tags"); });
    return () => { ++version.current; };
  }, [repoKey, branch]);
  const run = async () => {
    const current = ++version.current;
    setBusy(true); setError("");
    try {
      while (version.current === current) {
        const next = await categorize(repoKey, branch);
        if (version.current !== current) break;
        if (next.status === "FAILED") throw new Error(next.error ?? "Categorization unavailable");
        setState(next); onUpdate(next.commits);
        if (next.classified >= next.total) break;
      }
    } catch (e) { if (version.current === current) setError(e instanceof Error ? e.message : "Try again"); }
    finally { if (version.current === current) setBusy(false); }
  };
  const values = Object.values(state?.commits ?? {});
  return <span className="ai-categories">
    <button type="button" className="ai-action" disabled={busy || (!!state && state.total === state.classified)} onClick={() => void run()} title="Classify missing commits in small batches">{busy ? `Tagging ${state?.classified ?? 0}/${state?.total ?? "…"}` : "✦ Categorize"}</button>
    {busy && <button type="button" className="ai-action" aria-label="Stop after current batch" onClick={() => { ++version.current; setBusy(false); }}>×</button>}
    {values.length > 0 && <><select aria-label="Filter by AI category" value={category} onChange={e => onCategory(e.target.value)}><option value="">All categories</option>{[...new Set(values.map(v => v.category))].sort().map(v => <option key={v}>{v}</option>)}</select>
    <select aria-label="Filter by AI tag" value={tag} onChange={e => onTag(e.target.value)}><option value="">All tags</option>{[...new Set(values.flatMap(v => v.tags))].sort().map(v => <option key={v}>{v}</option>)}</select></>}
    {error && <span className="ai-error" role="alert" title={error}>⚑ <button type="button" className="ai-action" aria-label="Retry categorization" onClick={() => void run()}>↻</button></span>}
  </span>;
}
