import { memo, useMemo, useState, useRef } from "react";
import { AgCharts } from "ag-charts-react";
import { AllCommunityModule, ModuleRegistry } from "ag-charts-community";
import type { Commit, RepositoryMetrics } from "../types/git";
import "./MetricsExplorer.scss";

ModuleRegistry.registerModules([AllCommunityModule]);
type Props = { metrics: RepositoryMetrics; onClose: () => void };
type Row = Record<string, string | number>;
type Column = { key: string; label: string };
const fmt = (n: number) => n.toLocaleString();

function RankedTable({ title, subtitle, rows, columns, initialSort, preview = 6, hotspot = false }: { title: string; subtitle: string; rows: Row[]; columns: Column[]; initialSort: string; preview?: number; hotspot?: boolean }) {
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState(initialSort);
  const [desc, setDesc] = useState(true);
  const [expanded, setExpanded] = useState(false);
  const filtered = useMemo(() => rows.filter(r => columns.some(c => String(r[c.key] ?? "").toLowerCase().includes(query.toLowerCase().trim()))).sort((a,b) => {
    const av = a[sort], bv = b[sort]; const result = typeof av === "number" && typeof bv === "number" ? av-bv : String(av).localeCompare(String(bv)); return desc ? -result : result;
  }), [rows, columns, query, sort, desc]);
  return <section className={`explorer-table-card explorer-data-section ${hotspot ? "explorer-hotspots" : ""}`}>
    <div className="explorer-table-head"><div><h3>{title}</h3><p className="muted">{subtitle}</p></div><span className="explorer-count">{fmt(rows.length)} records</span></div>
    {expanded && <input className="explorer-filter" aria-label={`Search ${title}`} placeholder="Search files or values…" value={query} onChange={e => setQuery(e.target.value)} />}
    <div className="explorer-table-scroll"><table><thead><tr>{columns.map(c => <th key={c.key}><button type="button" onClick={() => { if (sort === c.key) setDesc(x => !x); else { setSort(c.key); setDesc(true); } }}>{c.label} {sort === c.key ? (desc ? "↓" : "↑") : ""}</button></th>)}</tr></thead><tbody>{filtered.slice(0, expanded ? undefined : preview).map((r,i) => <tr key={i}>{columns.map(c => <td key={c.key} title={String(r[c.key] ?? "")}>{c.key === "path" ? <span className="explorer-path"><strong>{String(r[c.key] ?? "").split("/").pop()}</strong><small>{String(r[c.key] ?? "").includes("/") ? String(r[c.key]).slice(0, String(r[c.key]).lastIndexOf("/")) : "Repository root"}</small></span> : c.key === "hotspotScore" ? <span className="explorer-hotspot-score">{fmt(Number(r[c.key]))}</span> : typeof r[c.key] === "number" ? fmt(r[c.key] as number) : String(r[c.key] ?? "")}</td>)}</tr>)}</tbody></table>{filtered.length === 0 && <p className="muted">No matching records.</p>}</div>
    {rows.length > preview && <div className="explorer-table-footer"><span>{expanded ? filtered.length : Math.min(preview, filtered.length)} of {filtered.length} rows</span><button type="button" onClick={() => {setExpanded(x => !x);setQuery("");}}>{expanded ? "Show fewer" : "View all records"} {expanded ? "↑" : "→"}</button></div>}
  </section>;
}

