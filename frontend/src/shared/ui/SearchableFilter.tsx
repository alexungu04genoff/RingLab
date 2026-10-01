import { useEffect, useId, useRef, useState, type ReactNode } from "react";

interface SearchableOption { value: string; label: string }

export function filterSearchableOptions(options: SearchableOption[], query: string) {
  const normalized = query.trim().toLocaleLowerCase();
  return normalized
    ? options.filter(({ label }) => label.toLocaleLowerCase().includes(normalized))
    : options;
}

export function SearchableFilter({ label, icon, options, value, allLabel, onChange }: {
  label: string;
  icon: ReactNode;
  options: SearchableOption[];
  value: string;
  allLabel: string;
  onChange: (value: string) => void;
}) {
  const listboxId = useId();
  const root = useRef<HTMLDivElement>(null);
  const selectedLabel = options.find((option) => option.value === value)?.label ?? allLabel;
  const [text, setText] = useState(selectedLabel);
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);
  const query = text === selectedLabel ? "" : text;
  const matches = filterSearchableOptions(options, query);
  const choices = query ? matches : [{ value: "", label: allLabel }, ...matches];

  useEffect(() => setText(selectedLabel), [selectedLabel]);

  function choose(option: SearchableOption) {
    onChange(option.value);
    setText(option.label);
    setOpen(false);
    setActiveIndex(0);
  }

  return (
    <label className="searchable-filter">
      <span className="field-label">{icon}{label}</span>
      <div
        className="searchable-filter-control"
        ref={root}
        onBlur={(event) => {
          if (!root.current?.contains(event.relatedTarget)) {
            setText(selectedLabel);
            setOpen(false);
          }
        }}
      >
        <input
          type="search"
          role="combobox"
          aria-label={`Search ${label.toLowerCase()}`}
          aria-autocomplete="list"
          aria-controls={listboxId}
          aria-expanded={open}
          aria-activedescendant={open && choices[activeIndex]
            ? `${listboxId}-option-${activeIndex}` : undefined}
          value={text}
          onFocus={(event) => {
            setOpen(true);
            setActiveIndex(0);
            event.currentTarget.select();
          }}
          onClick={() => {
            setOpen(true);
            setActiveIndex(0);
          }}
          onChange={(event) => {
            setText(event.target.value);
            setOpen(true);
            setActiveIndex(0);
          }}
          onKeyDown={(event) => {
            if (event.key === "Escape") {
              setText(selectedLabel);
              setOpen(false);
              return;
            }
            if (event.key === "ArrowDown" || event.key === "ArrowUp") {
              event.preventDefault();
              setOpen(true);
              const direction = event.key === "ArrowDown" ? 1 : -1;
              setActiveIndex((current) => choices.length
                ? (current + direction + choices.length) % choices.length : 0);
              return;
            }
            if (event.key === "Enter" && open && choices[activeIndex]) {
              event.preventDefault();
              choose(choices[activeIndex]);
            }
          }}
        />
        <span className="filter-chevron" aria-hidden="true">⌄</span>
        <div id={listboxId} role="listbox" hidden={!open}>
          {choices.map((option, index) => (
            <button
              id={`${listboxId}-option-${index}`}
              type="button"
              role="option"
              aria-selected={option.value === value}
              className={index === activeIndex ? "active" : undefined}
              key={option.value}
              onMouseDown={(event) => event.preventDefault()}
              onMouseEnter={() => setActiveIndex(index)}
              onClick={() => choose(option)}
            >
              {option.label}
            </button>
          ))}
          {choices.length === 0 && <span className="no-filter-results">No matches</span>}
        </div>
      </div>
    </label>
  );
}
