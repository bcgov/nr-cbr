import { Column, Grid, InlineNotification } from '@carbon/react';
import { useCallback, useMemo, useState } from 'react';

import DestructiveModal from '@/components/core/DestructiveModal';
import PageTitle from '@/components/core/PageTitle';

import SiteSearchCriteriaForm, { type CodeTables } from './SiteSearchCriteria';
import SiteSearchResults from './SiteSearchResults';
import { EMPTY_CRITERIA, type SiteSearchCriteria, type SiteSearchResult } from './types';

import './siteSearch.scss';

import type { ApiError } from '@/config/api/types';
import type { FC } from 'react';

import { useAuthorization } from '@/hooks/useAuthorization';
import {
  useForestDistricts,
  useManagementAreas,
  useReferenceDataState,
  useSiteStatusCodes,
  useSiteTypeCodes,
  useSpecialAccessCodes,
  useStructureInspectionStatusCodes,
} from '@/hooks/useConfiguration';
import { useDeleteSite, useSiteSearch } from '@/hooks/useSiteSearch';

/**
 * Site Search — the first screen of the Inventory section.
 *
 * <p>Legacy equivalent: `showSiteSearch.do` → `SiteSearchAction` → `site_search.jsp`, gated on the
 * `/showSiteSearch` privilege, which every role that can read holds.
 *
 * <p>The search runs against `/api/v1/sites/search`, which is a JPA Specification rather than the
 * legacy `CBR.FIND_SITES_BY_CRITERIA` — see `SiteSearchSpecifications` for the two places the
 * results deliberately differ from legacy. Paging and ordering are the server's.
 *
 * <p>Deleting a site is a hard delete and cannot be undone — see `SiteService.delete`. It is
 * reachable only to a role holding the destructive capability, and only behind a confirmation.
 */
/**
 * The server's explanation for a failed delete, or a fallback.
 *
 * <p>A 409 body is an RFC 7807 problem detail whose `detail` says what still references the site.
 * That sentence is the whole value of the response — it is the only place the user learns that an
 * archived structure or a close-proximity inspection is in the way, neither of which the results
 * table shows.
 */
const messageFor = (error: unknown, siteId: string): string => {
  const body = (error as ApiError | undefined)?.body;
  const detail =
    body !== null && typeof body === 'object' && 'detail' in body ? body.detail : undefined;

  return typeof detail === 'string' && detail.trim() !== ''
    ? detail
    : `Site ${siteId} could not be deleted. Try again, or contact support if this continues.`;
};

