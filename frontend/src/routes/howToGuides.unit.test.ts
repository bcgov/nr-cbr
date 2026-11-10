import { existsSync, readdirSync } from 'node:fs';
import { resolve } from 'node:path';

import { describe, expect, it } from 'vitest';

import { HOW_TO_GUIDES, howToGuideFor } from './howToGuides';

import { AVAILABLE_ROLES } from '@/context/auth/types';

/** Every subset of the given roles — 2^6 = 64 for CBR's six. */
const everyCombination = (roles: readonly string[]): string[][] =>
  roles.reduce<string[][]>(
    (subsets, role) => [...subsets, ...subsets.map((subset) => [...subset, role])],
    [[]],
  );

describe('howToGuideFor', () => {
  it.each([
    [['CBR_GENERAL'], '/cbr-guide-view.pdf'],
    [['CBR_LEVEL_1'], '/cbr-guide-level-1.pdf'],
    [['CBR_LEVEL_2'], '/cbr-guide-level-2.pdf'],
    [['CBR_PENG'], '/cbr-guide-peng.pdf'],
    [['CBR_INSPECTION_REVIEWER'], '/cbr-guide-reviewer.pdf'],
  ])('gives %j the guide for its level', (roles, guide) => {
    expect(howToGuideFor(roles)).toBe(guide);
  });

  it('adds the administration chapter to whatever level the user holds', () => {
    expect(howToGuideFor(['CBR_ADMIN', 'CBR_LEVEL_1'])).toBe('/cbr-guide-level-1-admin.pdf');
    expect(howToGuideFor(['CBR_ADMIN', 'CBR_PENG'])).toBe('/cbr-guide-peng-admin.pdf');
  });

  it('gives CBR_ADMIN alone the View All guide with administration — admin carries read', () => {
    expect(howToGuideFor(['CBR_ADMIN'])).toBe('/cbr-guide-view-admin.pdf');
    expect(howToGuideFor(['CBR_ADMIN', 'CBR_GENERAL'])).toBe('/cbr-guide-view-admin.pdf');
  });

  it('takes the highest level when several working roles are held', () => {
    expect(howToGuideFor(['CBR_GENERAL', 'CBR_LEVEL_2', 'CBR_LEVEL_1'])).toBe(
      '/cbr-guide-level-2.pdf',
    );
  });

  it('offers none to someone with no CBR role', () => {
    expect(howToGuideFor([])).toBeNull();
    expect(howToGuideFor(['SOME_OTHER_APP_ROLE'])).toBeNull();
  });

  it('lands every possible mix of roles on one of exactly 10 guides', () => {
    // The promise the guides rest on: whatever FAM grants, there is a guide for it, and the set
    // never grows past 10 unless the role list itself changes.
    const reached = new Set(
      everyCombination(AVAILABLE_ROLES)
        .filter((roles) => roles.length > 0)
        .map((roles) => howToGuideFor(roles)),
    );

    expect(reached).not.toContain(null);
    expect([...reached].sort()).toEqual([...HOW_TO_GUIDES].sort());
    expect(HOW_TO_GUIDES).toHaveLength(10);
  });
});

describe('the built guides', () => {
  const publicDir = resolve(__dirname, '../../public');

  it('has every guide in public/ — run `npm run guides:build` if this fails', () => {
    for (const guide of HOW_TO_GUIDES) {
      expect(existsSync(resolve(publicDir, guide.slice(1))), guide).toBe(true);
    }
  });

  it('has no other cbr-guide PDF lying about', () => {
    const onDisk = readdirSync(publicDir).filter((file) => /^cbr-guide-.*\.pdf$/.test(file));

    expect(onDisk.map((file) => `/${file}`).sort()).toEqual([...HOW_TO_GUIDES].sort());
  });
});
