import { describe, expect, it, vi } from 'vitest';

vi.mock('@/env', () => ({ env: { VITE_KEYCLOAK_CLIENT_ID: 'cbr-app' } }));

import { extractRoles, normalizeProvider, parseToken } from './authUtils';

describe('parseToken', () => {
  it('returns undefined when there are no claims', () => {
    expect(parseToken(undefined)).toBeUndefined();
  });

  it('parses an IDIR token: provider, username, and roles from client_roles', () => {
    const user = parseToken({
      identity_provider: 'azureidir',
      idir_username: 'JSMITH',
      display_name: 'Smith, John',
      email: 'john.smith@gov.bc.ca',
      client_roles: ['CBR_LEVEL_1'],
    });

    expect(user?.idpProvider).toBe('IDIR');
    expect(user?.providerUsername).toBe(String.raw`IDIR\JSMITH`);
    expect(user?.firstName).toBe('John');
    expect(user?.lastName).toBe('Smith');
    expect(user?.email).toBe('john.smith@gov.bc.ca');
    expect(user?.roles).toEqual(['CBR_LEVEL_1']);
  });

  it('parses a BCeID Business token, normalizing the username prefix to BCEID', () => {
    // CBR's WebADE profiles were linked to both GOV and BUP (business BCeID) user types, so this
    // path is real rather than inherited: AUTH_PROFILE_USR_LINK carries a BUP row per profile.
    const user = parseToken({
      identity_provider: 'bceidbusiness',
      bceid_username: 'CONTRACTOR1',
      display_name: 'Doe, Jane',
      email: 'jane@example.com',
      client_roles: ['CBR_LEVEL_1'],
    });

    // idpProvider keeps the accurate provider for display...
    expect(user?.idpProvider).toBe('BCEIDBUSINESS');
    // ...but providerUsername mirrors the backend-stored userid (BCEIDBUSINESS -> BCEID).
    expect(user?.providerUsername).toBe(String.raw`BCEID\CONTRACTOR1`);
    expect(user?.roles).toEqual(['CBR_LEVEL_1']);
  });

  it('leaves idpProvider undefined for an unrecognized provider', () => {
    expect(
      parseToken({ identity_provider: 'somethingelse', idir_username: 'X' })?.idpProvider,
    ).toBeUndefined();
  });

  it('parses the five real CBR roles as global (unscoped) privileges', () => {
    // The role set confirmed against the legacy roles.sql / users.sql pairing — one Oracle role per
    // proxy account. A global role's privilege value is null; only a scoped role carries an array.
    const user = parseToken({
      identity_provider: 'azureidir',
      idir_username: 'JSMITH',
      client_roles: ['CBR_ADMIN', 'CBR_GENERAL', 'CBR_LEVEL_1', 'CBR_LEVEL_2', 'CBR_PENG'],
    });

    expect(user?.roles).toEqual([
      'CBR_ADMIN',
      'CBR_GENERAL',
      'CBR_LEVEL_1',
      'CBR_LEVEL_2',
      'CBR_PENG',
    ]);
    expect(user?.privileges.CBR_PENG).toBeNull();
  });

  it('ignores a role that is not in AVAILABLE_ROLES', () => {
    const user = parseToken({
      identity_provider: 'azureidir',
      idir_username: 'JSMITH',
      client_roles: ['CBR_LEVEL_1', 'SOME_OTHER_APP_ROLE'],
    });

    expect(user?.roles).toEqual(['CBR_LEVEL_1']);
  });
});

describe('normalizeProvider — the azureidir trap', () => {
  it('folds every IDIR alias, in any case, onto IDIR', () => {
    // azureidir is what the realm actually reports: the CSS integration selects IDIR - MFA.
    // Mapping it verbatim would build userids matching nothing the backend has stored.
    expect(normalizeProvider('azureidir')).toBe('IDIR');
    expect(normalizeProvider('AzureIDIR')).toBe('IDIR');
    // The non-MFA broker folds identically, so switching the integration changes no stored string.
    expect(normalizeProvider('idir')).toBe('IDIR');
  });

  it('folds the BCeID aliases onto BCEIDBUSINESS', () => {
    expect(normalizeProvider('bceidbusiness')).toBe('BCEIDBUSINESS');
    expect(normalizeProvider('BCEIDBASIC')).toBe('BCEIDBUSINESS');
  });

  it('returns undefined for an empty or unknown provider', () => {
    expect(normalizeProvider(undefined)).toBeUndefined();
    expect(normalizeProvider('')).toBeUndefined();
    expect(normalizeProvider('twitter')).toBeUndefined();
  });
});

describe('username resolution — GUIDs are case-folded, usernames are not', () => {
  it('prefers idir_username and passes it through exactly as issued', () => {
    const user = parseToken({
      identity_provider: 'azureidir',
      idir_username: 'JSmith',
      idir_user_guid: '0a1b2c3d',
      preferred_username: '0a1b2c3d@azureidir',
    });
    expect(user?.providerUsername).toBe(String.raw`IDIR\JSmith`);
  });

  it('upper-cases the GUID whichever claim it falls back to, so one person is one identity', () => {
    const fromPreferred = parseToken({
      identity_provider: 'azureidir',
      preferred_username: '0a1b2c3d4e5f60718293a4b5c6d7e8f9@azureidir',
    });
    const fromGuidClaim = parseToken({
      identity_provider: 'azureidir',
      idir_user_guid: '0A1B2C3D4E5F60718293A4B5C6D7E8F9',
    });

    expect(fromPreferred?.providerUsername).toBe(String.raw`IDIR\0A1B2C3D4E5F60718293A4B5C6D7E8F9`);
    expect(fromGuidClaim?.providerUsername).toBe(fromPreferred?.providerUsername);
  });
});

describe('extractRoles', () => {
  it('reads the flat client_roles claim that CSS emits', () => {
    expect(extractRoles({ client_roles: ['CBR_ADMIN'] })).toEqual(['CBR_ADMIN']);
  });

  it('reads resource_access.<client>.roles, which stock Keycloak uses instead', () => {
    expect(extractRoles({ resource_access: { 'cbr-app': { roles: ['CBR_LEVEL_1'] } } })).toEqual([
      'CBR_LEVEL_1',
    ]);
  });

  it('ignores roles belonging to another client', () => {
    expect(extractRoles({ resource_access: { account: { roles: ['manage-account'] } } })).toEqual(
      [],
    );
  });

  it('merges both locations without duplicating', () => {
    expect(
      extractRoles({
        client_roles: ['CBR_ADMIN'],
        resource_access: { 'cbr-app': { roles: ['CBR_ADMIN', 'CBR_LEVEL_1'] } },
      }),
    ).toEqual(['CBR_ADMIN', 'CBR_LEVEL_1']);
  });

  it("drops FAM's per-grant expiry bookkeeping roles", () => {
    // FAM assigns expiry as a role on the person; it is not a role anyone holds.
    expect(
      extractRoles({ client_roles: ['CBR_PENG', 'FAM:EXPIRES:2026-09-30:CBR_PENG'] }),
    ).toEqual(['CBR_PENG']);
  });

  it('returns an empty array when there are no roles at all', () => {
    expect(extractRoles(undefined)).toEqual([]);
    expect(extractRoles({})).toEqual([]);
  });
});
