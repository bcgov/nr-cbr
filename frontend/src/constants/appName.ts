/**
 * The application's display name, used for the header, the browser tab title and the landing and
 * dashboard headings.
 *
 * A constant rather than configuration — carried over from nr-frep along with the reason: it used to
 * be a `VITE_APP_NAME` deployment variable, which let the name differ per environment and did, with
 * the header and the dashboard heading disagreeing. One name in one place is what stops that.
 */
export const APP_NAME = 'CBR';

/**
 * The expanded name, shown bold in the header after the {@link APP_NAME} prefix
 * ("CBR Corporate Bridge Register"), matching the FSPTS and FREP header treatment.
 *
 * Kept separate from {@link APP_NAME}, which is still what the browser tab, the landing heading and
 * the dashboard heading use — spelling the whole thing out in those places would be shouting.
 */
export const APP_FULL_NAME = 'Corporate Bridge Register';
