import type { CancelablePromise } from '@/config/api/CancelablePromise';
import type { CodeOption, OrgUnitOption } from '@/types/configuration';

import { HttpClient, type APIConfig } from '@/config/api/types';

/**
 * Reference-data client for the dropdown lookups, backed by `/api/v1/configuration/*`.
 *
 * <p>Each list is its own request rather than one bundle: they are cached independently, and only
 * management areas takes a parameter — bundling would re-fetch the other nine every time a district
 * changed.
 */
export class ConfigurationService extends HttpClient {
  constructor(readonly config: APIConfig) {
    super(config);
  }

  /**
   * Site statuses, in the order the dropdown should show them.
   *
   * <p>The order is `CROSSING_SITE_STATUS_XREF.DISPLAY_ORDER` and is deliberately not alphabetical,
   * so callers must not sort the result.
   */
  getSiteStatusCodes(): CancelablePromise<CodeOption[]> {
    return this.doRequest<CodeOption[]>(this.config, {
      method: 'GET',
      url: '/v1/configuration/site-status-codes',
    });
  }

  /** Structure inspection statuses, by description. */
  getStructureInspectionStatusCodes(): CancelablePromise<CodeOption[]> {
    return this.doRequest<CodeOption[]>(this.config, {
      method: 'GET',
      url: '/v1/configuration/structure-inspection-status-codes',
    });
  }

  /** Special access requirements, by description. */
  getSpecialAccessCodes(): CancelablePromise<CodeOption[]> {
    return this.doRequest<CodeOption[]>(this.config, {
      method: 'GET',
      url: '/v1/configuration/special-access-codes',
    });
  }

  /** Site types, by description. */
  getSiteTypeCodes(): CancelablePromise<CodeOption[]> {
    return this.doRequest<CodeOption[]>(this.config, {
      method: 'GET',
      url: '/v1/configuration/site-type-codes',
    });
  }

  /**
   * Structure types and classes, in the order the dropdown should show them.
   *
   * <p>The order is `STRUCTURE_TYPE_CLASS_XREF.DISPLAY_ORDER` and is deliberately not alphabetical,
   * so callers must not sort the result.
   *
   * <p>Retired codes are left out, which the other code lists on the same form do not do — that is
   * legacy's behaviour, not a rule to generalise. See the backend repository.
   */
  getStructureTypeClassCodes(): CancelablePromise<CodeOption[]> {
    return this.doRequest<CodeOption[]>(this.config, {
      method: 'GET',
      url: '/v1/configuration/structure-type-class-codes',
    });
  }

  /** Inspection types, by description. Retired codes are included. */
  getInspectionTypeCodes(): CancelablePromise<CodeOption[]> {
    return this.doRequest<CodeOption[]>(this.config, {
      method: 'GET',
      url: '/v1/configuration/inspection-type-codes',
    });
  }

  /**
   * Inspection report statuses, by description.
   *
   * <p>Includes `OFL` and the expired `ACC`, both of which a form that *sets* a status must not
   * offer. This list is for searching.
   */
  getInspectionReportStatusCodes(): CancelablePromise<CodeOption[]> {
    return this.doRequest<CodeOption[]>(this.config, {
      method: 'GET',
      url: '/v1/configuration/inspection-report-status-codes',
    });
  }

  /**
   * The BCTS business areas, by name.
   *
   * <p>Unlike management areas this takes no parameter: a business area is BC Timber Sales' own
   * geography rather than a subdivision of a forest district, and a site can carry one of each.
   */
  getBusinessAreas(): CancelablePromise<OrgUnitOption[]> {
    return this.doRequest<OrgUnitOption[]>(this.config, {
      method: 'GET',
      url: '/v1/configuration/business-areas',
    });
  }

  /** The current forest districts, by name. */
  getForestDistricts(): CancelablePromise<OrgUnitOption[]> {
    return this.doRequest<OrgUnitOption[]>(this.config, {
      method: 'GET',
      url: '/v1/configuration/forest-districts',
    });
  }

  /**
   * The management areas within one forest district, by name.
   *
   * <p>A CBR management area is a *former* district that now rolls up into the selected one, so this
   * list is meaningless without a district and the parameter is required. Asking for a district with
   * no former districts is a normal, empty answer.
   */
  getManagementAreas(forestDistrictOrgUnitNo: string): CancelablePromise<OrgUnitOption[]> {
    return this.doRequest<OrgUnitOption[]>(this.config, {
      method: 'GET',
      url: '/v1/configuration/management-areas',
      query: { forestDistrictOrgUnitNo },
    });
  }
}