function ActivityChart({ rows, commits }: { rows: RepositoryMetrics["activityOverTime"]; commits: Commit[] }) {
  const heatmapRef = useRef<HTMLDivElement>(null);
  const [hovered, setHovered] = useState<{ start: string; end: string; count: number; x: number; y: number } | null>(null);
  const daily = useMemo(() => {
    const counts = new Map<string, number>();
    for (const commit of commits) {
      const date = new Date(commit.timestamp);
      if (!Number.isFinite(date.getTime())) continue;
      const day = date.toISOString().slice(0, 10);
      counts.set(day, (counts.get(day) ?? 0) + 1);
    }
    if (!counts.size) return null;
    const dates = [...counts.keys()].sort();
    const start = new Date(`${dates[0]}T00:00:00Z`);
    const end = new Date(`${dates[dates.length - 1]}T00:00:00Z`);
    const span = Math.floor((end.getTime() - start.getTime()) / 86400000) + 1;
    // Use weekly cells for longer histories to keep the heatmap legible.
    const bucketDays = span > 120 ? 7 : 1;
    const buckets: { label: string; count: number; start: string; end: string }[] = [];
    for (let offset = 0; offset < span; offset += bucketDays) {
      const first = new Date(start.getTime() + offset * 86400000);
      const last = new Date(start.getTime() + Math.min(span - 1, offset + bucketDays - 1) * 86400000);
      let count = 0;
      for (let d = new Date(first); d <= last; d.setUTCDate(d.getUTCDate() + 1)) count += counts.get(d.toISOString().slice(0, 10)) ?? 0;
      buckets.push({ label: first.toISOString().slice(0, 10), start: first.toISOString().slice(0, 10), end: last.toISOString().slice(0, 10), count });
    }
    const values = [...counts.values()];
    return { buckets, bucketDays, total: values.reduce((a,b) => a+b, 0), activeDays: counts.size, busiest: Math.max(...values), average: values.reduce((a,b) => a+b, 0) / span };
  }, [commits]);
  const monthly = useMemo(() => rows.map(r => ({ period: r.period, commits: r.commits })), [rows]);
  const options = useMemo(() => ({ data: monthly, background: { fill: "transparent" }, series: [{ type: "bar" as const, xKey: "period", yKey: "commits", yName: "Commits", fill: "#4f7df3" }], legend: { enabled: false } }), [monthly]);
  return <section className="explorer-table-card explorer-activity">
    <div className="explorer-table-head"><div><h3>Commit activity</h3><p className="muted">When development happened</p></div>{daily && <div className="explorer-activity-total"><strong>{fmt(daily.total)}</strong><span>commits</span></div>}</div>
    {daily ? <>
      <div className="explorer-heatmap-wrap" ref={heatmapRef} onMouseLeave={() => setHovered(null)}>
        <div className="explorer-heatmap" role="group" aria-label={`${daily.total} commits across ${daily.activeDays} active days`} style={{ gridTemplateColumns: `repeat(${Math.min(daily.buckets.length, 16)}, minmax(0, 1fr))` }}>
          {daily.buckets.map(bucket => <div key={bucket.start} className="explorer-heat-cell" role="img" aria-label={`${bucket.start}${bucket.end !== bucket.start ? ` to ${bucket.end}` : ""}: ${bucket.count} commits`} onMouseEnter={event => {
            const parent = heatmapRef.current?.getBoundingClientRect();
            const rect = event.currentTarget.getBoundingClientRect();
            if (!parent) return;
            setHovered({ ...bucket, x: Math.max(115, Math.min(parent.width - 115, rect.left - parent.left + rect.width / 2)), y: rect.top - parent.top });
          }} onMouseLeave={() => setHovered(null)} style={{ backgroundColor: bucket.count === 0 ? "#edf2f7" : bucket.count === 1 ? "#bfdbfe" : bucket.count <= 3 ? "#7da8f8" : bucket.count <= 7 ? "#477bea" : "#1d4ed8" }} />)}
        </div>
        {hovered && <div className="explorer-heat-tooltip" role="tooltip" style={{ left: hovered.x, top: hovered.y }}>
          <strong>{new Date(`${hovered.start}T00:00:00Z`).toLocaleDateString(undefined, { month: "long", day: "numeric", year: "numeric", timeZone: "UTC" })}{hovered.end !== hovered.start ? ` – ${new Date(`${hovered.end}T00:00:00Z`).toLocaleDateString(undefined, { month: "short", day: "numeric", timeZone: "UTC" })}` : ""}</strong>
          <span><i aria-hidden="true" />{fmt(hovered.count)} {hovered.count === 1 ? "commit" : "commits"}</span>
        </div>}
      </div>
      <div className="explorer-heat-legend"><span>Less</span>{["#edf2f7", "#bfdbfe", "#7da8f8", "#477bea", "#1d4ed8"].map(color => <i key={color} style={{backgroundColor:color}}/>)}<span>More</span></div>
      <div className="explorer-activity-facts"><div><span>Active days</span><strong>{fmt(daily.activeDays)}</strong></div><div><span>Busiest day</span><strong>{fmt(daily.busiest)} commits</strong></div><div><span>Daily average</span><strong>{daily.average.toFixed(1)}</strong></div></div>
      <p className="explorer-chart-note">Each square represents {daily.bucketDays === 1 ? "a day" : "up to one week"}. Hover for dates and commit counts.</p>
    </> : monthly.length > 1 ? <div className="explorer-chart-area"><AgCharts options={options}/></div> : <p className="muted explorer-activity-fallback">{monthly.length === 1 ? `${fmt(monthly[0].commits)} commits in ${monthly[0].period}. Daily activity is unavailable.` : "No activity yet."}</p>}
  </section>;
}

