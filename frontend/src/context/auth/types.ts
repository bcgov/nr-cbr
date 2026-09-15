/** Decoded JWT claims, as `oidc-client-ts` hands them over (`User.profile` / the access token). */
export type JwtClaims = Record<string, unknown>;

/**
 * Application roles derived from the roles on the access token.
 *
 * Mirrors the backend `RoleConstants` — keep the two in step.
 *
 * The six live roles, from the WebADE export (`APPLICATION_ROLE` for `APPLICATION_ACRONYM='CBR'`),
 * each reached in the legacy app through one proxy connection account:
 *
 * | WebADE role | Legacy Oracle role | Legacy proxy | Business profile it appears in |
 * |---|---|---|---|
 * | `GENERAL` | `CBR_GENERAL` | `CBR$WEB1` | every working profile; alone in `CBR_READ_ONLY` |
 * | `LEVEL_0` | `CBR_LEVEL_0` | `CBR$WEB6` | `CBR_INSPECTOR_TECHNICIAN_0` and above |
 * | `LEVEL_1` | `CBR_LEVEL_1` | `CBR$WEB2` | `CBR_INSPECTOR_TECHNICIAN_1` and above |
 * | `LEVEL_2` | `CBR_LEVEL_2` | `CBR$WEB3` | `CBR_INSPECTOR_TECHNICIAN_2` and above |
 * | `PROFESSIONAL_ENGINEER` | `CBR_PROFESSIONAL_ENGINEER` | `CBR$WEB4` | `CBR_MINISTRY_PENG` |
 * | `ADMINISTRATOR` | `CBR_ADMINISTRATOR` | `CBR$WEB5` | `CBR_ADMINISTRATOR`, on its own |
 *
 * ⚠ PROVISIONAL — the target CSS/Keycloak role *names* have not been agreed, nor whether FAM grants
 * profiles or roles. What each one can do is settled; see `ROLE_CAPABILITIES` below, the backend
 * `RoleConstants`, and cbr-auth-and-roles.local.md.
 */
export const AVAILABLE_ROLES = [
  'CBR_ADMIN',
  'CBR_GENERAL',
  'CBR_LEVEL_0',
  'CBR_LEVEL_1',
  'CBR_LEVEL_2',
  'CBR_PENG',
] as const;

/**
 * What each live role actually grants, from the WebADE `ACTION_LNK` export. Mirrors the backend
 * `RoleConstants`; the full tables are in cbr-auth-and-roles.local.md.
 *
 * | Role | Grants |
 * |---|---|
 * | `CBR_GENERAL` | 24 privileges, **all `/show*`** — read only |
 * | `CBR_LEVEL_0` | `/saveInspection`, `/showOfflineInspection`, `/level0Access` |
 * | `CBR_LEVEL_1` | create/edit: save site / structure / inspection, uploads, repair responsibility |
 * | `CBR_LEVEL_2` | destructive: all nine deletes, archive, add site, status override, bulk upload |
 * | `CBR_PENG` | `/approveInspection` (**held by this role alone**), `/pEngAccess` |
 * | `CBR_ADMIN` | the two admin screens, **plus read** |
 *
 * Profiles nest: READ_ONLY ⊂ TECHNICIAN_0 ⊂ TECHNICIAN_1 ⊂ TECHNICIAN_2 ⊂ MINISTRY_PENG.
 * `CBR_ADMIN` stands apart — it is not on that ladder.
 *
 * **`CBR_ADMIN` gets read, diverging from legacy** (decided 2026-09-15): WebADE's `ADMINISTRATOR`
 * held three privileges and its profile bundled no `GENERAL`, so an administrator could not open a
 * site. CBR grants them search, view and reports. It is still **read only** — every write
 * capability below excludes `CBR_ADMIN`, so do not treat it as a superuser in the UI.
 */
export const ROLE_CAPABILITIES = {
  read: ['CBR_GENERAL', 'CBR_LEVEL_0', 'CBR_LEVEL_1', 'CBR_LEVEL_2', 'CBR_PENG', 'CBR_ADMIN'],
  /** Record or amend an inspection — narrower than `write`, which also covers sites and structures. */
  inspectionWrite: ['CBR_LEVEL_0', 'CBR_LEVEL_1', 'CBR_LEVEL_2', 'CBR_PENG'],
  /** The general create/edit surface. `CBR_GENERAL` is read-only; `CBR_LEVEL_0` writes inspections only. */
  write: ['CBR_LEVEL_1', 'CBR_LEVEL_2', 'CBR_PENG'],
  /** Delete / archive / status override. */
  destructive: ['CBR_LEVEL_2'],
  /** Inspection sign-off. Not `CBR_ADMIN` — see the note above. */
  approve: ['CBR_PENG'],
} as const satisfies Record<string, readonly ROLE_TYPE[]>;

export type ROLE_TYPE = (typeof AVAILABLE_ROLES)[number];

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