const SiteSearchPage: FC = () => {
  const { canDelete } = useAuthorization();

  const [criteria, setCriteria] = useState<SiteSearchCriteria>(EMPTY_CRITERIA);
  /**
   * The criteria the current results belong to — a snapshot taken when Search was pressed.
   *
   * <p>Kept apart from the live form state on purpose. The query is keyed on this, so binding it to
   * the form would run a search on every keystroke; and editing a field after a search would
   * silently change what the visible results claim to be. `null` means no search has been run,
   * which is what keeps the results table off the screen entirely.
   */
  const [submitted, setSubmitted] = useState<SiteSearchCriteria | null>(null);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [pendingDelete, setPendingDelete] = useState<SiteSearchResult | null>(null);
  /**
   * Why the last delete was refused, if it was.
   *
   * <p>Held on the page rather than read from the mutation, because the modal closes on failure and
   * the mutation's error would go with it. The server's sentence is shown verbatim: a 409 names
   * what still references the site, and archived structures and close-proximity inspections are
   * both invisible from this table.
   */
  const [deleteError, setDeleteError] = useState<string | null>(null);

  // Carbon's Pagination is one-based; the backend, like Spring Data, is zero-based.
  const results = useSiteSearch(submitted, page - 1, pageSize);
  const deleteSite = useDeleteSite();

  const siteStatusCodes = useSiteStatusCodes();
  const structureInspectionStatusCodes = useStructureInspectionStatusCodes();
  const specialAccessCodes = useSpecialAccessCodes();
  const siteTypeCodes = useSiteTypeCodes();
  const forestDistricts = useForestDistricts();
  // Management areas are the former districts inside the selected one, so this refetches — under
  // its own cache key — whenever Forest District changes, and does not run at all until one is
  // picked. Legacy does the same by posting the form back on change.
  const managementAreas = useManagementAreas(criteria.orgUnit);

  const referenceData = useReferenceDataState();

  /**
   * A failed lookup falls back to an empty list rather than blocking the form: every criterion is
   * optional, so a search still runs without a Status or Site Type filter. The failure is surfaced
   * below instead — an empty dropdown with no explanation reads as "there are none".
   */
  const codeTables = useMemo<CodeTables>(
    () => ({
      siteStatusCodes: siteStatusCodes.data ?? [],
      structureInspectionStatusCodes: structureInspectionStatusCodes.data ?? [],
      specialAccessCodes: specialAccessCodes.data ?? [],
      siteTypeCodes: siteTypeCodes.data ?? [],
      forestDistricts: forestDistricts.data ?? [],
      managementAreas: managementAreas.data ?? [],
    }),
    [
      siteStatusCodes.data,
      structureInspectionStatusCodes.data,
      specialAccessCodes.data,
      siteTypeCodes.data,
      forestDistricts.data,
      managementAreas.data,
    ],
  );

  const updateCriteria = useCallback(
    <K extends keyof SiteSearchCriteria>(field: K, value: SiteSearchCriteria[K]) => {
      setCriteria((current) => ({
        ...current,
        [field]: value,
        // Changing the district changes which management areas exist, so a selection made under
        // the old one has to go. Leaving it would submit a management area that is not in the list
        // the user can now see — a filter they cannot tell is applied.
        ...(field === 'orgUnit' ? { managementOrgUnit: '' } : {}),
      }));
    },
    [],
  );

  const search = useCallback(() => {
    // Back to page one: the previous page number belongs to the previous result set, and page 4 of
    // a search that now matches twelve sites is an empty table.
    setPage(1);
    setSubmitted(criteria);
  }, [criteria]);

  const reset = useCallback(() => {
    setCriteria(EMPTY_CRITERIA);
    setSubmitted(null);
    setPage(1);
  }, []);

  return (
    // PageTitle renders its own <Column>, so the page owns the <Grid>. Everything else sits in one
    // full-width column and lays itself out with CSS grid — the nr-frep pattern.
    <Grid fullWidth className="default-grid site-search-grid">
      <PageTitle
        title="Site Search"
        subtitle="Find a crossing site by location, tenure, maintainer or status."
        breadCrumbs={[{ name: 'Inventory', path: '/inventory' }]}
      />

      {/* The Column itself is conditional, not just its contents. Rendered around `null` it is
          still a grid item, so the page paid `.default-grid`'s row-gap twice — once above the empty
          row and once below — and the form sat an extra 2.5rem below the page title for a notice
          that was not there.

          This is what is left of the "not connected yet" notice that lived here while the search
          was a stub. That one was true then and is not now; leaving it would have told users their
          real results were fake. */}
      {referenceData.isError && (
        <Column sm={4} md={8} lg={16}>
          <InlineNotification
            kind="error"
            lowContrast
            hideCloseButton
            title="Some filters could not be loaded"
            subtitle={
              'One or more of the dropdown lists is unavailable, so those filters are empty. ' +
              'Every other criterion still works.'
            }
            data-testid="site-search-codes-error"
          />
        </Column>
      )}

      <Column sm={4} md={8} lg={16}>
        <SiteSearchCriteriaForm
          criteria={criteria}
          codeTables={codeTables}
          codeTablesLoading={referenceData.isLoading}
          managementAreasLoading={managementAreas.isFetching}
          onChange={updateCriteria}
          onSearch={search}
          onReset={reset}
        />
      </Column>

      {/* Legacy renders the results block only after a search (`<c:if test="${search}">`), so an
          untouched page is the form alone rather than an empty table implying zero matches. */}
      {deleteError !== null && (
        <Column sm={4} md={8} lg={16}>
          <InlineNotification
            kind="error"
            lowContrast
            title="The site was not deleted"
            subtitle={deleteError}
            data-testid="site-delete-error"
            onCloseButtonClick={() => setDeleteError(null)}
          />
        </Column>
      )}

      {submitted !== null && (
        <Column sm={4} md={8} lg={16}>
          {results.isError ? (
            <InlineNotification
              kind="error"
              lowContrast
              hideCloseButton
              title="The search could not be run"
              subtitle="Nothing was changed. Try again, or narrow the criteria."
              data-testid="site-search-error"
            />
          ) : (
            <SiteSearchResults
              results={results.data?.content ?? []}
              totalItems={results.data?.totalElements ?? 0}
              loading={results.isPending || results.isPlaceholderData}
              page={page}
              pageSize={pageSize}
              canDelete={canDelete}
              onPageChange={({ page: nextPage, pageSize: nextPageSize }) => {
                setPage(nextPage);
                setPageSize(nextPageSize);
              }}
              onDelete={setPendingDelete}
            />
          )}
        </Column>
      )}

      {/* Legacy uses a bare window.confirm(). A modal instead, but the same contract: the delete is
          confirmed before it runs, and it is only reachable by a role that holds /deleteSite. */}
      <DestructiveModal
        open={pendingDelete !== null}
        title="Delete site"
        message={
          `Are you sure you would like to delete site ${pendingDelete?.id ?? ''}? ` +
          'This cannot be undone.'
        }
        confirmButtonText="Delete"
        loading={deleteSite.isPending}
        onCancel={() => setPendingDelete(null)}
        onConfirm={() => {
          const site = pendingDelete;
          if (site === null) {
            return;
          }
          setDeleteError(null);
          deleteSite.mutate(site.id, {
            onSuccess: () => setPendingDelete(null),
            onError: (error) => {
              setPendingDelete(null);
              setDeleteError(messageFor(error, site.id));
            },
          });
        }}
      />
    </Grid>
  );
};

export default SiteSearchPage;
