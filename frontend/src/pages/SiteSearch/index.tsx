import { Column, Grid, InlineNotification } from '@carbon/react';
import { useCallback, useState } from 'react';

import DestructiveModal from '@/components/core/DestructiveModal';
import PageTitle from '@/components/core/PageTitle';

import SiteSearchCriteriaForm, { type CodeTables } from './SiteSearchCriteria';
import SiteSearchResults from './SiteSearchResults';
import { EMPTY_CRITERIA, type SiteSearchCriteria, type SiteSearchResult } from './types';

import './siteSearch.scss';

import type { FC } from 'react';

import { useAuthorization } from '@/hooks/useAuthorization';

/**
 * Site Search — the first screen of the Inventory section.
 *
 * <p>Legacy equivalent: `showSiteSearch.do` → `SiteSearchAction` → `site_search.jsp`, gated on the
 * `/showSiteSearch` privilege, which every role that can read holds.
 *
 * <p><b>UI only — there is no backend yet.</b> The criteria form, the results table and the
 * authorization gates are real; nothing queries. The code-table selects are empty because their
 * contents live in the database and have never been extracted (`cbr-modernization-plan.local.md`
 * item 0.1), and the search itself will go through `CBR.FIND_SITES_BY_CRITERIA`, which takes a
 * caller-built `WHERE` clause plus a bind array rather than fixed parameters.
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
  const [codeTables] = useState<CodeTables>({
    siteStatusCodes: [],
    structureInspectionStatusCodes: [],
    specialAccessCodes: [],
    siteTypeCodes: [],
    forestDistricts: [],
    managementAreas: [],
  });

  const updateCriteria = useCallback(
    <K extends keyof SiteSearchCriteria>(field: K, value: SiteSearchCriteria[K]) => {
      setCriteria((current) => ({ ...current, [field]: value }));
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
        <InlineNotification
          kind="info"
          lowContrast
          hideCloseButton
          title="Not connected yet"
          subtitle={
            'The form and results table are in place, but no search runs and the dropdowns are ' +
            'empty — the code tables have not been extracted from the database yet.'
          }
        />
      </Column>

      <Column sm={4} md={8} lg={16}>
        <SiteSearchCriteriaForm
          criteria={criteria}
          codeTables={codeTables}
          codeTablesLoading={false}
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
