import { memo, useEffect, useMemo, useRef, useState } from "react";
import "./FileDiff.scss";
import type { DetectedRefactoring } from "../types/refactoring";
import type { DiffHunk, DiffLine, FileChange } from "../types/git";

type Props = {
  change?: FileChange | null;
  content?: string | null;
  beforeContent?: string | null;
  afterContent?: string | null;
  binary?: boolean;
  path?: string;
  refactorings?: DetectedRefactoring[];
  highlights?: Array<{startLine: number; endLine: number; side: "before" | "after"}>;
  focusLine?: number;
  focusSide?: "before" | "after";
};
type Mark = "add" | "delete" | "context";
type Row = { kind: Mark; text: string; oldNo?: number; newNo?: number };

function kindOf(value?: string): Mark {
  const v = (value ?? "").toUpperCase();
  if (v.includes("ADD") || v === "+") return "add";
  if (v.includes("DEL") || v.includes("REMOVE") || v === "-") return "delete";
  return "context";
}
function parseHunk(hunk: DiffHunk) {
  const raw = Array.isArray(hunk.lines) ? hunk.lines : typeof hunk.content === "string" ? hunk.content.split("\n") : [];
  let oldCursor = hunk.oldStart ?? 1;
  let newCursor = hunk.newStart ?? 1;
  return raw.map(item => {
    const line = typeof item === "string" ? null : item as DiffLine;
    const text = typeof item === "string" ? item : line?.content ?? line?.line ?? "";
    const kind = line ? kindOf(line.type) : text.startsWith("+") ? "add" : text.startsWith("-") ? "delete" : "context";
    const oldNo = line?.oldLineNumber ?? line?.oldLine ?? (kind !== "add" ? oldCursor : undefined);
    const newNo = line?.newLineNumber ?? line?.newLine ?? (kind !== "delete" ? newCursor : undefined);
    if (kind !== "add") oldCursor = (oldNo ?? oldCursor) + 1;
    if (kind !== "delete") newCursor = (newNo ?? newCursor) + 1;
    return { kind, text, oldNo, newNo };
  });
}
function splitLines(value?: string | null) {
  if (!value) return [];
  const result = value.replace(/\r\n/g, "\n").split("\n");
  if (result[result.length - 1] === "") result.pop();
  return result;
}
function evolution(change: FileChange, beforeContent?: string | null, afterContent?: string | null): Row[] {
  const before = splitLines(beforeContent);
  const after = splitLines(afterContent);
  if (change.changeType === "DELETE") return before.map((text, i) => ({ kind: "delete", text, oldNo: i + 1 }));
  const added = new Set<number>();
  const deleted = new Map<number, Row[]>();
  for (const hunk of change.hunks ?? []) {
    const parsed = parseHunk(hunk);
    for (let i = 0; i < parsed.length; i++) {
      const line = parsed[i];
      if (line.kind === "add" && line.newNo) added.add(line.newNo);
      if (line.kind === "delete" && line.oldNo) {
        let position = hunk.newStart ?? 1;
        for (const preceding of parsed.slice(0, i)) if (preceding.kind !== "delete") position = (preceding.newNo ?? position) + 1;
        deleted.set(position, [...(deleted.get(position) ?? []), { kind: "delete", text: before[line.oldNo - 1] ?? "", oldNo: line.oldNo }]);
      }
    }
  }
  const rows: Row[] = [];
  for (let i = 1; i <= after.length; i++) {
    rows.push(...(deleted.get(i) ?? []));
    rows.push({ kind: added.has(i) ? "add" : "context", text: after[i - 1], newNo: i });
  }
  rows.push(...(deleted.get(after.length + 1) ?? []));
  return rows.length ? rows : before.map((text, i) => ({ kind: "context", text, oldNo: i + 1 }));
}

