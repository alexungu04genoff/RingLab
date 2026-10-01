export function SelectionLock({ label, locked, disabled, onClick }: {
  label: string; locked: boolean | "mixed"; disabled?: boolean; onClick: () => void;
}) {
  const hint = locked === true ? "This selection will not be replaced. Unlock it to allow changes."
    : "Keep this selection when recommending a build.";
  return <button type="button" className="selection-lock" aria-label={`${locked === true ? "Unlock" : "Lock"} ${label}`}
    aria-pressed={locked} disabled={disabled} title={hint} onClick={event => { event.preventDefault(); event.stopPropagation(); onClick(); }}>
    <svg viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true">
      <rect x="4" y="9" width="12" height="9" rx="2" />
      {locked === true ? <path d="M6 9V6a4 4 0 0 1 8 0v3" /> : <path d="M6 9V6a4 4 0 0 1 7.5-2" />}
      {locked === "mixed" ? <path d="M7 13.5h6" /> : <path d="M10 12v3" />}
    </svg>
    <span className="lock-focus-hint">{hint}</span>
  </button>;
}
