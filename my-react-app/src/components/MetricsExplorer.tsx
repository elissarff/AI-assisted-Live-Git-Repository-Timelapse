import { memo, useMemo, useState } from "react";
import type { RepositoryMetrics } from "../types/git";
import "./MetricsExplorer.scss";

type Props = { metrics: RepositoryMetrics; onClose: () => void };
type Row = Record<string, string | number>;
type Column = { key: string; label: string };
function DataTable({ title, rows, columns }: { title: string; rows: Row[]; columns: Column[] }) {
  const [sort, setSort] = useState(columns[1]?.key ?? columns[0].key);
  const [descending, setDescending] = useState(true);
  const [query, setQuery] = useState("");
  const [limit, setLimit] = useState(15);
  const visible = useMemo(() => rows.filter(row => columns.some(col => String(row[col.key] ?? "").toLowerCase().includes(query.toLowerCase())))
    .sort((a,b) => { const av=a[sort],bv=b[sort]; const comparison=typeof av==="number"&&typeof bv==="number" ? av-bv : String(av).localeCompare(String(bv));return descending?-comparison:comparison; }), [rows,columns,query,sort,descending]);
  return <section className="explorer-table-card"><div className="explorer-table-head"><h3>{title}</h3><input aria-label={`Search ${title}`} placeholder="Filter rows…" value={query} onChange={e=>{setQuery(e.target.value);setLimit(15);}} /></div>
    <div className="explorer-table-scroll"><table><thead><tr>{columns.map(col=><th key={col.key}><button type="button" onClick={()=>{if(sort===col.key)setDescending(v=>!v);else {setSort(col.key);setDescending(true);}}}>{col.label} {sort===col.key?(descending?"↓":"↑"):""}</button></th>)}</tr></thead>
    <tbody>{visible.slice(0,limit).map((row,i)=><tr key={i}>{columns.map(col=><td key={col.key} title={String(row[col.key]??"")}>{typeof row[col.key]==="number" ? (row[col.key] as number).toLocaleString() : String(row[col.key]??"")}</td>)}</tr>)}</tbody></table>{visible.length===0&&<p className="muted">No matching rows.</p>}</div>
    <div className="explorer-table-footer"><span>{Math.min(limit,visible.length)} of {visible.length} rows</span>{limit<visible.length&&<button type="button" onClick={()=>setLimit(v=>v+25)}>Show more</button>}</div></section>;
}
function ActivityChart({ activity }: { activity: RepositoryMetrics["activityOverTime"] }) {
  const recent=activity.slice(-18);
  const max=Math.max(1,...recent.map(x=>x.commits));
  return <section className="explorer-chart"><div><h3>Commit activity</h3><p className="muted">Commits by period · most recent {recent.length} periods</p></div>
    {recent.length ? <div className="activity-bars" role="img" aria-label="Bar chart of commits by period">{recent.map(item=><div className="activity-column" key={item.period} title={`${item.period}: ${item.commits} commits`}><span className="activity-value">{item.commits}</span><div className="activity-bar" style={{height:`${Math.max(3,item.commits/max*100)}%`}}/><span className="activity-label">{item.period}</span></div>)}</div> : <p className="muted">No activity available.</p>}
  </section>;
}
function MetricsExplorer({metrics,onClose}:Props) {
  return <section className="card metrics-explorer"><div className="explorer-heading"><div><span className="explorer-eyebrow">REPOSITORY INSIGHTS</span><h2>Explore metrics</h2><p className="muted">Understand activity, contributors, hotspots and related files.</p></div><button className="explorer-close" type="button" onClick={onClose}>Close details ×</button></div>
    <ActivityChart activity={metrics.activityOverTime}/>
    <div className="explorer-tables">
      <DataTable title="Activity over time" rows={metrics.activityOverTime} columns={[{key:"period",label:"Period"},{key:"commits",label:"Commits"},{key:"additions",label:"Added"},{key:"deletions",label:"Removed"}]}/>
      <DataTable title="Contributors" rows={metrics.contributors} columns={[{key:"name",label:"Contributor"},{key:"commits",label:"Commits"},{key:"churn",label:"Churn"}]}/>
      <DataTable title="File hotspots" rows={metrics.files} columns={[{key:"path",label:"File"},{key:"changes",label:"Changes"},{key:"contributorCount",label:"Contributors"},{key:"churn",label:"Churn"}]}/>
      <DataTable title="Directories" rows={metrics.directories} columns={[{key:"path",label:"Directory"},{key:"changes",label:"Changes"},{key:"churn",label:"Churn"}]}/>
      <div className="explorer-wide"><DataTable title="Files changed together" rows={metrics.filesChangedTogether} columns={[{key:"firstPath",label:"First file"},{key:"secondPath",label:"Second file"},{key:"commitsTogether",label:"Shared commits"}]}/></div>
    </div>
  </section>;
}
export default memo(MetricsExplorer);
