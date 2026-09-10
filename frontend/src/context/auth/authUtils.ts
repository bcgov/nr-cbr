import {
  AVAILABLE_ROLES,
  CONTRACT_ENGINEER_PREFIX,
  REGIONAL_ENGINEER_PREFIX,
  type FamLoginUser,
  type IdpProviderType,
  type JwtClaims,
  type ROLE_TYPE,
  type USER_PRIVILEGE_TYPE,
} from './types';

import { env } from '@/env';

// ── Session storage note ─────────────────────────────────────────────

/**
 * No cookies are read anywhere in the auth layer. The tokens live in sessionStorage under
 * `oidc-client-ts`'s own keys and are reached through the `UserManager` (see
 * `services/keycloak.ts`); the SPA sets the `Authorization` header itself.
 *
 * <p>A `getCookie` helper used to sit here for the backend's `XSRF-TOKEN`. The API is a stateless
 * bearer-token resource server with CSRF disabled (see `SecurityConfiguration`), so there is no
 * such cookie and nothing was calling it.
 */

/**
 * Set when the user signs out while offline, so the landing page can say that the sign-out was
 * local only. Mirrors {@code SESSION_EXPIRED_FLAG}: sessionStorage, read-and-cleared once.
 */
export const OFFLINE_SIGNOUT_FLAG = 'cbr.offlineSignOut';

// ── Claim helpers ────────────────────────────────────────────────────

const asString = (claims: JwtClaims | undefined, name: string): string => {
  const value = claims?.[name];
  return typeof value === 'string' ? value : '';
};

/**
 * Folds the realm's provider alias onto the two values the app knows about.
 *
 * <p>**`azureidir` is the one that actually arrives.** The CSS integration selects IDIR - MFA,
 * which federates via Azure AD, so `identity_provider` reads `azureidir` on every login rather than
 * `idir`. Mapping it verbatim would leave `idpProvider` unrecognised — and, worse, would build a
 * `providerUsername` of `AZUREIDIR\jsmith` that matches nothing the backend has ever stored,
 * silently breaking every "is this row mine?" comparison. Mirrors `JwtPrincipalUtil.getProvider` on
 * the backend, which has to produce the same string for the audit columns.
 */
export const normalizeProvider = (raw: string | undefined): IdpProviderType | undefined => {
  const alias = raw?.trim().toLowerCase();
  if (!alias) return undefined;
  if (alias === 'idir' || alias === 'azureidir' || alias === 'azure-idir') return 'IDIR';
  if (alias.startsWith('bceid')) return 'BCEIDBUSINESS';
  return undefined;
};

/**
 * Parses the BC Gov SSO access-token claims into the app's FamLoginUser shape.
 *
 * <p>The claims ride the **access token** now — Keycloak puts `idir_username`, `idir_user_guid`,
 * `identity_provider` and `display_name` there, so there is no ID-token-only step and no per-request
 * userinfo call. (If `idir_username` is missing, the integration's mappers were added to the ID
 * token only: a CSS console setting, not a code problem. The GUID fallback below keeps the app
 * usable but produces ids that will not match stored rows.)
 */
export const parseToken = (claims: JwtClaims | undefined): FamLoginUser | undefined => {
  if (!claims) return undefined;

  const idpProvider = normalizeProvider(asString(claims, 'identity_provider'));
  const displayName = asString(claims, 'display_name') || asString(claims, 'name') || '';

  const hasComma = displayName.includes(',');
  let [lastName, firstName] = hasComma ? displayName.split(', ') : displayName.split(' ');
  if (!hasComma) [lastName, firstName] = [firstName, lastName];
  const sanitizedFirstName = hasComma ? firstName?.split(' ')[0]?.trim() : firstName || '';

  const userName = resolveUsername(claims);
  const email = asString(claims, 'email');
  const privileges = parsePrivileges(extractRoles(claims));
  const derivedRoles = Object.keys(privileges) as ROLE_TYPE[];

  // The backend (JwtPrincipalUtil) stores userids with the legacy WebADE source-directory prefix,
  // normalizing the realm's "BCEIDBUSINESS" to "BCEID". Mirror that here so providerUsername matches
  // the backend-stored userid — CBR compares it against STRUCTURE_INSPECTION_REVIEWER.USERID and the
  // create_user/update_user audit columns. idpProvider keeps the accurate provider name for display.
  const userIdPrefix = idpProvider === 'BCEIDBUSINESS' ? 'BCEID' : idpProvider;
  return {
    userName,
    displayName,
    email,
    idpProvider,
    privileges,
    roles: derivedRoles,
    firstName: sanitizedFirstName,
    lastName,
    providerUsername: `${userIdPrefix}\\${userName}`,
  };
};

/**
 * The username, in the same resolution order the backend uses.
 *
 * <p>`idir_username` / `bceid_username` first, used exactly as issued — a username is a name.
 * Everything after that is a GUID, and **GUIDs are upper-cased**: `idir_user_guid` and the GUID
 * inside `preferred_username` (`<guid>@azureidir`) can arrive in different cases in the same token,
 * and a hex spelling carries no meaning. Leaving the case alone would let `IDIR\0a1b…` and
 * `IDIR\0A1B…` stand for one person in two places.
 */
