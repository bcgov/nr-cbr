import { describe, expect, it } from 'vitest';

import {
  blockedLine,
  joinedLabels,
  selectedStructures,
  sites,
  splitForDelete,
  structures,
} from './selection';

import type { SelectedStructure } from './types';

describe('joinedLabels', () => {
  it('words one, two and more as a sentence, as the server does', () => {
    expect(joinedLabels(['repairs'])).toBe('repairs');
    expect(joinedLabels(['repairs', 'monitors'])).toBe('repairs and monitors');
    expect(joinedLabels(['inspections', 'repairs', 'monitors'])).toBe(
      'inspections, repairs and monitors',
    );
  });
});

describe('splitForDelete', () => {
  const selection = (entries: [string, SelectedStructure][]) => new Map(entries);

  it('skips a structure the search said is blocked, and attempts the rest', () => {
    const { deletable, blocked } = splitForDelete(
      selection([
        ['1', { name: 'B1', siteId: null, deleteBlockers: [] }],
        ['2', { name: 'B2', siteId: null, deleteBlockers: ['inspections'] }],
      ]),
    );

    expect(deletable).toEqual([{ id: '1', name: 'B1' }]);
    expect(blocked).toEqual([{ id: '2', name: 'B2', blockers: ['inspections'] }]);
  });

  it('attempts a structure whose blockers are unknown, leaving the server to decide', () => {
    const { deletable } = splitForDelete(
      selection([['1', { name: 'B1', siteId: null, deleteBlockers: null }]]),
    );

    expect(deletable).toEqual([{ id: '1', name: 'B1' }]);
  });
});

describe('wording', () => {
  it('names a blocked structure and what is in its way', () => {
    expect(blockedLine({ id: '2', name: 'B2', blockers: ['inspections', 'repairs'] })).toBe(
      'B2 has inspections and repairs',
    );
  });

  it('counts in the singular and the plural', () => {
    expect(structures(1)).toBe('1 structure');
    expect(structures(3)).toBe('3 structures');
    expect(selectedStructures(1)).toBe('1 selected structure');
    expect(selectedStructures(3)).toBe('3 selected structures');
    expect(sites(1)).toBe('1 site');
    expect(sites(2)).toBe('2 sites');
  });
});
