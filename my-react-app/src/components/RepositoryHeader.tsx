import { Link } from "react-router-dom";
import type { Repository } from "../types/repository";
import "./RepositoryHeader.scss";

type Props = { repository: Repository | null; syncing: boolean; onSync: () => void };
export default function RepositoryHeader({ repository, syncing, onSync }: Props) {
  return <header className="card repository-header">
    <Link className="repository-back" to="/repositories">← Back to repositories</Link>
    <div className="repository-header-row">
      <div className="repository-identity">
        <span className="repository-icon" aria-hidden="true">⑂</span>
        <div><h1>{repository?.name ?? "Repository Git Extraction"}</h1><p className="muted">Explore your repository's history, files and changes.</p></div>
      </div>
      {repository && <div className="repository-actions">
        <span className="branch-pill" title="Current branch"><span aria-hidden="true">⑂</span> {repository.branch || "unknown"}</span>
        <button type="button" className="sync-action" onClick={onSync} disabled={syncing}><span aria-hidden="true">↻</span> {syncing ? "Syncing…" : "Sync repository"}</button>
      </div>}
    </div>
    {repository && <div className="repository-head">HEAD <code title={repository.headSha}>{repository.headSha || "—"}</code></div>}
  </header>;
}
