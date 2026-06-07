import { useEffect, useState } from 'react';
import { useDebounce } from '@/hooks/useDebounce';
import type { Event, ProblemDetail } from '@/types';
import { listEvents } from './api';

export function useEvents(search: string) {
  const debounced = useDebounce(search, 300);
  const [events, setEvents] = useState<Event[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ProblemDetail | null>(null);

  useEffect(() => {
    let active = true;
    setLoading(true);
    listEvents(debounced).then((res) => {
      if (!active) return;
      if (res.ok) {
        setEvents(res.data);
        setError(null);
      } else {
        setError(res.error);
      }
      setLoading(false);
    });
    return () => {
      active = false;
    };
  }, [debounced]);

  return { events, loading, error };
}
