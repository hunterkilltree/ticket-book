import { AxiosError } from 'axios';
import type { ProblemDetail } from '@/types';

/** Normalize any thrown error into an RFC 7807 ProblemDetail. */
export function toProblemDetail(err: unknown): ProblemDetail {
  if (err instanceof AxiosError) {
    const data = err.response?.data as Partial<ProblemDetail> | undefined;
    if (data && typeof data.title === 'string' && typeof data.status === 'number') {
      return {
        type: data.type ?? 'about:blank',
        title: data.title,
        status: data.status,
        detail: data.detail,
        instance: data.instance,
      };
    }
    return {
      type: 'about:blank',
      title: err.message || 'Request failed',
      status: err.response?.status ?? 0,
    };
  }
  return { type: 'about:blank', title: 'Unexpected error', status: 0 };
}
