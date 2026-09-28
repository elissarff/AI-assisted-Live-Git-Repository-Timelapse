import type { DiffHunk, DiffLine, FileChange } from "../types/git";

type Props = {
  change: FileChange;
  beforeContent?: string | null;
  afterContent?: string | null;
};

type Mark = "add" | "delete" | "context";
type Row = { kind: Mark; text: string; oldNo?: number; newNo?: number };

type Parsed = { kind: Mark; oldNo?: number; newNo?: number };

function kindOf(value?: string): Mark {
  const v = (value ?? "").toUpperCase();
  if (v.includes("ADD") || v === "+") return "add";
  if (v.includes("DEL") || v.includes("REMOVE") || v === "-") return "delete";
  return "context";
}

function parsedHunk(hunk: DiffHunk): Parsed[] {
  const raw = Array.isArray(hunk.lines)
    ? hunk.lines
    : typeof hunk.content === "string"
      ? hunk.content.split("\n")
      : [];
  let oldCursor = hunk.oldStart ?? 1;
  let newCursor = hunk.newStart ?? 1;

  return raw.map((item) => {
    const line = typeof item === "string" ? null : (item as DiffLine);
    const text = typeof item === "string" ? item : (line?.content ?? line?.line ?? "");
    const kind = line ? kindOf(line.type) : text.startsWith("+") ? "add" : text.startsWith("-") ? "delete" : "context";
    const oldNo = line?.oldLineNumber ?? line?.oldLine ?? (kind !== "add" ? oldCursor : undefined);
    const newNo = line?.newLineNumber ?? line?.newLine ?? (kind !== "delete" ? newCursor : undefined);
    if (kind !== "add") oldCursor = (oldNo ?? oldCursor) + 1;
    if (kind !== "delete") newCursor = (newNo ?? newCursor) + 1;
    return { kind, oldNo: oldNo ?? undefined, newNo: newNo ?? undefined };
  });
}

function lines(content?: string | null) {
  if (content == null) return [];
  return content.replace(/\r\n/g, "\n").split("\n");
}

/**
 * Build a full-file view of the selected commit. The current blob is the base,
 * then Git's hunk line numbers mark additions and insert deleted parent lines
 * back at the exact transition where they disappeared.
 */
function buildEvolution(change: FileChange, beforeContent?: string | null, afterContent?: string | null): Row[] {
  const before = lines(beforeContent);
  const after = lines(afterContent);
  const added = new Set<number>();
  const deletionsBefore = new Map<number, Row[]>();

  for (const hunk of change.hunks ?? []) {
    const parsed = parsedHunk(hunk);
    for (const line of parsed) {
      if (line.kind === "add" && line.newNo) added.add(line.newNo);
      if (line.kind === "delete" && line.oldNo) {
        // newNo is absent on a deletion. The number of current-side lines seen
        // before it determines the insertion point in the post-commit file.
        const insertion = (() => {
          let nextNew = hunk.newStart ?? 1;
          for (const p of parsed) {
            if (p === line) break;
            if (p.kind !== "delete") nextNew = (p.newNo ?? nextNew) + 1;
          }
          return nextNew;
        })();
        const bucket = deletionsBefore.get(insertion) ?? [];
        bucket.push({ kind: "delete", text: before[line.oldNo - 1] ?? "", oldNo: line.oldNo });
        deletionsBefore.set(insertion, bucket);
      }
    }
  }

  const rows: Row[] = [];
  // A fully deleted file has no after blob, so show every parent line as removed.
  if (change.changeType === "DELETE" && before.length) {
    return before.map((text, i) => ({ kind: "delete", text, oldNo: i + 1 }));
  }

  for (let newNo = 1; newNo <= after.length; newNo++) {
    rows.push(...(deletionsBefore.get(newNo) ?? []));
    rows.push({ kind: added.has(newNo) ? "add" : "context", text: after[newNo - 1], newNo });
  }
  rows.push(...(deletionsBefore.get(after.length + 1) ?? []));

  // Fallback for APIs that report counts but omit hunks: still show the raw file.
  if (!rows.length && before.length) return before.map((text, i) => ({ kind: "context", text, oldNo: i + 1 }));
  return rows;
}

export default function FileDiff({ change, beforeContent, afterContent }: Props) {
  if (change.binary) return <p>Binary content is not displayed.</p>;
  const rows = buildEvolution(change, beforeContent, afterContent);

  return (
    <div className="file-evolution" role="table" aria-label="Full file with commit changes">
      <div className="file-evolution-legend">
        <span><b className="legend-add">+</b> added this commit</span>
        <span><b className="legend-delete">−</b> removed this commit</span>
      </div>
      <div className="file-evolution-code">
        {rows.map((row, index) => (
          <div className={`evolution-line evolution-${row.kind}`} key={`${row.kind}:${row.oldNo ?? ""}:${row.newNo ?? ""}:${index}`} role="row">
            <span className="evolution-line-no">{row.oldNo ?? row.newNo ?? ""}</span>
            <span className="evolution-sign">{row.kind === "add" ? "+" : row.kind === "delete" ? "−" : " "}</span>
            <code>{row.text || " "}</code>
          </div>
        ))}
      </div>
    </div>
  );
}
