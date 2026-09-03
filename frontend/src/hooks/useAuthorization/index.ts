import { useMemo } from 'react';

import type { FamLoginUser, ROLE_TYPE } from '@/context/auth/types';

import { useAuth } from '@/context/auth/useAuth';

/**
 * Authorization result returned by {@link useAuthorization}.
 *
 * Deliberately mirrors the backend `LoggedUserHelper` method-for-method — when one side changes,
 * change the other. The backend is the enforcement point; this hook exists so the UI does not offer
 * actions that would come back 403.
 */
export type AuthorizationInfo = {
  /** `true` when the user holds the `CBR_ADMIN` Cognito group. */
  isSysAdmin: boolean;
  /** `true` when the user holds the `CBR_GENERAL` group. */
  isGeneral: boolean;
  /** `true` when the user holds the `CBR_ENGINEER` group. */
  isEngineer: boolean;
  /** `true` when the user may review and seal inspection reports (`CBR_PENG`, or sys-admin). */
  isPeng: boolean;
  /** `true` when the user has at least one recognized CBR role, global or region-scoped. */
  hasAnyRole: boolean;
  /** `true` when the user can perform general write operations. */
  canEdit: boolean;
  /** `true` when the user can create new resources. Alias for {@link canEdit}. */
  canCreate: boolean;
  /** Org-unit codes the user is a regional engineer for (from `CBR_REGIONAL_ENGINEER_*`). */
  regions: string[];
  /** Org-unit codes the user is a *contract* regional engineer for. */
  contractRegions: string[];
  /** `true` when the user holds a regional-engineer role for any region (or is sys-admin). */
  canAnyRegion: boolean;
  /**
   * `true` when the user may perform regional-engineer operations — the destructive set: delete
   * site/structure/inspection/repair/monitor/attachment, and archive a structure — for the given
   * org-unit code. Sys-admins pass for every region.
   */
  canRegion: (orgUnitCode: string | undefined | null) => boolean;
  /**
   * `true` when the user may update a site in the given region: a regional engineer, or a contract
   * regional engineer, whose single legacy privilege was exactly `UPDATE_SITE`.
   */
  canUpdateSite: (orgUnitCode: string | undefined | null) => boolean;
  /**
   * `true` when the user may delete. Region-scoped: prefer {@link canRegion} with the record's org
   * unit wherever one is available — this is the coarse, id-less gate only.
   */
  canDelete: boolean;
  /** Checks if the user holds a specific role. */
  hasRole: (role: ROLE_TYPE) => boolean;
  /** The full user object for advanced checks (may be `undefined` before login). */
  user: FamLoginUser | undefined;
};

/**
 * Hook providing role-based authorization helpers derived from the authenticated user's Cognito
 * (FAM) groups.
 *
 * @example
 * ```tsx
 * const { isSysAdmin, canEdit, canRegion } = useAuthorization();
 *
 * return (
 *   <>
 *     {canEdit && <Button>Edit</Button>}
 *     {canRegion(site.orgUnitCode) && <Button kind="danger">Delete</Button>}
 *     {isSysAdmin && <Link to="/admin">Admin</Link>}
 *   </>
 * );
 * ```
 */
export const useAuthorization = (): AuthorizationInfo => {
  const { user } = useAuth();

  return useMemo<AuthorizationInfo>(() => {
    const roles = user?.roles ?? [];
    const isSysAdmin = roles.includes('CBR_ADMIN');
    const isGeneral = roles.includes('CBR_GENERAL');
    const isEngineer = roles.includes('CBR_ENGINEER');
    const isPeng = isSysAdmin || roles.includes('CBR_PENG');

    // Region-scoped roles: the privilege value carries the org-unit codes.
    const regions = user?.privileges?.CBR_REGIONAL_ENGINEER ?? [];
    const contractRegions = user?.privileges?.CBR_CONTRACT_REGIONAL_ENGINEER ?? [];

    const canAnyRegion = isSysAdmin || regions.length > 0;

    const canRegion = (orgUnitCode: string | undefined | null) =>
      isSysAdmin || (!!orgUnitCode && regions.includes(orgUnitCode.toUpperCase()));

    const canUpdateSite = (orgUnitCode: string | undefined | null) =>
      canRegion(orgUnitCode) ||
      (!!orgUnitCode && contractRegions.includes(orgUnitCode.toUpperCase()));

    const canEdit = isSysAdmin || isGeneral || isEngineer || isPeng;

    // A region-only user holds no global role, so include the scoped roles here — otherwise they
    // would look role-less and be routed to the role-error page. Same reasoning as nr-frep's
    // per-district CHR editors.
    const hasAnyRole = canEdit || canAnyRegion || contractRegions.length > 0;

    const hasRole = (role: ROLE_TYPE) => roles.includes(role);

    return {
      isSysAdmin,
      isGeneral,
      isEngineer,
      isPeng,
      hasAnyRole,
      canEdit,
      canCreate: canEdit,
      regions,
      contractRegions,
      canAnyRegion,
      canRegion,
      canUpdateSite,
      canDelete: canAnyRegion,
      hasRole,
      user,
    };
  }, [user]);
};
