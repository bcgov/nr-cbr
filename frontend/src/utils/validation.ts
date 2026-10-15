/**
 * Shared vocabulary for validation that runs while the user is still typing.
 *
 * <p>A field's rules fall into two families, and only one of them can be shown on every keystroke:
 *
 * <ul>
 *   <li><b>Too much</b> — a letter in a number, a third decimal place, a value above the maximum,
 *       more bytes than the column holds. No continuation of the text makes it valid, so saying so
 *       immediately is always right.
 *   <li><b>Too little</b> — a required field still empty, a value part-way through a pattern.
 *       Typing more may well fix it, so reporting it now marks a field red for everyone who fills
 *       the form in the ordinary way.
 * </ul>
 *
 * <p>Rules take a {@link ValidationMode} and skip the second family in `'typing'`. The same rule
 * set runs in both modes, so the two can never disagree about what a field allows — `'settled'` is
 * the full check, and it is the one that guards Save.
 */
export type ValidationMode = 'typing' | 'settled';

/**
 * True while `text` is a number the user is part-way through writing: empty, a lone sign, or digits
 * ending in a decimal point (`"12."`).
 *
 * <p>Only consulted in `'typing'` mode — a value left like this is genuinely malformed once the
 * user is done with it.
 *
 * <p>A finished number is deliberately <em>not</em> "in progress", digits alone included: `"100"`
 * is a complete value, and a range rule has to be free to say so while it is on screen.
 */
export const isNumberInProgress = (text: string): boolean => /^[+-]?(?:\d*\.)?$/.test(text.trim());

/**
 * The errors to show for fields the user has finished with: those on a field that has been left.
 *
 * <p>A required field left empty says so as soon as it is left — the app's one rule for inline
 * errors (see `useFieldErrors`): a field the user has not been in stays quiet until Save, one they
 * have been in and left shows what is wrong with it, blank included.
 *
 * <p>Fed the `'settled'` error set, since that is the full check; the caller merges the result over
 * whatever `'typing'` already had to say.
 */
export const errorsForSettledFields = <T extends string>(
  settledErrors: Partial<Record<T, string>>,
  settled: ReadonlySet<string>,
): Partial<Record<T, string>> =>
  Object.fromEntries(Object.entries(settledErrors).filter(([key]) => settled.has(key))) as Partial<
    Record<T, string>
  >;
