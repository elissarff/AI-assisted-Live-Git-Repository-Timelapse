import { useEffect, useRef, useState } from "react";
import { requestAiCommitAnalysis, type AiCommitStatus } from "../../api/aiAnalysis";
import { ApiError } from "../../api/api";

export default function AiCommitExplanation({ repoKey, sha }: { repoKey: string; sha: string }) {
  const [state, setState] = useState<AiCommitStatus>({ status: "NOT_REQUESTED" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const requestVersion = useRef(0);
  useEffect(() => {
    ++requestVersion.current;
    setState({ status: "NOT_REQUESTED" });
    setBusy(false);
    setError("");
    return () => { ++requestVersion.current; };
  }, [repoKey, sha]);
  const analyze = async (retry: boolean) => {
    const version = ++requestVersion.current;
    setBusy(true); setError("");
    try {
      const result = await requestAiCommitAnalysis(repoKey, sha, retry);
      if (requestVersion.current === version) setState(result);
    }
    catch (err) {
      if (requestVersion.current !== version) return;
      const data = err instanceof ApiError ? err.data as { message?: string } | undefined : undefined;
      setError(data?.message ?? "Could not request analysis. Please try again.");
    }
    finally { if (requestVersion.current === version) setBusy(false); }
  };
  const a = state.analysis;
  return <section aria-label="AI commit explanation" style={{ padding: "1rem", border: "1px solid var(--border-color, #7775)", borderRadius: 10, marginTop: 12 }}>
    <h3 style={{ marginTop: 0 }}>AI commit explanation</h3>
    {a ? <>
      <p><strong>Summary:</strong> {a.summary}</p>
      <p><strong>Likely purpose:</strong> {a.purpose}</p>
      <p><strong>Significance:</strong> {a.significance}</p>
      <p><strong>Category:</strong> {a.category}</p>
      {a.affectedComponents?.length > 0 && <p><strong>Components:</strong> {a.affectedComponents.join(", ")}</p>}
      {a.tags?.length > 0 && <p><strong>Tags:</strong> {a.tags.join(" · ")}</p>}
      <small>AI interpretation; verify against the original Git changes.</small>
    </> : <p role="status">{busy ? "Analyzing commit…" : `Status: ${state.status.toLowerCase().replaceAll("_", " ")}`}</p>}
    {state.error && <p role="alert">{state.error}</p>}
    {error && <p role="alert">{error}</p>}
    {(state.status === "NOT_REQUESTED" || state.status === "FAILED") && <button disabled={busy} onClick={() => void analyze(state.status === "FAILED")}>{busy ? "Requesting…" : state.status === "FAILED" ? "Retry analysis" : "Analyze commit"}</button>}
  </section>;
}
