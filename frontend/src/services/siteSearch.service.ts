import type { SiteCreateRequest } from '@/components/SiteForm/request';
import type { CancelablePromise } from '@/config/api/CancelablePromise';
import type { SiteDetailResponse } from '@/pages/SiteDetail/siteResponse';
import type { PagedResponse, SiteSearchCriteria, SiteSearchResult } from '@/pages/SiteSearch/types';

import { HttpClient, type APIConfig } from '@/config/api/types';

/**
 * Site Search, backed by `/api/v1/sites/search`.
 */
export class SiteSearchService extends HttpClient {
  constructor(readonly config: APIConfig) {
    super(config);
  }

  /**
   * One page of matching sites.
   *
   * <p>`pageNumber` is zero-based, matching the backend. Blank criteria are dropped rather than sent
   * as empty parameters: the server treats a blank as unset either way, but a URL carrying all
   * seventeen is unreadable in a log or a network tab, which is where this gets debugged.
   *
   * <p>The two booleans are sent only when true. They are one-way filters — "Capital Road" on means
   * capital roads only, off means no filter rather than non-capital roads only — so `false` and
   * absent mean the same thing, and sending `false` would suggest otherwise.
   */
  searchSites(
    criteria: SiteSearchCriteria,
    pageNumber: number,
    pageSize: number,
  ): CancelablePromise<PagedResponse<SiteSearchResult>> {
    return this.doRequest<PagedResponse<SiteSearchResult>>(this.config, {
      method: 'GET',
      url: '/v1/sites/search',
      query: { ...populated(criteria), pageNumber, pageSize },
    });
  }

  /**
   * One site, for the detail screen.
   *
   * <p>Answers with the stored values — codes as codes, org units as numbers — plus the two things
   * the screen could not resolve on its own: the road's name and the maintainer. 404 when the site
   * is gone, which is ordinary: the link here comes from a results table that may have been on
   * screen for some time.
   */
  getSite(siteId: string): CancelablePromise<SiteDetailResponse> {
    return this.doRequest<SiteDetailResponse>(this.config, {
      method: 'GET',
      url: '/v1/sites/{siteId}',
      path: { siteId },
    });
  }

  /**
   * Creates a site.
   *
   * <p>201 with the stored site, whose number comes back upper-cased and whose road name and
   * maintainer label are filled in — so the caller should navigate to what came back rather than
   * to what it sent.
   *
   * <p><b>400 carries a message per field.</b> The problem detail has a `fieldErrors` property
   * keyed by the same names this request uses, which are the form's names too, so the screen can
   * mark the boxes instead of printing a paragraph. See `siteFieldErrors`.
   */
  createSite(site: SiteCreateRequest): CancelablePromise<SiteDetailResponse> {
    return this.doRequest<SiteDetailResponse>(this.config, {
      method: 'POST',
      url: '/v1/sites',
      body: site,
    });
  }

  /**
   * Deletes a site.
   *
   * <p>204 on success. 404 if it is already gone, 409 if a structure or inspection still references
   * it — the body carries a sentence naming what, which the caller should show rather than replace
   * with wording of its own.
   *
   * <p><b>Irreversible.</b> The backend issues a hard delete and CBR keeps no history row for a
   * site, so nothing can undo this.
   */
  deleteSite(siteId: string): CancelablePromise<void> {
    return this.doRequest<void>(this.config, {
      method: 'DELETE',
      url: '/v1/sites/{siteId}',
      path: { siteId },
    });
  }
}

/**
 * The form fields that exist for the screen rather than for the server.
 *
 * <p>`maintainerLabel` is what the Designated Maintainer combo box displays; the criteria it stands
 * for are `clientNumber` and `clientLocationCode`, which travel on their own. Sending it would put
 * a client's name in every search URL to no effect.
 */
const DISPLAY_ONLY: ReadonlySet<string> = new Set(['maintainerLabel']);

/** Drops blank strings, false booleans and display-only fields, leaving the criteria that matter. */
const populated = (criteria: SiteSearchCriteria): Record<string, string | boolean> => {
  const query: Record<string, string | boolean> = {};
  for (const [field, value] of Object.entries(criteria)) {
    if (DISPLAY_ONLY.has(field)) {
      continue;
    }
    if (typeof value === 'string' && value.trim() !== '') {
      query[field] = value.trim();
    } else if (value === true) {
      query[field] = true;
    }
  }
  return query;
};
