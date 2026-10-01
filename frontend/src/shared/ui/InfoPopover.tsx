import { useEffect, useId, useLayoutEffect, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";

export function InfoPopover({ label, hint, children }: { label: string; hint: string; children: ReactNode }) {
  const [open, setOpen] = useState(false);
  const [position, setPosition] = useState({ left: 12, top: 12 });
  const id = useId();
  const trigger = useRef<HTMLButtonElement>(null);
  const panel = useRef<HTMLDivElement>(null);
  function close() {
    setOpen(false);
    trigger.current?.focus();
  }
  useLayoutEffect(() => {
    if (!open) return;
    function place() {
      const anchor = trigger.current!.getBoundingClientRect();
      const bounds = panel.current!.getBoundingClientRect();
      const left = Math.max(12, Math.min(anchor.left, window.innerWidth - bounds.width - 12));
      const below = anchor.bottom + 8;
      const top = below + bounds.height <= window.innerHeight - 12 ? below
        : Math.max(12, Math.min(anchor.top - bounds.height - 8, window.innerHeight - bounds.height - 12));
      setPosition({ left, top });
    }
    place();
    panel.current?.focus();
    window.addEventListener("resize", place);
    window.addEventListener("scroll", place, true);
    return () => {
      window.removeEventListener("resize", place);
      window.removeEventListener("scroll", place, true);
    };
  }, [open]);
  useEffect(() => {
    if (!open) return;
    const outside = (event: MouseEvent) => {
      if (!panel.current?.contains(event.target as Node) && !trigger.current?.contains(event.target as Node)) close();
    };
    const escape = (event: KeyboardEvent) => {
      if (event.key === "Escape") { event.preventDefault(); event.stopPropagation(); close(); }
    };
    document.addEventListener("click", outside);
    document.addEventListener("keydown", escape);
    return () => {
      document.removeEventListener("click", outside);
      document.removeEventListener("keydown", escape);
    };
  }, [open]);
  return <>
    <button ref={trigger} type="button" className="info-popover-trigger" title={hint}
      aria-expanded={open} aria-haspopup="dialog" aria-controls={open ? id : undefined}
      onClick={() => open ? close() : setOpen(true)}>{label} <span aria-hidden="true">ⓘ</span></button>
    {open && createPortal(<div ref={panel} id={id} role="dialog" aria-label={label} tabIndex={-1}
      className="info-popover" style={position}>
      <strong>{label}</strong>{children}
      <button type="button" className="small" onClick={close}>Close {label.toLowerCase()}</button>
    </div>, document.body)}
  </>;
}
