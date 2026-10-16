import type { OutstandingGroup } from '@/pages/StructureDetail/format';

/** Add Structure's tabs, as the structure page's. Spans & Piers is there for a bridge only. */
export type AddStructureTab = 'information' | 'details' | 'spans-and-piers' | 'inspections';

/** Each section's card, in the order the cards are shown, and the tab it is on. */
const CARDS: { prefix: string; card: string; tab: AddStructureTab }[] = [
  { prefix: 'structureName', card: 'Structure and site', tab: 'information' },
  { prefix: 'siteId', card: 'Structure and site', tab: 'information' },
  { prefix: 'typeClassCode', card: 'Structure', tab: 'details' },
  { prefix: 'details.', card: 'Structure', tab: 'details' },
  { prefix: 'bridge.', card: 'Bridge', tab: 'details' },
  { prefix: 'culvert.', card: 'Culvert', tab: 'details' },
  { prefix: 'loadRating.', card: 'Load Rating Details', tab: 'details' },
  { prefix: 'replacement.', card: 'Replacement Details', tab: 'details' },
  { prefix: 'comment', card: 'Comment', tab: 'details' },
  { prefix: 'spans', card: 'Spans', tab: 'spans-and-piers' },
  { prefix: 'piers', card: 'Piers', tab: 'spans-and-piers' },
  { prefix: 'schedule.', card: 'Inspection Schedule', tab: 'inspections' },
];

/** A message's card, by its key: `structureName`, `bridge.deckWidth`, `spans`, … */
const placeOf = (key: string) =>
  CARDS.find(({ prefix }) => (prefix.endsWith('.') ? key.startsWith(prefix) : key === prefix));

/** The tab a message is shown on; one the page does not place, on the first. */
export const tabOf = (key: string): AddStructureTab => placeOf(key)?.tab ?? 'information';

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

/** How many messages each tab holds. */
export const countByTab = (messages: Record<string, string>): Record<AddStructureTab, number> => {
  const counts: Record<AddStructureTab, number> = {
    'information': 0,
    'details': 0,
    'spans-and-piers': 0,
    'inspections': 0,
  };
  for (const key of Object.keys(messages)) counts[tabOf(key)] += 1;
  return counts;
};

/** A tab's messages grouped by card, in the order the cards are shown — the tab's list. */
export const groupsOf = (
  messages: Record<string, string>,
  tab: AddStructureTab,
): OutstandingGroup[] => {
  const placed = Object.entries(messages)
    .filter(([key]) => tabOf(key) === tab)
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
