import type { DiffHunk, DiffLine, FileChange } from "../types/git";

type Props = {
  /** Optional commit change. Omit it for a plain/unchanged file view. */
  change?: FileChange | null;
  /** Plain file content. Useful when there is no diff/evolution to build. */
  content?: string | null;
  /** Parent blob; only needed when rendering deletions/evolution. */
  beforeContent?: string | null;
  /** Current blob; only needed when rendering additions/evolution. */
  afterContent?: string | null;
  /** Binary can be supplied even when there is no FileChange. */
  binary?: boolean;
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
    for (const [lineIndex, line] of parsed.entries()) {
      if (line.kind === "add" && line.newNo) added.add(line.newNo);
      if (line.kind === "delete" && line.oldNo) {
        // Count current-side lines before this deletion using its stable index.
        let insertion = hunk.newStart ?? 1;
        for (const p of parsed.slice(0, lineIndex)) {
          if (p.kind !== "delete") insertion = (p.newNo ?? insertion) + 1;
        }
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

export default function FileDiff({ change, content, beforeContent, afterContent, binary }: Props) {
  const isBinary = binary ?? change?.binary ?? false;
  if (isBinary) return <p>Binary content is not displayed.</p>;

  // Evolution is an enhancement, not a requirement. If there is no change
  // metadata, this component is simply the canonical raw-file renderer.
  const hasEvolution = Boolean(change && (change.hunks?.length || change.changeType === "DELETE"));
  const rawContent = content ?? afterContent ?? beforeContent ?? "";
  const rows = hasEvolution && change
    ? buildEvolution(change, beforeContent, afterContent ?? content)
    : lines(rawContent).map((text, i) => ({ kind: "context" as const, text, newNo: i + 1 }));

  // split("") represents an empty file as one empty line. Keep that useful
  // visual row without pretending there was an addition/deletion.
  return (
    <div className="file-evolution" role="table" aria-label={hasEvolution ? "Full file with commit changes" : "File content"}>
      {hasEvolution && (
        <div className="file-evolution-legend">
          <span><b className="legend-add">+</b> added this commit</span>
          <span><b className="legend-delete">−</b> removed this commit</span>
        </div>
      )}
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
