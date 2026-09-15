import { useMemo } from 'react';

import type { FamLoginUser, ROLE_TYPE } from '@/context/auth/types';

import { ROLE_CAPABILITIES } from '@/context/auth/types';
import { useAuth } from '@/context/auth/useAuth';

/**
 * Authorization result returned by {@link useAuthorization}.
 *
 * Deliberately mirrors the backend `LoggedUserHelper` method-for-method — when one side changes,
 * change the other. The backend is the enforcement point; this hook exists so the UI does not offer
 * actions that would come back 403.
 */
export type AuthorizationInfo = {
  /** `true` when the user holds the `CBR_ADMIN` role. Administration screens only — plus read. */
  isSysAdmin: boolean;
  /** `true` when the user holds `CBR_GENERAL`, the read-only role. */
  isGeneral: boolean;
  /** `true` when the user may review and seal inspection reports (`CBR_PENG`). Not implied by admin. */
  isPeng: boolean;
  /** `true` when the user holds any recognized CBR role. */
  hasAnyRole: boolean;
  /** `true` when the user may search, view and run reports. */
  canRead: boolean;
  /** `true` when the user may record or amend an inspection (`CBR_LEVEL_0` and above). */
  canWriteInspection: boolean;
  /** `true` when the user may create or edit sites and structures (`CBR_LEVEL_1` and above). */
  canEdit: boolean;
  /** `true` when the user can create new resources. Alias for {@link canEdit}. */
  canCreate: boolean;
  /**
   * `true` when the user may delete, archive, add a site or override inspection status —
   * `CBR_LEVEL_2` and above.
   */
  canDelete: boolean;
  /** Checks if the user holds a specific role. */
  hasRole: (role: ROLE_TYPE) => boolean;
  /** The full user object for advanced checks (may be `undefined` before login). */
  user: FamLoginUser | undefined;
};

/**
 * Hook providing role-based authorization helpers derived from the roles on the access token.
 *
 * <p>Capabilities come from `ROLE_CAPABILITIES`, which mirrors the backend `CbrAuthorities` matrix.
 * There is no region or client scoping anywhere in CBR — every role is flat and province-wide.
 *
 * @example
 * ```tsx
 * const { isSysAdmin, canEdit, canDelete } = useAuthorization();
 *
 * return (
 *   <>
 *     {canEdit && <Button>Edit</Button>}
 *     {canDelete && <Button kind="danger">Delete</Button>}
 *     {isSysAdmin && <Link to="/admin">Admin</Link>}
 *   </>
 * );
 * ```
 */
export const useAuthorization = (): AuthorizationInfo => {
  const { user } = useAuth();

  return useMemo<AuthorizationInfo>(() => {
    const roles = user?.roles ?? [];
    const holds = (allowed: readonly ROLE_TYPE[]) => allowed.some((role) => roles.includes(role));

    const isSysAdmin = roles.includes('CBR_ADMIN');
    const isGeneral = roles.includes('CBR_GENERAL');
    // Not `isSysAdmin || …`: /approveInspection belongs to CBR_PENG alone. Sealing an inspection is
    // a professional engineering act tied to a named P.Eng, not an administrative one.
    const isPeng = roles.includes('CBR_PENG');

    const canRead = holds(ROLE_CAPABILITIES.read);
    const canWriteInspection = holds(ROLE_CAPABILITIES.inspectionWrite);
    const canEdit = holds(ROLE_CAPABILITIES.write);
    const canDelete = holds(ROLE_CAPABILITIES.destructive);

    // Read is the widest capability and CBR_ADMIN is the only role outside the ladder, so anyone
    // with a recognised role satisfies it — which makes this the role-error-page gate.
    const hasAnyRole = canRead;

    const hasRole = (role: ROLE_TYPE) => roles.includes(role);

    return {
      isSysAdmin,
      isGeneral,
      isPeng,
      hasAnyRole,
      canRead,
      canWriteInspection,
      canEdit,
      canCreate: canEdit,
      canDelete,
      hasRole,
      user,
    };
  }, [user]);
};
