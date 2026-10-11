import { Link } from "react-router-dom";
import type { Repository } from "../types/repository";
import type { RepositoryMetrics } from "../types/git";
import "./RepositoryHeader.scss";

type Props = {
  repository: Repository | null;
  branches: string[];
  branch: string;
  switchingBranch: boolean;
  onBranchChange: (branch: string) => void;
  metrics: RepositoryMetrics | null;
  syncing: boolean;
  onSync: () => void;
  onExploreMetrics: () => void;
  metricsOpen: boolean;
};

export default function RepositoryHeader({ repository, branches, branch, switchingBranch, onBranchChange, metrics, syncing, onSync, onExploreMetrics, metricsOpen }: Props) {
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
              <div className="branch-control">
                <label htmlFor="repository-branch" className="branch-control-label">Branch</label>
                <div className="branch-select-wrap">
                  <svg className="branch-select-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><circle cx="6" cy="3" r="2"/><path d="M6 5v12"/><circle cx="6" cy="19" r="2"/><circle cx="18" cy="6" r="2"/><path d="M18 8a6 6 0 0 1-6 6H6"/></svg>
                  <select 
                  style={{width: "auto"}}
                  id="repository-branch" aria-label="Choose branch" value={branch} disabled={switchingBranch || branches.length === 0} onChange={(event) => onBranchChange(event.target.value)}>
                    {branches.map((name) => <option key={name} value={name}>{name}</option>)}
                  </select>
                  <svg className="branch-select-chevron" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m6 9 6 6 6-6"/></svg>
                </div>
              </div>
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
