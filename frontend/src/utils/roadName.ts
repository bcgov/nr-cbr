/** Small words left lower-case inside a name, as in "Lake of the Woods". */
const MINOR_WORDS = new Set(['OF', 'THE', 'AND', 'AT', 'ON', 'IN', 'TO']);

/** A word with no vowel is an abbreviation: FSR, RD, RP, NSR, BCTS, MT, CK. */
const NO_VOWEL = /^[B-DF-HJ-NP-TV-Z]+$/;

const capitalize = (part: string): string => {
  const lower = part.toLowerCase();
  // O'NEIL reads O'Neil; MAC'S reads Mac's.
  if (/^[a-z]'[a-z]{2,}/.test(lower)) {
    return lower[0].toUpperCase() + "'" + lower[2].toUpperCase() + lower.slice(3);
  }
  return lower.charAt(0).toUpperCase() + lower.slice(1);
};

/**
 * A road name as the screens show it: LRMOPS stores names in capitals (`BIG CREEK FSR`), the rest
 * of the app shows names in mixed case (`Cariboo-Chilcotin Natural Resource District`).
 *
 * <p>Each word capitalized, except codes, which stay in capitals: a word with a digit (`4A`), a
 * word with no vowel (`FSR`, `RD`), and a one- or two-letter word before a number (`AM 4`, `B 12`).
 * Words joined by a hyphen or a slash are capitalized part by part. Display only: the search filters on the name as
 * LRMOPS stores it.
 */
export const roadNameCase = (name: string | null | undefined): string => {
  if (!name) return '';
  const words = name.split(' ');
  return words
    .map((word, index) => {
      const upper = word.toUpperCase();
      const next = words[index + 1] ?? '';
      if (/\d/.test(upper) || NO_VOWEL.test(upper.replace(/[^A-Z]/g, '') || '1')) return upper;
      if (/^[A-Z]{1,2}$/.test(upper) && /^\d/.test(next)) return upper;
      if (index > 0 && MINOR_WORDS.has(upper)) return upper.toLowerCase();
      // Each part of BONAPARTE/FROSTY or CARIBOO-CHILCOTIN on its own.
      return upper.replace(/[A-Z']+/g, capitalize);
    })
    .join(' ');
};
