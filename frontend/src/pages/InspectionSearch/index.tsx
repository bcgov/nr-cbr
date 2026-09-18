import { Column, Grid, InlineNotification } from '@carbon/react';
import { useCallback, useMemo, useState } from 'react';

import DestructiveModal from '@/components/core/DestructiveModal';
import PageTitle from '@/components/core/PageTitle';

import InspectionSearchCriteriaForm, { type CodeTables } from './InspectionSearchCriteria';
import InspectionSearchResults from './InspectionSearchResults';
import {
  EMPTY_CRITERIA,
  type InspectionSearchCriteria,
  type InspectionSearchResult,
} from './types';

import './inspectionSearch.scss';

import type { FC } from 'react';

import { useAuthorization } from '@/hooks/useAuthorization';
import {
  useForestDistricts,
  useManagementAreas,
  useReferenceDataState,
} from '@/hooks/useConfiguration';

/**
 * Inspection Search — the first screen of the Inspection section.
 *
 * <p>Legacy equivalent: `showInspectionSearch.do` → `InspectionSearchAction` →
 * `inspection_search.jsp`, gated on the `/showInspectionSearch` privilege, which every role that
 * can read holds.
 *
 * <p><b>UI only — there is no inspection search endpoint yet.</b> The criteria form, its validation,
 * the results table and the authorization gates are real and complete; nothing queries. This is the
 * same shape Site Search shipped in first (#8), and the wiring points are marked below.
 *
 * <p>Two of the seven dropdowns are live — Forest District and Management Area, which
 * `/api/v1/configuration` already serves. The other five are empty because nothing serves them yet:
 * inspection report statuses, structure type/class, inspection types, BCTS business areas and the
 * reviewer list. Four are ordinary code tables; the reviewer list is not, and may not survive at all
 * — `cbr-auth-and-roles.local.md` §6 recommends moving the reviewer permission into FAM, which would
 * re-source it from `UserLookupClient` (decision D4).
 */
const InspectionSearchPage: FC = () => {
  /**
   * Legacy gates the delete icon on `/deleteInspection`, which is one of the nine deletes the
   * destructive capability covers — `CBR_LEVEL_2` and above.
   */
  const { canDelete } = useAuthorization();

  const [criteria, setCriteria] = useState<InspectionSearchCriteria>(EMPTY_CRITERIA);
  /**
   * The criteria the current results belong to — a snapshot taken when Search was pressed, kept
   * apart from the live form state so that editing a field after a search does not silently change
   * what the visible results claim to be. `null` means no search has been run, which is what keeps
   * the results table off the screen entirely.
   */
  const [submitted, setSubmitted] = useState<InspectionSearchCriteria | null>(null);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [pendingDelete, setPendingDelete] = useState<InspectionSearchResult | null>(null);

  /**
   * Empty until an endpoint exists. Kept as state rather than a constant so wiring the query is a
   * change of source, not a change of shape — the same way Site Search was wired later.
   */
  const [results] = useState<InspectionSearchResult[]>([]);

  const forestDistricts = useForestDistricts();
  // Management areas are the former districts inside the selected one, so this refetches — under
  // its own cache key — whenever Forest District changes, and does not run at all until one is
  // picked. Legacy does the same by posting the whole form back on change.
  const managementAreas = useManagementAreas(criteria.orgUnitNo);
  const referenceData = useReferenceDataState();

  /**
   * A failed lookup falls back to an empty list rather than blocking the form: every criterion is
   * optional on its own, so a search still runs without a district filter. The failure is surfaced
   * below instead — an empty dropdown with no explanation reads as "there are none".
   */
  const codeTables = useMemo<CodeTables>(
    () => ({
      // The five the configuration endpoint does not serve yet. Listed rather than omitted so the
      // form's shape is the finished one and wiring each is a one-line change here.
      structureTypeClassCodes: [],
      inspectionTypeCodes: [],
      inspectionReportStatusCodes: [],
      businessAreas: [],
      inspectionReviewers: [],
      forestDistricts: forestDistricts.data ?? [],
      managementAreas: managementAreas.data ?? [],
    }),
    [forestDistricts.data, managementAreas.data],
  );

  const updateCriteria = useCallback(
    <K extends keyof InspectionSearchCriteria>(field: K, value: InspectionSearchCriteria[K]) => {
      setCriteria((current) => ({
        ...current,
        [field]: value,
        // Changing the district changes which management areas exist, so a selection made under the
        // old one has to go. Leaving it would submit a management area that is not in the list the
        // user can now see — a filter they cannot tell is applied.
        ...(field === 'orgUnitNo' ? { managementOrgUnitNo: '' } : {}),
      }));
    },
    [],
  );

  const search = useCallback(() => {
    // Back to page one: the previous page number belongs to the previous result set, and page 4 of
    // a search that now matches twelve inspections is an empty table.
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
    <Grid fullWidth className="default-grid">
      <PageTitle
        title="Inspection Search"
        subtitle="Find inspections by structure, location, date, inspector or status."
        experimental
        breadCrumbs={[{ name: 'Inspection', path: '/inspection' }]}
      />

      <Column sm={4} md={8} lg={16}>
        <InlineNotification
          kind="info"
          lowContrast
          hideCloseButton
          title="Not connected yet"
          subtitle={
            'The form and results table are in place, but no search runs. Forest District and ' +
            'Management Area are live; the other five dropdowns are empty until the configuration ' +
            'endpoint serves them.'
          }
          data-testid="inspection-search-placeholder"
        />
      </Column>

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
            data-testid="inspection-search-codes-error"
          />
        </Column>
      )}

      <Column sm={4} md={8} lg={16}>
        <InspectionSearchCriteriaForm
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
      {submitted !== null && (
        <Column sm={4} md={8} lg={16}>
          <InspectionSearchResults
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

      {/* Legacy uses a bare window.confirm() — "Are you sure you would like to delete this offline
          inspection?" — then navigates to deleteInspection.do. A modal instead, but the same
          contract: the delete is confirmed before it runs, and it is only reachable on an offline
          row by a role holding /deleteInspection. */}
      <DestructiveModal
        open={pendingDelete !== null}
        title="Delete offline inspection"
        message={`Are you sure you would like to delete offline inspection ${pendingDelete?.id ?? ''}?`}
        confirmButtonText="Delete"
        onCancel={() => setPendingDelete(null)}
        // No endpoint to call yet, so confirming just closes. The wiring point is here.
        onConfirm={() => setPendingDelete(null)}
      />
    </Grid>
  );
};

export default InspectionSearchPage;