function FileDiff({ change, content, beforeContent, afterContent, binary, path, highlights = [], refactorings = [], focusLine, focusSide = "after" }: Props) {
  const [mode, setMode] = useState<"code" | "diff">("diff");
  const [expandedRefactoring, setExpandedRefactoring] = useState<number | null>(null);
  const [copied, setCopied] = useState(false);
  const isBinary = binary ?? change?.binary ?? false;
  const raw = content ?? afterContent ?? (change?.changeType === "DELETE" ? beforeContent : afterContent) ?? "";
  const canDiff = Boolean(change && (change.hunks?.length || change.changeType === "DELETE"));
  const rows = useMemo(() => {
    if (mode === "diff" && canDiff && change) return evolution(change, beforeContent, afterContent ?? content);
    return splitLines(raw).map((text, i): Row => ({ kind: "context", text, newNo: i + 1 }));
  }, [mode, canDiff, change, beforeContent, afterContent, content, raw]);
  const scrollRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (focusLine == null) return;
    scrollRef.current?.querySelector(`[data-source-line="${focusSide}-${focusLine}"]`)?.scrollIntoView({ behavior: "smooth", block: "center" });
  }, [focusLine, focusSide, rows]);
  const rawLines = splitLines(raw);
  const loc = rawLines.filter(line => line.trim()).length;
  const size = new TextEncoder().encode(raw).length;
  const displayPath = path ?? change?.path ?? "File content";
  const fileName = displayPath.split("/").pop() ?? displayPath;
  const extension = fileName.includes(".") ? fileName.split(".").pop()?.toUpperCase() : "FILE";
  const download = () => {
    const url = URL.createObjectURL(new Blob([raw], { type: "text/plain;charset=utf-8" }));
    const link = document.createElement("a"); link.href = url; link.download = fileName; link.click();
    URL.revokeObjectURL(url);
  };
  const copy = async () => {
    try { await navigator.clipboard.writeText(raw); setCopied(true); }
    catch { setCopied(false); }
  };
  return <div className="code-viewer">
    <div className="code-viewer-toolbar">
      <div className="code-viewer-info">
        <span className="code-viewer-file-icon" aria-hidden="true">▤</span>
        <span className="code-viewer-filename" title={displayPath}>{fileName}</span>
        <span className="code-viewer-language">{extension}</span>
        <span className="code-viewer-meta">{rawLines.length} lines · {loc} loc · {(size / 1024).toFixed(1)} KB</span>
      </div>
      <div className="code-viewer-actions">
        <div className="code-viewer-tabs" role="group" aria-label="File viewing mode">
          <button type="button" className={mode === "code" ? "active" : ""} aria-pressed={mode === "code"} onClick={() => setMode("code")}>Code</button>
          <button type="button" className={mode === "diff" ? "active" : ""} aria-pressed={mode === "diff"} onClick={() => setMode("diff")} disabled={!canDiff}>Diff</button>
        </div>
        <button type="button" className="code-viewer-action" title="Copy file contents" onClick={copy}>{copied ? "Copied" : "Copy"}</button>
        <button type="button" className="code-viewer-action" title="Download file" onClick={download}>Download</button>
      </div>
    </div>
    {refactorings.length > 0 && <div className="refactoring-discovery" aria-label="Detected structural refactorings">
      <strong>◆ Structural changes in this file</strong>
      {refactorings.map(r => <button key={r.id} type="button" className={`refactoring-action ${expandedRefactoring === r.id ? "is-open" : ""}`} onClick={() => setExpandedRefactoring(expandedRefactoring === r.id ? null : r.id)} aria-expanded={expandedRefactoring === r.id}>
        <span className="refactoring-action-type">{r.type.replaceAll("_", " ")}</span><span>{expandedRefactoring === r.id ? "Hide details" : "View what changed"}</span><span aria-hidden="true">{expandedRefactoring === r.id ? "▴" : "▾"}</span>
      </button>)}
    </div>}
    {expandedRefactoring !== null && (() => { const r=refactorings.find(item=>item.id===expandedRefactoring); if(!r)return null; const before=r.before_locations.find(l=>l.filePath===change?.oldPath || l.filePath===displayPath) ?? r.before_locations[0]; const after=r.after_locations.find(l=>l.filePath===displayPath) ?? r.after_locations[0]; const excerpt=(source:string|undefined|null,start?:number,end?:number)=>splitLines(source).slice(Math.max(0,(start??1)-1),Math.max(start??1,end??start??1)).map((line,i)=>`${(start??1)+i}  ${line}`).join("\n"); const moved = r.type.includes("MOVE") || r.type.includes("RENAME"); return <div className="refactoring-inline-compare"><div className="refactoring-compare-heading"><strong>{r.type.replaceAll("_", " ")}</strong><button type="button" onClick={()=>setExpandedRefactoring(null)}>Close ×</button></div>{moved && <div className="refactoring-move-summary"><div><small>BEFORE</small><code>{before?.filePath ?? "Unknown location"}</code></div><span className="refactoring-move-arrow" aria-hidden="true">→</span><div><small>AFTER</small><code>{after?.filePath ?? "Unknown location"}</code></div></div>}<p className="refactoring-description">{r.description}</p><div className="refactoring-code-details"><div className="refactoring-code-title">Affected source lines ({before?.startLine ?? "?"}–{before?.endLine ?? "?"} → {after?.startLine ?? "?"}–{after?.endLine ?? "?"})</div><div className="refactoring-compare-columns"><div><strong>Before</strong><pre>{excerpt(beforeContent,before?.startLine,before?.endLine) || "Before source unavailable"}</pre></div><div><strong>After</strong><pre>{excerpt(afterContent ?? content,after?.startLine,after?.endLine) || "After source unavailable"}</pre></div></div></div></div>; })()}
    {mode === "diff" && canDiff && <div className="code-viewer-legend"><span><b className="legend-add">+</b> Added</span><span><b className="legend-delete">−</b> Removed</span></div>}
    {isBinary ? <div className="code-viewer-empty">Binary file preview is unavailable.</div> :
      <div ref={scrollRef} className="code-viewer-scroll" role="table" aria-label={mode === "diff" ? "File changes" : "File source code"}>
        {rows.length ? rows.map((row, i) => <div onClick={() => { const match=refactorings.find(r=>[...r.before_locations.map(l=>({...l,side:"before"})),...r.after_locations.map(l=>({...l,side:"after"}))].some(l=>{const no=l.side==="before"?row.oldNo:row.newNo;return no!=null && no>=(l.startLine??Infinity) && no<=(l.endLine??l.startLine??-1) && (l.filePath===displayPath || l.filePath===change?.oldPath);}));if(match)setExpandedRefactoring(expandedRefactoring===match.id?null:match.id);}} role="row" title={highlights.some(h => (h.side === "before" ? row.oldNo : row.newNo) != null && (h.side === "before" ? row.oldNo! : row.newNo!) >= h.startLine && (h.side === "before" ? row.oldNo! : row.newNo!) <= h.endLine) ? "Refactoring detected here — click to inspect" : undefined} className={`code-viewer-line code-viewer-${row.kind} ${highlights.some(h => (h.side === "before" ? row.oldNo : row.newNo) != null && (h.side === "before" ? row.oldNo! : row.newNo!) >= h.startLine && (h.side === "before" ? row.oldNo! : row.newNo!) <= h.endLine) ? "code-viewer-refactored" : ""}`} data-source-line={`${row.oldNo != null ? "before" : "after"}-${row.oldNo ?? row.newNo}`} key={i}>
          <span className="code-viewer-line-number">{row.oldNo ?? row.newNo ?? ""}</span>
          <span className="code-viewer-line-marker">{mode === "diff" ? row.kind === "add" ? "+" : row.kind === "delete" ? "−" : "" : ""}</span>
          <code>{row.text || " "}</code>
        </div>) : <div className="code-viewer-empty">This file is empty.</div>}
      </div>}
    <div className="code-viewer-footer"><span>UTF-8 · {extension}</span><span>{rawLines.length} lines</span></div>
  </div>;
}
export default memo(FileDiff);