const resolveUsername = (claims: JwtClaims): string => {
  const named = asString(claims, 'idir_username') || asString(claims, 'bceid_username');
  if (named) return named;

  const preferred = asString(claims, 'preferred_username');
  const at = preferred.indexOf('@');
  const guid =
    at > 0
      ? preferred.slice(0, at)
      : asString(claims, 'idir_user_guid') || asString(claims, 'bceid_user_guid');
  return guid.toUpperCase();
};

/**
 * Parses role strings into a user privilege object.
 *
 * - Global roles that exactly match {@link AVAILABLE_ROLES} (e.g. "CBR_ADMIN", "CBR_LEVEL_1") map to
 *   a `null` value (null = global, non-scoped role).
 * - Any other role is ignored.
 *
 * <p>**The two region-scoped branches are dead code**, retained only until the D3 cleanup removes
 * their call sites. Legacy CBR has no region scoping — `CBR_REGIONAL_ENGINEER` and
 * `CBR_CONTRACT_REGIONAL_ENGINEER` are PL/SQL package names, not roles — so no token will carry a
 * role with either prefix. See `DEPRECATED_ROLES` in ./types.
 *
 * @param {string[]} input - Array of role strings from the access token.
 * @returns {USER_PRIVILEGE_TYPE} The parsed privilege object.
 */
export function parsePrivileges(input: string[]): USER_PRIVILEGE_TYPE {
  const result: USER_PRIVILEGE_TYPE = {};
  const regionalCodes: string[] = [];
  const contractCodes: string[] = [];
  for (const item of input) {
    // CONTRACT first: CBR_CONTRACT_REGIONAL_ENGINEER_ and CBR_REGIONAL_ENGINEER_ are distinct
    // strings, but testing the narrower role first keeps the ordering safe if either is ever
    // renamed such that one becomes a prefix of the other.
    if (item.startsWith(CONTRACT_ENGINEER_PREFIX)) {
      const code = item.slice(CONTRACT_ENGINEER_PREFIX.length).trim().toUpperCase();
      if (code) contractCodes.push(code);
    } else if (item.startsWith(REGIONAL_ENGINEER_PREFIX)) {
      const code = item.slice(REGIONAL_ENGINEER_PREFIX.length).trim().toUpperCase();
      if (code) regionalCodes.push(code);
    } else if (AVAILABLE_ROLES.includes(item as ROLE_TYPE)) {
      // Direct match against a known global role name.
      result[item as ROLE_TYPE] = null; // null = global (non-scoped) role
    }
  }
  const dedupeSorted = (codes: string[]) =>
    [...new Set(codes)].sort((a, b) => a.localeCompare(b));
  if (regionalCodes.length > 0) result.CBR_REGIONAL_ENGINEER = dedupeSorted(regionalCodes);
  if (contractCodes.length > 0) {
    result.CBR_CONTRACT_REGIONAL_ENGINEER = dedupeSorted(contractCodes);
  }
  return result;
}

/**
 * FAM records per-grant expiry as a role on the person, shaped `FAM:EXPIRES:2026-09-30:CBR_PENG`.
 * It is not a role anyone holds — it is bookkeeping riding along beside the real ones — so it is
 * filtered on both sides of the wire (the backend drops the same prefix).
 */
const FAM_BOOKKEEPING_PREFIX = 'FAM:';

/**
 * Extracts the caller's roles from the decoded access-token claims.
 *
 * <p>**Both locations are read on purpose.** CSS's mappers emit a flat `client_roles` array; stock
 * Keycloak nests the same values under `resource_access.<client>.roles`. Which one is populated
 * depends on the realm's mappers, and getting the wrong one produces a user with no roles — which
 * looks exactly like revoked access rather than like a configuration problem.
 */
export function extractRoles(claims: JwtClaims | undefined): string[] {
  if (!claims) return [];

  const direct = Array.isArray(claims.client_roles) ? (claims.client_roles as unknown[]) : [];

  // Scoped to our own client: the standard realm hands every token an `account` entry too, and
  // roles from another integration are not ours to honour.
  const resourceAccess = claims.resource_access;
  const forThisClient =
    resourceAccess && typeof resourceAccess === 'object'
      ? (resourceAccess as Record<string, unknown>)[(env.VITE_KEYCLOAK_CLIENT_ID ?? '').trim()]
      : undefined;
  const nested: unknown[] =
    forThisClient &&
    typeof forThisClient === 'object' &&
    Array.isArray((forThisClient as { roles?: unknown }).roles)
      ? ((forThisClient as { roles: unknown[] }).roles as unknown[])
      : [];

  return [...new Set([...direct, ...nested])]
    .filter((role): role is string => typeof role === 'string' && role.trim().length > 0)
    .map((role) => role.trim())
    .filter((role) => !role.toUpperCase().startsWith(FAM_BOOKKEEPING_PREFIX));
}
