import type { Commit, CommitDetail } from "../types/git";
import "./CommitSummary.scss";

type Props = { commit: Commit | null; detail: CommitDetail | null };
export default function CommitSummary({ commit, detail }: Props) {
  if (!commit) return null;
  return <div className="commit-bottom-details">
    <div className="commit-bottom-message"><span className="timelapse-kicker">COMMIT</span><strong>{commit.message}</strong></div>
    <code title={commit.sha}>{commit.sha.slice(0, 12)}</code>
    <span>{commit.author}</span><span>{new Date(commit.timestamp).toLocaleString()}</span>
    {detail && <span className="commit-stats">{detail.filesChanged} files · <b>+{detail.additions}</b> / <b>-{detail.deletions}</b></span>}
  </div>;
}
