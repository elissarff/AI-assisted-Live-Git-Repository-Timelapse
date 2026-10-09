import { Link } from "react-router-dom";
import type { Repository } from "../types/repository";
import type { RepositoryMetrics } from "../types/git";
import "./RepositoryHeader.scss";

type Props = {
  repository: Repository | null;
  metrics: RepositoryMetrics | null;
  syncing: boolean;
  onSync: () => void;
  onExploreMetrics: () => void;
  metricsOpen: boolean;
};

export default function RepositoryHeader({ repository, metrics, syncing, onSync, onExploreMetrics, metricsOpen }: Props) {
  const format = (value: number) => value.toLocaleString();
  return <>
    <Link className="repository-back-link" to="/repositories"><span aria-hidden="true">←</span> <span>All repositories</span></Link>
    <header className="card repository-header">
      <div className="repository-overview">
        <div className="repository-profile">
          <div className="repository-identity">
            <span className="repository-icon" aria-hidden="true">⑂</span>
            <div><span className="repository-eyebrow">REPOSITORY OVERVIEW</span><h1 style={{fontWeight: "normal"}}>{repository?.name ?? "Repository Git Extraction"}</h1><p className="muted">Explore your repository's history, files and changes.</p></div>
          </div>
          {repository && <>
            <div className="repository-actions">
              <span className="branch-pill" title="Current branch"><span aria-hidden="true">⑂</span> {repository.branch || "unknown"}</span>
              <button type="button" className="sync-action" onClick={onSync} disabled={syncing}><span aria-hidden="true">↻</span> {syncing ? "Syncing…" : "Sync repository"}</button>
            </div>
            <div className="repository-head"><span>HEAD</span> <code title={repository.headSha}>{repository.headSha || "—"}</code></div>
          </>}
        </div>
        <div className="repository-metrics" aria-label="Repository metrics">
          <div className="repository-metrics-heading">
            <div><span className="repository-eyebrow">AT A GLANCE</span><h2>Repository metrics</h2><p>A quick look at your repository</p></div>
            {metrics && <button type="button" className="explore-metrics-button" onClick={onExploreMetrics}>{metricsOpen ? "View details ↓" : "Explore metrics ↗"}</button>}
          </div>
          {metrics ? <div className="repository-metric-grid">
            <div className="repository-metric"><strong>{format(metrics.totalCommits)}</strong><span>Commits</span></div>
            <div className="repository-metric"><strong>{format(metrics.contributorCount)}</strong><span>Contributors</span></div>
            <div className="repository-metric"><strong>{format(metrics.repositoryAgeDays)}d</strong><span>Repository age</span></div>
            <div className="repository-metric"><strong>{format(metrics.codeChurn)}</strong><span>Code churn</span></div>
            <div className="repository-metric metric-additions"><strong>+{format(metrics.totalAdditions)}</strong><span>Lines added</span></div>
            <div className="repository-metric metric-deletions"><strong>−{format(metrics.totalDeletions)}</strong><span>Lines removed</span></div>
          </div> : <p className="muted metrics-loading">Repository metrics will appear when available.</p>}
        </div>
      </div>
    </header>
  </>;
}
