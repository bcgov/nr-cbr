import { describe, expect, it } from 'vitest';

import { apiErrorMessage } from './apiError';

/**
 * The one thing this has to get right is preferring the server's sentence over the status phrase.
 * An `ApiError`'s own `message` is "Conflict"; the body's `detail` is "Site S-1 still has 2
 * archived structures." Returning the first leaves the user with no next step.
 */
describe('apiErrorMessage', () => {
  it('prefers an RFC 7807 detail, which is what CBR returns', () => {
    const error = Object.assign(new Error('Conflict'), {
      body: { type: 'about:blank', title: 'Conflict', status: 409, detail: 'Still in use.' },
    });

    expect(apiErrorMessage(error, 'fallback')).toBe('Still in use.');
  });

  it('falls back to message, the shape nr-frep returns', () => {
    const error = Object.assign(new Error('Bad Request'), { body: { message: 'Not a month.' } });

    expect(apiErrorMessage(error, 'fallback')).toBe('Not a month.');
  });

  it('ignores a blank detail rather than showing an empty explanation', () => {
    const error = Object.assign(new Error('Conflict'), { body: { detail: '   ' } });

    expect(apiErrorMessage(error, 'fallback')).toBe('Conflict');
  });

  it('uses the error message when there is no body', () => {
    expect(apiErrorMessage(new Error('Network Error'), 'fallback')).toBe('Network Error');
  });

  it('uses the caller fallback for anything else', () => {
    expect(apiErrorMessage(undefined, 'Could not delete the site.')).toBe(
      'Could not delete the site.',
    );
    expect(apiErrorMessage({ body: null }, 'Could not delete the site.')).toBe(
      'Could not delete the site.',
    );
  });
});
