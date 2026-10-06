import type { SelectedStructure } from './types';

/** A structure a delete will be attempted on. */
export type DeleteTarget = { id: string; name: string };

/** A ticked structure the search already knows cannot be deleted, and why. */
export type BlockedStructure = DeleteTarget & { blockers: string[] };

/** "repairs", "repairs and monitors", "inspections, repairs and monitors" — as the server words it. */
export const joinedLabels = (labels: string[]): string =>
  labels.length < 2 ? (labels[0] ?? '') : `${labels.slice(0, -1).join(', ')} and ${labels.at(-1)}`;

/**
 * Splits the ticked structures into those a delete will be attempted on and those it will skip.
 *
 * <p>A structure whose blockers are unknown is attempted: the server checks again at delete time
 * and refuses it there if it must.
 */
export const splitForDelete = (
  selected: ReadonlyMap<string, SelectedStructure>,
): { deletable: DeleteTarget[]; blocked: BlockedStructure[] } => {
  const deletable: DeleteTarget[] = [];
  const blocked: BlockedStructure[] = [];
  for (const [id, { name, deleteBlockers }] of selected) {
    if (deleteBlockers && deleteBlockers.length > 0) {
      blocked.push({ id, name, blockers: deleteBlockers });
    } else {
      deletable.push({ id, name });
    }
  }
  return { deletable, blocked };
};

/** "B100 has inspections and repairs" — one line of the skipped list. */
export const blockedLine = ({ name, blockers }: BlockedStructure): string =>
  `${name} has ${joinedLabels(blockers)}`;

/** "1 structure", "3 structures". */
export const structures = (count: number): string => `${count} structure${count === 1 ? '' : 's'}`;

/** "1 selected structure", "3 selected structures" — the count always shown. */
export const selectedStructures = (count: number): string =>
  `${count} selected structure${count === 1 ? '' : 's'}`;

/** "1 site", "3 sites". */
export const sites = (count: number): string => `${count} site${count === 1 ? '' : 's'}`;
