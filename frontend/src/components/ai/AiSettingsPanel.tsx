import { useEffect, useState } from "react";
import { getAiSettings, updateAiSettings } from "../../api/ai";
import "./AiSettingsToggle.scss";

type Props = { repoKey: string };

/** Phase 1: settings only. No LLM or RefactoringMiner invocation. */
export default function AiSettingsPanel({ repoKey }: Props) {
  const [ai, setAi] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState<boolean>(false);
  const [error, setError] = useState("");
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    let active = true;
    setAi(false); setLoading(true); setError("");
    getAiSettings(repoKey)
      .then(a => { if (active) setAi(a.autoAnalyze); })
      .catch(() => { if (active) setError("Could not load AI preference"); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [repoKey, retry]);

  async function toggle(next: boolean) {
    setSaving(true); setError("");
    try {
      const result = await updateAiSettings(repoKey, { autoAnalyze: next });
      setAi(result.autoAnalyze);
    } catch {
      setError("Could not save AI preference");
    } finally { setSaving(false); }
  }

  return <div className="repository-ai-control" aria-label="Repository analysis preferences">
    <label className="repository-ai-toggle" title="AI commit explanations will be implemented in a later phase">
      <span className="repository-ai-label">✦ AI <span className="repository-ai-state">{ai ? "On" : "Off"}</span></span>
      <input type="checkbox" role="switch" aria-label="Enable AI for this repository"
        checked={ai} disabled={loading || saving || !!error}
        onChange={event => void toggle(event.target.checked)} />
      <span className="repository-ai-track" aria-hidden="true" />
    </label>
    {error && <span className="repository-ai-error" role="alert">{error} <button type="button" onClick={() => setRetry(n => n + 1)}>Retry</button></span>}
  </div>;
}
