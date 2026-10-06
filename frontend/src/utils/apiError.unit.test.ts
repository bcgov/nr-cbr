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

  it('uses the caller fallback for a server error, never its internal text', () => {
    // What the ORA-01031 on a structure delete put in front of the user, word for word.
    const error = Object.assign(new Error('Internal Server Error'), {
      status: 500,
      body: {
        status: 500,
        message:
          'Request processing failed: org.hibernate.exception.SQLGrammarException: JDBC ' +
          'exception executing SQL [DELETE FROM THE.CROSSING_STRUCTURE_NAME_HIST WHERE ' +
          'CROSSING_STRUCTURE_ID = ?] [ORA-01031: insufficient privileges ] [n/a]',
      },
    });

    expect(apiErrorMessage(error, 'B1 could not be deleted.')).toBe('B1 could not be deleted.');
  });

  it('still shows the sentence a 4xx carries', () => {
    const error = Object.assign(new Error('Conflict'), {
      status: 409,
      body: { detail: 'Structure B1 has inspections and cannot be deleted.' },
    });

    expect(apiErrorMessage(error, 'fallback')).toBe(
      'Structure B1 has inspections and cannot be deleted.',
    );
  });
});
