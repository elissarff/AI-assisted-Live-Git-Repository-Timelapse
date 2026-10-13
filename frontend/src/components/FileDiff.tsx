import { memo, useMemo, useState } from "react";
import "./FileDiff.scss";
import type { DiffHunk, DiffLine, FileChange } from "../types/git";

type Props = {
  change?: FileChange | null;
  content?: string | null;
  beforeContent?: string | null;
  afterContent?: string | null;
  binary?: boolean;
  path?: string;
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
    return { kind, oldNo, newNo };
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

function FileDiff({ change, content, beforeContent, afterContent, binary, path }: Props) {
  const [mode, setMode] = useState<"code" | "diff">("diff");
  const [copied, setCopied] = useState(false);
  const isBinary = binary ?? change?.binary ?? false;
  const raw = content ?? afterContent ?? (change?.changeType === "DELETE" ? beforeContent : afterContent) ?? "";
  const canDiff = Boolean(change && (change.hunks?.length || change.changeType === "DELETE"));
  const rows = useMemo(() => {
    if (mode === "diff" && canDiff && change) return evolution(change, beforeContent, afterContent ?? content);
    return splitLines(raw).map((text, i): Row => ({ kind: "context", text, newNo: i + 1 }));
  }, [mode, canDiff, change, beforeContent, afterContent, content, raw]);
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
    {mode === "diff" && canDiff && <div className="code-viewer-legend"><span><b className="legend-add">+</b> Added</span><span><b className="legend-delete">−</b> Removed</span></div>}
    {isBinary ? <div className="code-viewer-empty">Binary file preview is unavailable.</div> :
      <div className="code-viewer-scroll" role="table" aria-label={mode === "diff" ? "File changes" : "File source code"}>
        {rows.length ? rows.map((row, i) => <div role="row" className={`code-viewer-line code-viewer-${row.kind}`} key={i}>
          <span className="code-viewer-line-number">{row.oldNo ?? row.newNo ?? ""}</span>
          <span className="code-viewer-line-marker">{mode === "diff" ? row.kind === "add" ? "+" : row.kind === "delete" ? "−" : "" : ""}</span>
          <code>{row.text || " "}</code>
        </div>) : <div className="code-viewer-empty">This file is empty.</div>}
      </div>}
    <div className="code-viewer-footer"><span>UTF-8 · {extension}</span><span>{rawLines.length} lines</span></div>
  </div>;
}
export default memo(FileDiff);
