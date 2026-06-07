/** RFC 7807 Problem Details — the shape every backend error conforms to. */
export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail?: string;
  instance?: string;
}

/** Discriminated result returned by the typed API layer. */
export type ApiResult<T> =
  | { ok: true; data: T }
  | { ok: false; error: ProblemDetail };
