import "./TimeLine.scss";
import type { Commit } from "../types/git";

type Props = {
  commits: Commit[]; currentIndex: number; playing: boolean; speed: number;
  onIndexChange: (index: number) => void;
  onPlayingChange: (playing: boolean) => void;
  onSpeedChange: (speed: number) => void;
};
const SPEEDS = [0.5, 1, 2, 4];
export default function TimeLine({ commits, currentIndex, playing, speed, onIndexChange, onPlayingChange, onSpeedChange }: Props) {
  const lastIndex = Math.max(0, commits.length - 1);
  if (!commits.length) return <p className="muted">No commits extracted.</p>;
  const seek = (next: number) => { onPlayingChange(false); onIndexChange(Math.max(0, Math.min(lastIndex, next))); };
  const togglePlayback = () => {
    if (!playing && currentIndex >= lastIndex && commits.length > 1) { onIndexChange(0); onPlayingChange(true); }
    else onPlayingChange(!playing);
  };
  return <section className="timelapse-player" aria-label="Repository history timelapse">
    <div className="timelapse-now"><div><span className="timelapse-kicker">TIMELAPSE</span><strong>Repository history</strong></div><span className="timelapse-count">{currentIndex + 1} / {commits.length}</span></div>
    <input className="timeline-range" aria-label="Commit timeline" type="range" min={0} max={lastIndex} value={currentIndex}
      style={{"--progress": `${lastIndex ? (currentIndex / lastIndex) * 100 : 0}%`} as React.CSSProperties}
      onChange={e => seek(Number(e.target.value))}/>
    <div className="timelapse-controls">
      <div className="player-transport">
        <button type="button" className="player-button secondary" onClick={() => seek(0)} disabled={currentIndex === 0} aria-label="First commit">|‹</button>
        <button type="button" className="player-button secondary" onClick={() => seek(currentIndex - 1)} disabled={currentIndex === 0} aria-label="Previous commit">‹</button>
        <button type="button" className="player-button play" onClick={togglePlayback} disabled={commits.length < 2} aria-label={playing ? "Pause" : "Play"}>{playing ? "Ⅱ" : "▶"}</button>
        <button type="button" className="player-button secondary" onClick={() => seek(currentIndex + 1)} disabled={currentIndex >= lastIndex} aria-label="Next commit">›</button>
        <button type="button" className="player-button secondary" onClick={() => seek(lastIndex)} disabled={currentIndex >= lastIndex} aria-label="Latest commit">›|</button>
      </div>
      <div className="speed-controls" aria-label="Playback speed">{SPEEDS.map(value => <button type="button" key={value} aria-pressed={speed === value} className={`speed-button ${speed === value ? "active" : ""}`} onClick={() => onSpeedChange(value)}>{value}×</button>)}</div>
    </div>
  </section>;
}