function RankingChart({ title, subtitle, rows, categoryKey, metricKey, metricLabel }: { title: string; subtitle: string; rows: Row[]; categoryKey: string; metricKey: string; metricLabel: string }) {
  const isDirectory = categoryKey === "path";
  const root = isDirectory ? rows.find(r => String(r.path) === "(root)") : undefined;
  const data = useMemo(() => [...rows].filter(r => !isDirectory || String(r.path) !== "(root)").sort((a,b) => Number(b[metricKey])-Number(a[metricKey])).slice(0, 7).reverse().map(r => ({ label: String(r[categoryKey]), value: Number(r[metricKey]) })), [rows, categoryKey, metricKey, isDirectory]);
  const options = useMemo(() => ({ data, background: { fill: "transparent" }, series: [{ type: "bar" as const, direction: "horizontal" as const, xKey: "label", yKey: "value", yName: metricLabel, fill: isDirectory ? "#7c8db5" : "#4f7df3" }], legend: { enabled: false } }), [data, metricLabel, isDirectory]);
  return <section className="explorer-table-card"><div className="explorer-table-head"><div><h3>{title}</h3><p className="muted">{subtitle}</p></div><span className="explorer-count">Top {Math.min(7, data.length)}</span></div>{root && <div className="explorer-root-note"><span>Root-level changes</span><strong>{fmt(Number(root[metricKey]))}</strong><small>Shown separately to keep smaller directories readable</small></div>}{data.length ? <div className="explorer-chart-area"><AgCharts options={options}/></div> : <p className="muted explorer-empty">No data yet.</p>}</section>;
}

function MetricsExplorer({ metrics, commits, onClose }: Props & { commits: Commit[] }) {
  const summary = [{ label: "Commits", value: fmt(metrics.totalCommits) }, { label: "Contributors", value: fmt(metrics.contributorCount) }, { label: "Repository age", value: `${fmt(metrics.repositoryAgeDays)}d` }, { label: "Code churn", value: fmt(metrics.codeChurn) }, { label: "Lines added", value: `+${fmt(metrics.totalAdditions)}`, tone: "positive" }, { label: "Lines removed", value: `−${fmt(metrics.totalDeletions)}`, tone: "negative" }];
  return <section className="card metrics-explorer"><div className="explorer-heading"><div><span className="explorer-eyebrow">REPOSITORY INSIGHTS</span><h2>More details</h2><p className="muted">Activity, ownership, and code change patterns</p></div><button className="explorer-close" type="button" onClick={onClose}>Close details ×</button></div>
    <div className="explorer-summary" aria-label="Repository metrics summary">{summary.map(s => <div key={s.label} className={`explorer-stat ${s.tone ?? ""}`}><span>{s.label}</span><strong>{s.value}</strong></div>)}</div>
    <div className="explorer-dashboard-grid">
      <div className="explorer-dashboard-hotspots">
        <RankedTable title="File hotspots" subtitle="Priority files ranked by hotspot score" rows={metrics.files} columns={[{key:"path",label:"File"},{key:"hotspotScore",label:"Score"},{key:"changes",label:"Changes"},{key:"contributorCount",label:"People"},{key:"churn",label:"Churn"}]} initialSort="hotspotScore" preview={7} hotspot/>
      </div>
      <div className="explorer-dashboard-activity"><ActivityChart rows={metrics.activityOverTime} commits={commits}/></div>
      <div className="explorer-dashboard-contributors"><RankingChart title="Top contributors" subtitle="Commits by contributor" rows={metrics.contributors} categoryKey="name" metricKey="commits" metricLabel="Commits"/></div>
      <div className="explorer-dashboard-directories"><RankingChart title="Directory activity" subtitle="Most frequently changed directories" rows={metrics.directories} categoryKey="path" metricKey="changes" metricLabel="Changes"/></div>
    </div>
    <div className="explorer-section-heading"><div><h3>Change relationships</h3><p className="muted">Pairs of files that tend to change together.</p></div></div>
    <div className="explorer-data-stack"><RankedTable title="Files changed together" subtitle="Shared commits between pairs of files; full paths are available on hover." rows={metrics.filesChangedTogether.map(r => ({ pair: `${r.firstPath} ↔ ${r.secondPath}`, commitsTogether: r.commitsTogether }))} columns={[{key:"pair",label:"File pair"},{key:"commitsTogether",label:"Shared commits"}]} initialSort="commitsTogether"/></div>
  </section>;
}
export default memo(MetricsExplorer);
