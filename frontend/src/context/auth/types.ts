import type { JWT as AmplifyJWT } from '@aws-amplify/core';

export type JWT = AmplifyJWT;

/**
 * Application roles derived from Cognito (FAM) groups.
 *
 * Mirrors the backend `RoleConstants` — keep the two in step.
 *
 * `CBR_ADMIN`, `CBR_GENERAL`, `CBR_ENGINEER` and `CBR_PENG` are raw Cognito groups.
 * `CBR_REGIONAL_ENGINEER` and `CBR_CONTRACT_REGIONAL_ENGINEER` are **synthetic aggregates** — there
 * is no raw group of either name. They are derived when the user holds one or more of the
 * region-scoped groups below, and their privilege value carries the org-unit codes (a scoped role:
 * `string[]` rather than `null`). Same mechanism as nr-frep's per-district CHR roles.
 *
 * ⚠ PROVISIONAL — the FAM role set for CBR has not been agreed. See the backend `RoleConstants`
 * and cbr-auth-and-roles.local.md.
 */
export const AVAILABLE_ROLES = [
  'CBR_ADMIN',
  'CBR_GENERAL',
  'CBR_ENGINEER',
  'CBR_PENG',
  'CBR_REGIONAL_ENGINEER',
  'CBR_CONTRACT_REGIONAL_ENGINEER',
] as const;

export type ROLE_TYPE = (typeof AVAILABLE_ROLES)[number];

/**
 * Prefix of the region-scoped regional-engineer Cognito groups:
 * `CBR_REGIONAL_ENGINEER_<org unit code>`. Grants the destructive operations the legacy
 * `CBR_REGIONAL_ENGINEER` package held, within the holder's region only.
 */
export const REGIONAL_ENGINEER_PREFIX = 'CBR_REGIONAL_ENGINEER_';

/**
 * Prefix of the region-scoped contract-engineer groups:
 * `CBR_CONTRACT_REGIONAL_ENGINEER_<org unit code>`. Narrower — the legacy package held only
 * `UPDATE_SITE`.
 *
 * Note this string contains {@link REGIONAL_ENGINEER_PREFIX} as a substring only if matched
 * loosely; always match with `startsWith` so a contract engineer is never read as a full
 * regional engineer.
 */
export const CONTRACT_ENGINEER_PREFIX = 'CBR_CONTRACT_REGIONAL_ENGINEER_';

type RoleValue = string[] | null;

export type USER_PRIVILEGE_TYPE = Partial<Record<ROLE_TYPE, RoleValue>>;

export const validIdpProviders = ['IDIR', 'BCEIDBUSINESS'] as const;

export type IdpProviderType = (typeof validIdpProviders)[number];

/** Identity provider the user picks at login. Mirrors nr-fspts. */
export type LoginProvider = 'idir' | 'bceid';

export type FamLoginUser = {
  providerUsername?: string;
  userName?: string;
  displayName?: string;
  email?: string;
  idpProvider?: IdpProviderType;
  roles?: ROLE_TYPE[];
  authToken?: string;
  exp?: number;
  privileges: USER_PRIVILEGE_TYPE;
  firstName?: string;
  lastName?: string;
};
