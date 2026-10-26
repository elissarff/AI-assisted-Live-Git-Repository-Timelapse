import { useEffect, useState } from "react";
import { getAiSettings, updateAiSettings } from "../../api/ai";
import { getRefactoringSettings, updateRefactoringSettings } from "../../api/refactoring";
import "./AiSettingsToggle.scss";

type Props = { repoKey: string };
type Engine = "ai" | "refactoring";

/** Phase 1: settings only. No LLM or RefactoringMiner invocation. */
export default function AiSettingsPanel({ repoKey }: Props) {
  const [ai, setAi] = useState(false);
  const [refactoring, setRefactoring] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState<Engine | null>(null);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    let active = true;
    setAi(false); setRefactoring(false); setLoading(true); setError("");
    Promise.all([getAiSettings(repoKey), getRefactoringSettings(repoKey)])
      .then(([a, r]) => { if (active) { setAi(a.autoAnalyze); setRefactoring(r.enabled); } })
      .catch(() => { if (active) setError("Could not load analysis preferences"); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [repoKey, retry]);

  async function toggle(engine: Engine, next: boolean) {
    setSaving(engine); setError("");
    try {
      if (engine === "ai") {
        const result = await updateAiSettings(repoKey, { autoAnalyze: next });
        setAi(result.autoAnalyze);
      } else {
        const result = await updateRefactoringSettings(repoKey, { enabled: next });
        setRefactoring(result.enabled);
      }
    } catch {
      setError(`Could not save ${engine === "ai" ? "AI" : "refactoring"} preference`);
    } finally { setSaving(null); }
  }

  return <div className="repository-ai-control" aria-label="Repository analysis preferences">
    <label className="repository-ai-toggle" title="AI commit explanations will be implemented in a later phase">
      <span className="repository-ai-label">✦ AI <span className="repository-ai-state">{ai ? "On" : "Off"}</span></span>
      <input type="checkbox" role="switch" aria-label="Enable AI for this repository"
        checked={ai} disabled={loading || saving !== null || !!error}
        onChange={event => void toggle("ai", event.target.checked)} />
      <span className="repository-ai-track" aria-hidden="true" />
    </label>
    <label className="repository-ai-toggle" title="RefactoringMiner detection will be implemented in a later phase">
      <span className="repository-ai-label">⑂ Refactoring <span className="repository-ai-state">{refactoring ? "On" : "Off"}</span></span>
      <input type="checkbox" role="switch" aria-label="Enable RefactoringMiner for this repository"
        checked={refactoring} disabled={loading || saving !== null || !!error}
        onChange={event => void toggle("refactoring", event.target.checked)} />
      <span className="repository-ai-track" aria-hidden="true" />
    </label>
    {error && <span className="repository-ai-error" role="alert">{error} <button type="button" onClick={() => setRetry(n => n + 1)}>Retry</button></span>}
  </div>;
}
