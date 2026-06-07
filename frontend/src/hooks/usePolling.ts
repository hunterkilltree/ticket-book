import { useEffect, useRef } from 'react';

/** Invoke `fn` every `intervalMs` while `enabled`. */
export function usePolling(fn: () => void, intervalMs: number, enabled = true): void {
  const saved = useRef(fn);
  saved.current = fn;
  useEffect(() => {
    if (!enabled) return;
    const id = setInterval(() => saved.current(), intervalMs);
    return () => clearInterval(id);
  }, [intervalMs, enabled]);
}
