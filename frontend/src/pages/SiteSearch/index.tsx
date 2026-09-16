import { Column, Grid, InlineNotification } from '@carbon/react';
import { useCallback, useMemo, useState } from 'react';

import DestructiveModal from '@/components/core/DestructiveModal';
import PageTitle from '@/components/core/PageTitle';

import SiteSearchCriteriaForm, { type CodeTables } from './SiteSearchCriteria';
import SiteSearchResults from './SiteSearchResults';
import { EMPTY_CRITERIA, type SiteSearchCriteria, type SiteSearchResult } from './types';

import './siteSearch.scss';

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

/**
 * Site Search — the first screen of the Inventory section.
 *
 * <p>Legacy equivalent: `showSiteSearch.do` → `SiteSearchAction` → `site_search.jsp`, gated on the
 * `/showSiteSearch` privilege, which every role that can read holds.
 *
 * <p><b>The search itself is not wired yet.</b> The criteria form, the results table, the
 * authorization gates and all six dropdowns are real; running a search still returns nothing. It
 * will go through `CBR.FIND_SITES_BY_CRITERIA`, which takes a caller-built `WHERE` clause plus a
 * bind array rather than fixed parameters.
 */
const SiteSearchPage: FC = () => {
  const { canDelete } = useAuthorization();

  const [criteria, setCriteria] = useState<SiteSearchCriteria>(EMPTY_CRITERIA);
  const [searched, setSearched] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [pendingDelete, setPendingDelete] = useState<SiteSearchResult | null>(null);

  /**
   * Empty until a backend exists. Kept as state rather than a constant so wiring the query is a
   * change of source, not a change of shape.
   */
  const [results] = useState<SiteSearchResult[]>([]);

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
    setPage(1);
    setSearched(true);
  }, []);

  const reset = useCallback(() => {
    setCriteria(EMPTY_CRITERIA);
    setSearched(false);
    setPage(1);
  }, []);

  return (
    // PageTitle renders its own <Column>, so the page owns the <Grid>. Everything else sits in one
    // full-width column and lays itself out with CSS grid — the nr-frep pattern.
    <Grid fullWidth className="default-grid site-search-grid">
      <PageTitle
        title="Site Search"
        subtitle="Find a crossing site by location, tenure, maintainer or status."
        experimental
        breadCrumbs={[{ name: 'Inventory', path: '/inventory' }]}
      />

      <Column sm={4} md={8} lg={16}>
        {referenceData.isError ? (
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
        ) : (
          <InlineNotification
            kind="info"
            lowContrast
            hideCloseButton
            title="Search is not connected yet"
            subtitle={
              'The form, the results table and every dropdown are in place, but running a search ' +
              'returns nothing yet.'
            }
          />
        )}
      </Column>

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
      {searched && (
        <Column sm={4} md={8} lg={16}>
          <SiteSearchResults
            results={results}
            totalItems={results.length}
            page={page}
            pageSize={pageSize}
            canDelete={canDelete}
            onPageChange={({ page: nextPage, pageSize: nextPageSize }) => {
              setPage(nextPage);
              setPageSize(nextPageSize);
            }}
            onDelete={setPendingDelete}
          />
        </Column>
      )}

      {/* Legacy uses a bare window.confirm(). A modal instead, but the same contract: the delete is
          confirmed before it runs, and it is only reachable by a role that holds /deleteSite. */}
      <DestructiveModal
        open={pendingDelete !== null}
        title="Delete site"
        message={`Are you sure you would like to delete site ${pendingDelete?.id ?? ''}?`}
        confirmButtonText="Delete"
        onCancel={() => setPendingDelete(null)}
        // No backend to call yet, so confirming just closes. The wiring point is here.
        onConfirm={() => setPendingDelete(null)}
      />
    </Grid>
  );
};

export default SiteSearchPage;
