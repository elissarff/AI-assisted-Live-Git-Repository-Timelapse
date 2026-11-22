import "./ErrorFlag.scss";

type Props = { message: string; onRetry?: () => void; busy?: boolean };
export default function ErrorFlag({ message, onRetry, busy = false }: Props) {
  return <span className="error-flag" title={message}>
    <span role="img" aria-label={message}>⚑</span>
    {onRetry && <button type="button" disabled={busy} onClick={onRetry} aria-label="Retry refactoring analysis" title="Retry analysis with more time">↻</button>}
  </span>;
}
