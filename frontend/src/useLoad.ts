import { useEffect, useState } from "react";
import { api } from "./api";
export function useLoad<T>(path: string, refresh = 0) {
  const [result, setResult] = useState<{
    path: string; refresh: number; data?: T; error: string; loading: boolean;
  }>({ path, refresh, error: "", loading: !!path });
  useEffect(() => {
    if (!path) {
      setResult({ path, refresh, error: "", loading: false });
      return;
    }
    const controller = new AbortController();
    setResult({ path, refresh, error: "", loading: true });
    api<T>(path, { signal: controller.signal })
      .then((value) => {
        if (!controller.signal.aborted) setResult({ path, refresh, data: value, error: "", loading: false });
      })
      .catch((e) => {
        if (!controller.signal.aborted) setResult({ path, refresh, error: e.message, loading: false });
      });
    return () => controller.abort();
  }, [path, refresh]);
  // Do not show the previous query's items/totals during the render before its effect runs.
  const current = result.path === path && result.refresh === refresh
    ? result : { data: undefined, error: "", loading: !!path };
  return { data: current.data, error: current.error, loading: current.loading };
}
