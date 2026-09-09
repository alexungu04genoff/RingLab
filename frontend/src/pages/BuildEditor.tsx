import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { api, json } from "../api";
import { useAuth } from "../auth";
import { ErrorNotice, ItemSelect } from "../components";
import { moveGadget, toggleGadget } from "../buildForm";
import { useLoad } from "../useLoad";
import type { Build, BuildDraft, GameItem } from "../types";
export function BuildEditor() {
  const { id } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [draft, setDraft] = useState<BuildDraft>({
    title: "",
    description: "",
    racerId: "",
    machineId: "",
    gadgetIds: [],
  });
  const [loading, setLoading] = useState(!!id);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [allowed, setAllowed] = useState(!id);
  const racers = useLoad<GameItem[]>("/racers");
  const machines = useLoad<GameItem[]>("/machines");
  const gadgets = useLoad<GameItem[]>("/gadgets");
  useEffect(() => {
    if (!id) return;
    const controller = new AbortController();
    api<Build>(`/builds/${id}`, { signal: controller.signal })
      .then((b) => {
        if (b.author.id !== user?.id) {
          setError("Only the author may edit this build.");
          return;
        }
        setAllowed(true);
        setDraft({
          title: b.title,
          description: b.description,
          racerId: b.racer.id,
          machineId: b.machine.id,
          gadgetIds: b.gadgets.map((g) => g.id),
        });
      })
      .catch((e) => {
        if (!controller.signal.aborted) setError(e.message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [id, user?.id]);
  function field<K extends keyof BuildDraft>(key: K, value: BuildDraft[K]) {
    setDraft((d) => ({ ...d, [key]: value }));
  }
  return (
    <>
      <Link className="back" to={id ? `/builds/${id}` : "/"}>
        ← Back
      </Link>
      <div className="page-heading">
        <div>
          <div className="eyebrow accent">THE GARAGE</div>
          <h1>{id ? "Fine-tune your build." : "Make it your own."}</h1>
          <p>One racer. One stock machine. Your gadget combination.</p>
        </div>
      </div>
      <ErrorNotice
        message={error || racers.error || machines.error || gadgets.error}
      />
      {loading ? (
        <p role="status">Loading your build…</p>
      ) : (
        allowed && (
          <form
            className="editor"
            onSubmit={async (e) => {
              e.preventDefault();
              setBusy(true);
              setError("");
              try {
                const b = await api<Build>(
                  id ? `/builds/${id}` : "/builds",
                  json(id ? "PUT" : "POST", draft),
                );
                navigate(`/builds/${b.id}`);
              } catch (e) {
                setError((e as Error).message);
              } finally {
                setBusy(false);
              }
            }}
          >
            <div className="editor-main">
              <section className="panel">
                <h2>
                  <span className="step">01</span> The essentials
                </h2>
                <label>
                  Build title
                  <input
                    required
                    maxLength={120}
                    value={draft.title}
                    onChange={(e) => field("title", e.target.value)}
                    placeholder="Give your setup a name"
                  />
                </label>
                <label>
                  Description
                  <textarea
                    maxLength={10000}
                    rows={6}
                    value={draft.description}
                    onChange={(e) => field("description", e.target.value)}
                    placeholder="What makes this combination work for you?"
                  />
                </label>
              </section>
              <section className="panel">
                <h2>
                  <span className="step">02</span> Racer & machine
                </h2>
                <div className="two-columns">
                  <ItemSelect
                    label="Racer"
                    items={racers.data || []}
                    value={draft.racerId}
                    onChange={(v) => field("racerId", v)}
                  />
                  <ItemSelect
                    label="Machine"
                    items={machines.data || []}
                    value={draft.machineId}
                    onChange={(v) => field("machineId", v)}
                  />
                </div>
                <p className="muted">
                  Choose any racer with any stock machine.
                </p>
              </section>
              <section className="panel">
                <h2>
                  <span className="step">03</span> Gadgets{" "}
                  <span className="muted">optional</span>
                </h2>
                <p>Select gadgets in the order you want them displayed.</p>
                <div className="gadget-options">
                  {gadgets.data?.map((g) => (
                    <label
                      key={g.id}
                      className={`gadget-option ${draft.gadgetIds.includes(g.id) ? "selected" : ""}`}
                    >
                      <input
                        type="checkbox"
                        checked={draft.gadgetIds.includes(g.id)}
                        onChange={() =>
                          field(
                            "gadgetIds",
                            toggleGadget(draft.gadgetIds, g.id),
                          )
                        }
                      />
                      <span>{g.name}</span>
                    </label>
                  ))}
                </div>
                <p className="muted">
                  Combinations are shared as selected. Gadget Plate capacity and
                  compatibility are not checked.
                </p>
              </section>
            </div>
            <aside className="panel selection">
              <div className="eyebrow accent">YOUR COMBINATION</div>
              <h2>{draft.title || "Untitled build"}</h2>
              <p>
                {racers.data?.find((r) => r.id === draft.racerId)?.name ||
                  "Choose a racer"}
              </p>
              <p>
                {machines.data?.find((m) => m.id === draft.machineId)?.name ||
                  "Choose a machine"}
              </p>
              <hr />
              <h3>Gadgets · {draft.gadgetIds.length}</h3>
              {draft.gadgetIds.length === 0 && (
                <p className="muted">No gadgets selected.</p>
              )}
              <ol className="selected-gadgets">
                {draft.gadgetIds.map((g, i) => (
                  <li key={`${g}-${i}`}>
                    <span>
                      {gadgets.data?.find((item) => item.id === g)?.name}
                    </span>
                    <div>
                      <button
                        type="button"
                        aria-label={`Move gadget ${i + 1} up`}
                        disabled={i === 0}
                        onClick={() =>
                          field("gadgetIds", moveGadget(draft.gadgetIds, i, -1))
                        }
                      >
                        ↑
                      </button>
                      <button
                        type="button"
                        aria-label={`Move gadget ${i + 1} down`}
                        disabled={i === draft.gadgetIds.length - 1}
                        onClick={() =>
                          field("gadgetIds", moveGadget(draft.gadgetIds, i, 1))
                        }
                      >
                        ↓
                      </button>
                    </div>
                  </li>
                ))}
              </ol>
              <button
                className="primary"
                disabled={
                  busy || !racers.data || !machines.data || !gadgets.data
                }
              >
                {busy ? "Saving…" : id ? "Save changes" : "Publish build"}
              </button>
            </aside>
          </form>
        )
      )}
    </>
  );
}
