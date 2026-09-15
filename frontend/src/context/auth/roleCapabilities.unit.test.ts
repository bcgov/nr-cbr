import { describe, expect, it } from 'vitest';

import { AVAILABLE_ROLES, ROLE_CAPABILITIES } from './types';

import type { ROLE_TYPE } from './types';

/**
 * Pins `ROLE_CAPABILITIES` to the same ladder the backend's `CbrRoles` defines.
 *
 * <p>This is the frontend half of `CbrAuthoritiesTest`. The two files are edited independently and
 * a disagreement between them is silent in both directions: too narrow here hides a control the API
 * would have allowed, too wide offers one that comes back 403. Both have happened — `destructive`
 * once omitted `CBR_PENG` while the backend admitted it, because the cumulative rule was applied to
 * every other capability and not that one.
 *
 * <p>The ladder is cumulative, so each capability is "this rung and everything above it". `CBR_ADMIN`
 * sits outside it and appears in exactly two places: `read` and its own admin gate.
 */
const LADDER: ROLE_TYPE[] = [
  'CBR_GENERAL',
  'CBR_LEVEL_0',
  'CBR_LEVEL_1',
  'CBR_LEVEL_2',
  'CBR_PENG',
];

/** Every rung at or above `floor` — what a capability with that floor must admit. */
const atLeast = (floor: ROLE_TYPE): ROLE_TYPE[] => LADDER.slice(LADDER.indexOf(floor));

describe('ROLE_CAPABILITIES mirrors the backend ladder', () => {
  it.each([
    ['inspectionWrite', 'CBR_LEVEL_0'],
    ['write', 'CBR_LEVEL_1'],
    ['destructive', 'CBR_LEVEL_2'],
    ['approve', 'CBR_PENG'],
  ] as const)('%s admits exactly its floor (%s) and above', (capability, floor) => {
    expect([...ROLE_CAPABILITIES[capability]].sort()).toEqual([...atLeast(floor)].sort());
  });

  it('read admits every ladder role plus CBR_ADMIN', () => {
    // The one deliberate divergence from legacy (2026-09-15): WebADE's ADMINISTRATOR had no read.
    expect([...ROLE_CAPABILITIES.read].sort()).toEqual(
      [...atLeast('CBR_GENERAL'), 'CBR_ADMIN'].sort(),
    );
  });

  it('no write capability admits CBR_ADMIN', () => {
    const writeCapabilities = [
      ROLE_CAPABILITIES.inspectionWrite,
      ROLE_CAPABILITIES.write,
      ROLE_CAPABILITIES.destructive,
      ROLE_CAPABILITIES.approve,
    ];

    for (const capability of writeCapabilities) {
      expect(capability).not.toContain('CBR_ADMIN');
    }
  });

  it('every capability names only roles that exist', () => {
    for (const roles of Object.values(ROLE_CAPABILITIES)) {
      for (const role of roles) {
        expect(AVAILABLE_ROLES).toContain(role);
      }
    }
  });

  it('AVAILABLE_ROLES is exactly the ladder plus CBR_ADMIN — no obsolete entries', () => {
    expect([...AVAILABLE_ROLES].sort()).toEqual([...LADDER, 'CBR_ADMIN'].sort());
  });
});
