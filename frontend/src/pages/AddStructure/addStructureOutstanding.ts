import type { OutstandingGroup } from '@/pages/StructureDetail/format';

/**
 * Each section's card, in the order Add Structure shows them, by the prefix of its messages' keys.
 * One Details tab holds them all (UAT, "CBR Test Changes", 2026-11) — the structure page's
 * Information, Spans & Piers and Inspections cards among them.
 */
const CARDS: { prefix: string; card: string }[] = [
  { prefix: 'structureName', card: 'Structure and site' },
  { prefix: 'siteId', card: 'Structure and site' },
  { prefix: 'typeClassCode', card: 'Structure' },
  { prefix: 'details.', card: 'Structure' },
  { prefix: 'bridge.', card: 'Bridge' },
  { prefix: 'spans', card: 'Spans' },
  { prefix: 'piers', card: 'Piers' },
  { prefix: 'culvert.', card: 'Culvert' },
  { prefix: 'loadRating.', card: 'Load Rating Details' },
  { prefix: 'replacement.', card: 'Replacement Details' },
  { prefix: 'schedule.', card: 'Inspection Schedule' },
  { prefix: 'comment', card: 'Comment' },
];

/** A message's card, by its key: `structureName`, `bridge.deckWidth`, `spans`, … */
const placeOf = (key: string) =>
  CARDS.find(({ prefix }) => (prefix.endsWith('.') ? key.startsWith(prefix) : key === prefix));

/** Every message still standing — the page's own and the server's — dropping the empty ones. */
export const outstandingOf = (
  ...sources: Record<string, string | undefined>[]
): Record<string, string> => {
  const outstanding: Record<string, string> = {};
  for (const source of sources) {
    for (const [key, message] of Object.entries(source)) {
      if (message) outstanding[key] = message;
    }
  }
  return outstanding;
};

/**
 * The messages grouped by card, in the order the cards are shown — the list at the top of the
 * tab. A message the page does not place goes with the first card.
 */
export const groupsOf = (messages: Record<string, string>): OutstandingGroup[] => {
  const placed = Object.entries(messages)
    .map(([key, message]) => {
      const place = placeOf(key);
      return {
        card: place?.card ?? 'Structure and site',
        order: place ? CARDS.indexOf(place) : 0,
        message,
      };
    })
    .sort((a, b) => a.order - b.order);
  const groups: OutstandingGroup[] = [];
  for (const { card, message } of placed) {
    const group = groups.find((each) => each.title === card);
    if (group) group.items.push(message);
    else groups.push({ title: card, items: [message] });
  }
  return groups;
};
