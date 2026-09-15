import { renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { useAuthorization } from './index';

import type { FamLoginUser, ROLE_TYPE } from '@/context/auth/types';

vi.mock('@/context/auth/useAuth', () => ({
  useAuth: vi.fn(),
}));

import { useAuth } from '@/context/auth/useAuth';

const mockUseAuth = vi.mocked(useAuth);

function withRoles(roles: ROLE_TYPE[]) {
  mockUseAuth.mockReturnValue({
    user: { roles, privileges: {} } as FamLoginUser,
    isLoggedIn: true,
    isLoading: false,
    login: vi.fn(),
    logout: vi.fn(),
    completeSignIn: vi.fn(),
    ensureFreshToken: vi.fn(),
    forceRefreshSession: vi.fn(),
  });
}

/**
 * Mirrors `CbrAuthoritiesTest` on the backend. The two sides must agree: this hook only decides
 * what the UI offers, and offering an action the API will refuse is the failure it exists to
 * prevent. The matrix is in cbr-auth-and-roles.local.md §3.2.
 */
describe('useAuthorization — the capability ladder', () => {
  it('CBR_GENERAL reads and nothing else', () => {
    withRoles(['CBR_GENERAL']);

    expect(renderHook(() => useAuthorization()).result.current).toMatchObject({
      isGeneral: true,
      canRead: true,
      canWriteInspection: false,
      canEdit: false,
      canCreate: false,
      canDelete: false,
      isPeng: false,
      isSysAdmin: false,
      hasAnyRole: true,
    });
  });

  it('CBR_LEVEL_0 adds inspection capture but not the edit surface', () => {
    withRoles(['CBR_LEVEL_0']);

    expect(renderHook(() => useAuthorization()).result.current).toMatchObject({
      canRead: true,
      canWriteInspection: true,
      canEdit: false,
      canDelete: false,
    });
  });

  it('CBR_LEVEL_1 adds create/edit but deletes nothing — the Level 1 / Level 2 line', () => {
    withRoles(['CBR_LEVEL_1']);

    expect(renderHook(() => useAuthorization()).result.current).toMatchObject({
      canRead: true,
      canWriteInspection: true,
      canEdit: true,
      canCreate: true,
      canDelete: false,
    });
  });

  it('CBR_LEVEL_2 adds the destructive set', () => {
    withRoles(['CBR_LEVEL_2']);

    expect(renderHook(() => useAuthorization()).result.current).toMatchObject({
      canEdit: true,
      canDelete: true,
      isPeng: false,
    });
  });

  it('CBR_PENG carries everything below it, plus sign-off', () => {
    withRoles(['CBR_PENG']);

    expect(renderHook(() => useAuthorization()).result.current).toMatchObject({
      canRead: true,
      canWriteInspection: true,
      canEdit: true,
      canDelete: true,
      isPeng: true,
    });
  });
});

describe('useAuthorization — CBR_ADMIN is off the ladder', () => {
  it('reads, but writes nothing at any level', () => {
    // Legacy gave ADMINISTRATOR three privileges and no read at all; the rebuild grants read
    // (decided 2026-09-15) and nothing more. "Admin can do everything" is false here.
    withRoles(['CBR_ADMIN']);

    expect(renderHook(() => useAuthorization()).result.current).toMatchObject({
      isSysAdmin: true,
      canRead: true,
      hasAnyRole: true,
      canWriteInspection: false,
      canEdit: false,
      canCreate: false,
      canDelete: false,
    });
  });

  it('does NOT imply P.Eng — sign-off is tied to a named engineer', () => {
    withRoles(['CBR_ADMIN']);

    expect(renderHook(() => useAuthorization()).result.current.isPeng).toBe(false);
  });
});

describe('useAuthorization — edge cases', () => {
  it('a user with no roles can do nothing and is routed to the role-error page', () => {
    withRoles([]);

    expect(renderHook(() => useAuthorization()).result.current).toMatchObject({
      hasAnyRole: false,
      canRead: false,
      canEdit: false,
      canDelete: false,
      isSysAdmin: false,
      isPeng: false,
    });
  });

  it('an unrecognised role grants nothing', () => {
    withRoles(['NOT_A_CBR_ROLE' as ROLE_TYPE]);

    expect(renderHook(() => useAuthorization()).result.current.hasAnyRole).toBe(false);
  });

  it('holding several roles takes the widest capability, never the narrowest', () => {
    withRoles(['CBR_GENERAL', 'CBR_LEVEL_2']);

    expect(renderHook(() => useAuthorization()).result.current).toMatchObject({
      canEdit: true,
      canDelete: true,
    });
  });

  it('hasRole reports exact membership', () => {
    withRoles(['CBR_LEVEL_1']);
    const { result } = renderHook(() => useAuthorization());

    expect(result.current.hasRole('CBR_LEVEL_1')).toBe(true);
    expect(result.current.hasRole('CBR_LEVEL_2')).toBe(false);
  });

  it('a missing user is treated as no roles rather than throwing', () => {
    mockUseAuth.mockReturnValue({
      user: undefined,
      isLoggedIn: false,
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn(),
      completeSignIn: vi.fn(),
      ensureFreshToken: vi.fn(),
      forceRefreshSession: vi.fn(),
    });

    expect(renderHook(() => useAuthorization()).result.current).toMatchObject({
      hasAnyRole: false,
      canRead: false,
      user: undefined,
    });
  });
});
