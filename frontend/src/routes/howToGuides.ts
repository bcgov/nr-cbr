import guides from './howToGuides.json';

import { ROLE_CAPABILITIES } from '@/context/auth/types';

/**
 * The how-to guides: one PDF per combination of what a user can do, served from `public/` and
 * opened in a new tab from the side nav's Support block.
 *
 * <p>Built from `docs/guides/cbr-guide.md` by `npm run guides:build`. The Markdown tags each section
 * with the capability it needs (`<!-- guide: approve -->`), and the build prints one PDF per
 * combination in `howToGuides.json` — so a user reads only what their roles let them do, whatever mix of roles FAM
 * gave them.
 *
 * <p>`howToGuides.json` is the one table both sides read: the build script, to know which PDFs to
 * print, and this module, to know which one to link. Change it and rebuild the guides together.
 *
 * <h2>Why 10</h2>
 * The working roles nest (View All ⊂ Level 1 ⊂ Level 2 ⊂ PENG ⊂ Inspector Reviewer), so a user's
 * working roles come down to one of five levels. `CBR_ADMIN` sits beside that ladder rather than on
 * it, so each level comes with or without it: 5 × 2. `CBR_ADMIN` on its own reads like View All plus
 * administration, so it shares `cbr-guide-view-admin.pdf`. The count changes only when the role
 * list does.
 */

/** A section tag in the guide Markdown, named after the `ROLE_CAPABILITIES` group it follows. */
export type GuideCapability = 'read' | 'write' | 'destructive' | 'approve' | 'review' | 'admin';

type GuideLevel = { id: string; title: string; capabilities: GuideCapability[] };

const LEVELS = guides.levels as GuideLevel[];
const ADMIN = guides.admin as { capability: 'admin'; suffix: string; title: string };

/** Where a guide is served from. Must match the build script's output names. */
const fileFor = (level: GuideLevel, admin: boolean) =>
  `/cbr-guide-${level.id}${admin ? ADMIN.suffix : ''}.pdf`;

/** Every guide the build produces — the 10 PDFs in `public/`. */
export const HOW_TO_GUIDES: readonly string[] = LEVELS.flatMap((level) => [
  fileFor(level, false),
  fileFor(level, true),
]);

/**
 * The capabilities these roles carry, in the guide's terms.
 *
 * <p>Read from `ROLE_CAPABILITIES`, the same matrix every screen uses, so the guide and the app
 * cannot disagree about what a role may do. The guide's `write` covers both `inspectionWrite` and
 * `write`: since Level 0 went they admit the same roles.
 */
const capabilitiesOf = (roles: readonly string[]): Set<GuideCapability> => {
  const holds = (allowed: readonly string[]) => allowed.some((role) => roles.includes(role));
  const held = new Set<GuideCapability>();
  if (holds(ROLE_CAPABILITIES.read)) held.add('read');
  if (holds(ROLE_CAPABILITIES.write) || holds(ROLE_CAPABILITIES.inspectionWrite)) {
    held.add('write');
  }
  if (holds(ROLE_CAPABILITIES.destructive)) held.add('destructive');
  if (holds(ROLE_CAPABILITIES.approve)) held.add('approve');
  if (holds(ROLE_CAPABILITIES.review)) held.add('review');
  if (holds(ROLE_CAPABILITIES.admin)) held.add('admin');
  return held;
};

/**
 * The how-to guide for these roles, or `null` when they carry no CBR role at all.
 *
 * <p>The highest level whose capabilities the user holds in full, plus the administration chapter
 * when they hold `CBR_ADMIN`. Someone holding several working roles gets the highest; FAM is free
 * to grant a mix, and the ladder means the higher role already covers the lower one.
 */
export const howToGuideFor = (roles: readonly string[]): string | null => {
  const held = capabilitiesOf(roles);
  const level = [...LEVELS]
    .reverse()
    .find((candidate) => candidate.capabilities.every((capability) => held.has(capability)));
  return level ? fileFor(level, held.has(ADMIN.capability)) : null;
};
