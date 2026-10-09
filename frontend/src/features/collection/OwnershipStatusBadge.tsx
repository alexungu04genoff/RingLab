import "../../styles/ownership-status.css";

export function OwnershipStatusBadge({ notOwned }: { notOwned: boolean }) {
  if (!notOwned) return null;
  return <span className="ownership-status-badge">
    <svg role="img" aria-label="Warning" width="14" height="14" viewBox="0 0 24 24"
      fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 3 2 21h20L12 3Z" /><path d="M12 9v5m0 3v.01" />
    </svg>
    Not owned
  </span>;
}
