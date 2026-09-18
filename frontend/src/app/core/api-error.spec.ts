import { HttpErrorResponse } from '@angular/common/http';
import { describeError } from './api-error';

describe('describeError', () => {
  it('uses the server message when there is one', () => {
    const err = new HttpErrorResponse({
      status: 409,
      error: { message: 'Category already exists', fields: null }
    });
    expect(describeError(err, 'fallback')).toBe('Category already exists');
  });

  it('spells out field errors instead of a bare "Validation failed"', () => {
    const err = new HttpErrorResponse({
      status: 400,
      error: { message: 'Validation failed', fields: { spentOn: 'must be between 2000 and 2100' } }
    });
    expect(describeError(err, 'fallback')).toBe('Spent on must be between 2000 and 2100');
  });

  it('tells an unreachable server apart from a rejected request', () => {
    const err = new HttpErrorResponse({ status: 0 });
    expect(describeError(err, 'fallback')).toContain('Cannot reach the server');
  });

  it('falls back when the body is not the API error shape', () => {
    const err = new HttpErrorResponse({ status: 500, error: '<html>' });
    expect(describeError(err, 'fallback')).toBe('fallback');
  });

  it('falls back for anything that is not an HTTP error', () => {
    expect(describeError(new Error('boom'), 'fallback')).toBe('fallback');
  });
});
