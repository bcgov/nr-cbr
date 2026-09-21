import { describe, expect, it } from 'vitest';

import type { ClientSuggestion } from '@/types/client';

import { MIN_CLIENT_TERM_LENGTH, clientLabel, isSearchableTerm } from '@/utils/clientSearch';

const suggestion = (overrides: Partial<ClientSuggestion> = {}): ClientSuggestion => ({
  clientNumber: '00001012',
  clientLocnCode: '00',
  clientName: 'CANFOR CORPORATION',
  clientLocnName: null,
  city: 'Vancouver',
  ...overrides,
});

describe('clientLabel', () => {
  it('reads name, division, city and the key it filters on', () => {
    expect(clientLabel(suggestion({ clientLocnName: 'Northern Division' }))).toBe(
      'CANFOR CORPORATION · Northern Division · Vancouver · 00001012-00',
    );
  });

  it('drops the division when there is none, rather than leaving a stranded separator', () => {
    // The common case: most clients have a single office and no division name at all.
    expect(clientLabel(suggestion())).toBe('CANFOR CORPORATION · Vancouver · 00001012-00');
  });

  it('keeps the two locations of one client apart', () => {
    // The whole reason a suggestion is a location rather than a client. Two rows with the same
    // name and city are still distinguishable, because the location code always shows.
    const vancouver = clientLabel(suggestion({ clientLocnCode: '00' }));
    const second = clientLabel(suggestion({ clientLocnCode: '01' }));

    expect(vancouver).not.toBe(second);
  });

  it('renders a stand-in item carrying only a name, with no undefined key appended', () => {
    // The combo box renders its *selected* item through this, and that item is a stand-in holding
    // nothing but the label — an unguarded template printed "· undefined-undefined" after it.
    const standIn = { clientName: 'CANFOR CORPORATION · Vancouver · 00001012-00' };

    expect(clientLabel(standIn as ClientSuggestion)).toBe(
      'CANFOR CORPORATION · Vancouver · 00001012-00',
    );
  });
});

describe('isSearchableTerm', () => {
  it('rejects a term too short to narrow anything', () => {
    expect(isSearchableTerm('')).toBe(false);
    expect(isSearchableTerm('   ')).toBe(false);
    expect(isSearchableTerm('ca')).toBe(false);
  });

  it(`accepts ${MIN_CLIENT_TERM_LENGTH} characters or more`, () => {
    expect(isSearchableTerm('can')).toBe(true);
    expect(isSearchableTerm('canfor')).toBe(true);
  });

  it('accepts digits at any length, because a client number is an exact key', () => {
    // "5" asks for client 00000005 and can return only that client's locations, where two letters
    // would match a large share of the table.
    expect(isSearchableTerm('5')).toBe(true);
    expect(isSearchableTerm('66')).toBe(true);
  });

  it('measures after trimming', () => {
    expect(isSearchableTerm('  ca  ')).toBe(false);
    expect(isSearchableTerm('  can  ')).toBe(true);
  });
});
