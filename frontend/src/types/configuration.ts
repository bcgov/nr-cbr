/**
 * Reference data served by `/api/v1/configuration/*` — the lists that fill the UI's dropdowns.
 *
 * <p>These live here rather than beside the screen that first needed them because every screen
 * needs the same lists: CBR has 37 code tables and they all share one shape (a code and a
 * description), so a type per consumer would be 37 copies of the same two fields.
 */

/** One entry of a code table. Mirrors the backend's `CodeOptionResponse`. */
export type CodeOption = {
  code: string;
  description: string;
};

/**
 * An org-unit entry — Forest District and Management Area both use this shape. Mirrors the
 * backend's `OrgUnitResponse`.
 *
 * <p>`orgUnitNo` is the submitted value; the code and name are what the user reads. The code is not
 * unique across the whole org hierarchy, which is why it is not the value.
 */
export type OrgUnitOption = {
  orgUnitNo: string;
  orgUnitCode: string;
  orgUnitName: string;
};

/** The Carbon `Tag` colours this application uses. Mirrors nr-frep's `StatusTagType`. */
export type StatusTagType =
  | 'red'
  | 'magenta'
  | 'purple'
  | 'blue'
  | 'cyan'
  | 'teal'
  | 'green'
  | 'gray'
  | 'cool-gray'
  | 'warm-gray';
