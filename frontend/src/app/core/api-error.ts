import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from './models';

/**
 * Turns whatever went wrong into a sentence worth showing.
 *
 * <p>Every component used to inline `err?.error?.message ?? 'Something failed'`,
 * which loses the two cases a user most needs told apart: the network being down
 * and a field being wrong.
 */
export function describeError(error: unknown, fallback: string): string {
  if (!(error instanceof HttpErrorResponse)) {
    return fallback;
  }

  // status 0 is "the request never got an answer" — offline, DNS, CORS, or a
  // free-tier instance that is still waking up.
  if (error.status === 0 || error.status === 502 || error.status === 503 || error.status === 504) {
    return 'Cannot reach the server. It may still be starting up — try again in a moment.';
  }

  const body = error.error as Partial<ApiError> | string | null;
  if (typeof body === 'string' || !body) {
    return fallback;
  }

  // Field errors say more than "Validation failed" does.
  if (body.fields && Object.keys(body.fields).length > 0) {
    return Object.entries(body.fields)
      .map(([field, message]) => `${humanise(field)} ${message}`)
      .join('. ');
  }

  return body.message ?? fallback;
}

/** "spentOn" -> "Spent on" */
function humanise(field: string): string {
  const spaced = field.replace(/([A-Z])/g, ' $1').toLowerCase();
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}
