/** Decoded JWT claims, as `oidc-client-ts` hands them over (`User.profile` / the access token). */
export type JwtClaims = Record<string, unknown>;

/**
 * Application roles derived from the roles on the access token.
 *
 * Mirrors the backend `RoleConstants` — keep the two in step.
 *
 * The five below are the real CBR roles, confirmed against the legacy
 * `database/roles_users/roles.sql` and its one-proxy-account-per-role mapping in `users.sql`:
 *
 * | Business role | Legacy Oracle role | Legacy proxy |
 * |---|---|---|
 * | Read Only | `CBR_GENERAL` | `CBR$WEB1` |
 * | Inspector Level 1 | `CBR_LEVEL_1` | `CBR$WEB2` |
 * | Inspector Level 2 | `CBR_LEVEL_2` | `CBR$WEB3` |
 * | PENG | `CBR_PROFESSIONAL_ENGINEER` | `CBR$WEB4` |
 * | Administrator | `CBR_ADMINISTRATOR` | `CBR$WEB5` |
 *
 * ⚠ PROVISIONAL — the target CSS/Keycloak role names have not been agreed. See the backend
 * `RoleConstants` and cbr-auth-and-roles.local.md.
 */
export const AVAILABLE_ROLES = [
  'CBR_ADMIN',
  'CBR_GENERAL',
  'CBR_LEVEL_1',
  'CBR_LEVEL_2',
  'CBR_PENG',
  // ── deprecated, no legacy counterpart — see DEPRECATED_ROLES below ──
  'CBR_ENGINEER',
  'CBR_REGIONAL_ENGINEER',
  'CBR_CONTRACT_REGIONAL_ENGINEER',
] as const;

export type ROLE_TYPE = (typeof AVAILABLE_ROLES)[number];

/**
 * Roles retained only so their existing call sites keep compiling. **None of these exists.**
 *
 * `CBR_ENGINEER` was derived from the single `roles="INTERNAL_ENGINEER"` attribute in the legacy
 * `struts-config.xml` — a value `SecurityRequestProcessor` only tests for *emptiness*, never
 * compares. `CBR_REGIONAL_ENGINEER` and `CBR_CONTRACT_REGIONAL_ENGINEER` name PL/SQL **packages**,
 * not roles: `roles.sql` issues no `create role` for either, and they are granted `EXECUTE`
 * unscoped to `CBR_LEVEL_1` / `CBR_LEVEL_2` / `CBR_PROFESSIONAL_ENGINEER`. Legacy CBR has no region
 * scoping at all; the mechanism was imported from nr-frep's per-district CHR roles.
 *
 * Removing them — here, in `authUtils`, `useAuthorization` and the backend — is the outstanding D3
 * cleanup, deliberately kept out of the Keycloak migration.
 */
export const DEPRECATED_ROLES = [
  'CBR_ENGINEER',
  'CBR_REGIONAL_ENGINEER',
  'CBR_CONTRACT_REGIONAL_ENGINEER',
] as const satisfies readonly ROLE_TYPE[];

/**
 * @deprecated No legacy counterpart — see {@link DEPRECATED_ROLES}. Never appears on a token.
 *
 * Note the separator: nr-frep's equivalent prefix had to change from `_` to `-` under Keycloak,
 * because FAM flattens a scoped grant into one role string joined with `-`. That question is moot
 * here — CBR grants no scoped roles, so nothing will ever match this prefix in either spelling.
 */
export const REGIONAL_ENGINEER_PREFIX = 'CBR_REGIONAL_ENGINEER_';

/**
 * @deprecated No legacy counterpart — see {@link DEPRECATED_ROLES}. Never appears on a token.
 *
 * Note this string contains {@link REGIONAL_ENGINEER_PREFIX} as a substring only if matched
 * loosely; always match with `startsWith` so a contract engineer is never read as a full
 * regional engineer.
 */
export const CONTRACT_ENGINEER_PREFIX = 'CBR_CONTRACT_REGIONAL_ENGINEER_';

type RoleValue = string[] | null;

export type USER_PRIVILEGE_TYPE = Partial<Record<ROLE_TYPE, RoleValue>>;

/**
 * Identity providers, in the normalized form the app uses. The realm reports IDIR - MFA as
 * `azureidir` and BCeID Business as `bceidbusiness`; both are folded to these before anything else
 * sees them — see `normalizeProvider` in authUtils.
 */
export const validIdpProviders = ['IDIR', 'BCEIDBUSINESS'] as const;

export type IdpProviderType = (typeof validIdpProviders)[number];

/** Identity provider the user picks at login; maps to a `kc_idp_hint` in services/keycloak.ts. */
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
