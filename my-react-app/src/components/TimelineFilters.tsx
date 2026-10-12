import { useEffect, useLayoutEffect, useRef, useState } from "react";
import "./TimeLine.scss";
import { createPortal } from "react-dom";
import type { Commit } from "../types/git";

type Props = {
  allCommits: Commit[]; matchedCount: number;
  authors: string[]; onAuthorsChange: (authors: string[]) => void;
  fromDate: string; onFromDateChange: (date: string) => void;
  toDate: string; onToDateChange: (date: string) => void;
  messageQuery: string; onMessageQueryChange: (query: string) => void;
};

export default function TimelineFilters({ allCommits, matchedCount, authors, onAuthorsChange, fromDate, onFromDateChange, toDate, onToDateChange, messageQuery, onMessageQueryChange }: Props) {
  const [open, setOpen] = useState(false);
  const [authorSearch, setAuthorSearch] = useState("");
  const root = useRef<HTMLDivElement>(null);
  const panel = useRef<HTMLDivElement>(null);
  const [position, setPosition] = useState<React.CSSProperties>({ top: 0, left: 0, visibility: "hidden" });

  useLayoutEffect(() => {
    if (!open) return;
    const updatePosition = () => {
      const trigger = root.current?.getBoundingClientRect();
      if (!trigger) return;
      const margin = 12;
      const gap = 8;
      const width = Math.min(370, window.innerWidth - margin * 2);
      const availableBelow = window.innerHeight - trigger.bottom - gap - margin;
      const availableAbove = trigger.top - gap - margin;
      const height = Math.min(570, Math.max(180, window.innerHeight - margin * 2));
      const openAbove = availableBelow < Math.min(height, 340) && availableAbove > availableBelow;
      const maxHeight = Math.max(100, Math.min(height, openAbove ? availableAbove : availableBelow));
      const left = Math.max(margin, Math.min(trigger.right - width, window.innerWidth - width - margin));
      setPosition({ position: "fixed", top: openAbove ? Math.max(margin, trigger.top - gap - maxHeight) : trigger.bottom + gap, left, width, maxHeight, visibility: "visible" });
    };
    updatePosition();
    window.addEventListener("resize", updatePosition);
    window.addEventListener("scroll", updatePosition, true);
    return () => { window.removeEventListener("resize", updatePosition); window.removeEventListener("scroll", updatePosition, true); };
  }, [open]);

  const contributors = [...new Set(allCommits.map(c => c.author))].sort((a, b) => a.localeCompare(b));
  const activeCount = Number(authors.length > 0) + Number(!!fromDate || !!toDate) + Number(!!messageQuery.trim());
  const clearFilters = () => { onAuthorsChange([]); onFromDateChange(""); onToDateChange(""); onMessageQueryChange(""); };

  useEffect(() => {
    if (!open) return;
    const outside = (event: PointerEvent) => { if (!root.current?.contains(event.target as Node) && !panel.current?.contains(event.target as Node)) setOpen(false); };
    const escape = (event: KeyboardEvent) => { if (event.key === "Escape") setOpen(false); };
    document.addEventListener("pointerdown", outside);
    document.addEventListener("keydown", escape);
    return () => { document.removeEventListener("pointerdown", outside); document.removeEventListener("keydown", escape); };
  }, [open]);
  const toggleAuthor = (author: string) => onAuthorsChange(authors.includes(author) ? authors.filter(a => a !== author) : [...authors, author]);
  return <div className="history-filter-anchor" ref={root}>
    <button type="button" className={`history-filter-trigger ${activeCount ? "has-filters" : ""}`} aria-expanded={open} aria-haspopup="dialog" onClick={() => setOpen(v => !v)}>
      <span aria-hidden="true">☷</span> Filters {activeCount > 0 && <span className="history-filter-badge">{activeCount}</span>}
    </button>
    {open && createPortal(<div ref={panel} className="history-filter-popover" style={position} role="dialog" aria-label="Filter repository history">
      <div className="history-filter-popover-heading"><div><strong>Filter history</strong><small>{matchedCount} of {allCommits.length} commits</small></div><button type="button" className="history-filter-close" aria-label="Close filters" onClick={() => setOpen(false)}>×</button></div>
      <div className="history-filter-fields">
        <div className="history-filter-field"><label htmlFor="history-author-search">Contributors</label><input id="history-author-search" type="search" placeholder="Find contributors…" value={authorSearch} onChange={e => setAuthorSearch(e.target.value)} />
          <div className="history-filter-authors">{contributors.filter(a => a.toLowerCase().includes(authorSearch.toLowerCase())).map(author => <label key={author} className="history-filter-author"><input type="checkbox" checked={authors.includes(author)} onChange={() => toggleAuthor(author)} /><span>{author}</span></label>)}{contributors.length === 0 && <small>No contributors yet</small>}</div>
        </div>
        <div className="history-filter-dates"><div className="history-filter-field"><label htmlFor="history-from">From</label><input id="history-from" type="date" value={fromDate} max={toDate || undefined} onChange={e => onFromDateChange(e.target.value)} /></div><div className="history-filter-field"><label htmlFor="history-to">To</label><input id="history-to" type="date" value={toDate} min={fromDate || undefined} onChange={e => onToDateChange(e.target.value)} /></div></div>
        <div className="history-filter-field"><label htmlFor="history-message">Commit message</label><input id="history-message" type="search" placeholder="Search messages…" value={messageQuery} onChange={e => onMessageQueryChange(e.target.value)} /></div>
      </div>
      <div className="history-filter-footer"><button type="button" className="history-filter-clear" onClick={clearFilters} disabled={!activeCount}>Clear all</button><button type="button" className="history-filter-done" onClick={() => setOpen(false)}>Done</button></div>
    </div>, document.body)}
  </div>;
}
