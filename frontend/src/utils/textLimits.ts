/**
 * How long a value is, in the unit the database actually counts.
 *
 * <p><b>Bytes, not characters.</b> Every text column in the CBR schema is declared
 * `VARCHAR2(n BYTE)` — see `scripts/THE/TABLES/` in nr-mof-db — so bytes are the only unit that
 * predicts whether a save will succeed. A character count would read `255 / 255` in black on a
 * value the database then rejects with ORA-12899, which is exactly the confusion a counter exists
 * to remove: an accented character costs two bytes, an em dash three, an emoji four.
 */
export const byteLength = (value: string | undefined): number =>
  value ? new TextEncoder().encode(value).length : 0;

/**
 * The over-limit message, or `''` when the value fits.
 *
 * <p>Phrased in the same units as the counter and without the word "characters" — the limit is
 * bytes, so "260 characters" can legitimately be over a 255 limit, and saying otherwise would read
 * as a bug rather than as a rule.
 */
export const overLimitError = (value: string | undefined, limit: number): string => {
  const used = byteLength(value);
  return used > limit ? `Too long — the limit is ${limit} and this entry uses ${used}.` : '';
};
