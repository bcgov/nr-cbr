import { Column, Grid, InlineNotification } from '@carbon/react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';

import DestructiveModal from '@/components/core/DestructiveModal';
import PageTitle from '@/components/core/PageTitle';

import DeleteStructuresDialog from './DeleteStructuresDialog';
import RepairResponsibilityDialog from './RepairResponsibilityDialog';
import { blockedLine, selectedStructures, splitForDelete } from './selection';
import StructureSearchCriteriaForm, { type CodeTables } from './StructureSearchCriteria';
import StructureSearchResults from './StructureSearchResults';
import {
  EMPTY_CRITERIA,
  type SelectedStructure,
  type StructureSearchCriteria,
  type StructureSearchResult,
  type StructureSort,
} from './types';

import './structureSearch.scss';

import type { FC } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import {
  useCulvertTypeCodes,
  useForestDistricts,
  useManagementAreas,
  useSiteStatusCodes,
  useSiteTypeCodes,
  useSpecialAccessCodes,
  useSpecialEquipmentCodes,
  useStructureCurbTypeCodes,
  useStructureReferenceDataState,
  useStructureTypeClassCodes,
  useSuperstructureTypeCodes,
} from '@/hooks/useConfiguration';
import { useArchiveStructures, useStructureSearch } from '@/hooks/useStructureSearch';
import { apiErrorMessage } from '@/utils/apiError';
import { readSessionState, writeSessionState } from '@/utils/sessionState';

/**
 * Structure Search — Inventory's third screen, after Add Site.
 *
 * <p>Legacy equivalent: `showStructureSearch.do` → `structure_search.jsp`, gated on
 * `/showStructureSearch`, which every role that can read holds. Archive, Delete and Update Repair
 * Responsibility act on the ticked structures.
 *
 * <p>Built the same way as Site Search: the form edits live criteria, Search snapshots them, and
 * the query is keyed on the snapshot. Paging and ordering are the server's — district, road, Br.,
 * km, as legacy orders them.
 */
/** Where the page keeps its search for the tab. */
export const STRUCTURE_SEARCH_STATE_KEY = 'cbr.structureSearch';

/**
 * What is kept, so leaving for a structure and coming back finds the search as it was left. The
 * ticks are not: they belong to the moment, and an action run on them later would act on rows the
 * user may no longer have in mind.
 */
type SavedStructureSearch = {
  criteria: StructureSearchCriteria;
  submitted: StructureSearchCriteria | null;
  page: number;
  pageSize: number;
  sort: StructureSort | null;
};

/**
 * Where the page opens. A site sent by Site Detail's Display Structures (`?siteId=`) wins: it is a
 * new search, run at once for that site, as legacy's `location=site` does. Otherwise the search
 * kept for this tab, its criteria laid over the empty set so one saved before a criterion was added
 * still restores; otherwise blank.
 */
const initialSearch = (siteId: string | null): SavedStructureSearch => {
  if (siteId) {
    const criteria = { ...EMPTY_CRITERIA, siteId };
    return { criteria, submitted: criteria, page: 1, pageSize: 20, sort: null };
  }
  const saved = readSessionState<Partial<SavedStructureSearch>>(STRUCTURE_SEARCH_STATE_KEY);
  return {
    criteria: { ...EMPTY_CRITERIA, ...saved?.criteria },
    submitted: saved?.submitted ? { ...EMPTY_CRITERIA, ...saved.submitted } : null,
    page: saved?.page ?? 1,
    pageSize: saved?.pageSize ?? 20,
    sort: saved?.sort ?? null,
  };
};

const StructureSearchPage: FC = () => {
  // Level 1 and above may tick rows — legacy's `/deleteStructure` or `/updateRepairResponsibility`.
  const { canEdit, canDelete } = useAuthorization();
  // The last search in this tab comes back with the page — through the breadcrumb, the side nav or
  // Back — unless Site Detail sent a site to search. Reset forgets it.
  const [searchParams] = useSearchParams();
  const [initial] = useState(() => initialSearch(searchParams.get('siteId')));
  const [criteria, setCriteria] = useState<StructureSearchCriteria>(initial.criteria);
  /** The criteria the visible results belong to; `null` until the first search. */
  const [submitted, setSubmitted] = useState<StructureSearchCriteria | null>(initial.submitted);
  const [page, setPage] = useState(initial.page);
  const [pageSize, setPageSize] = useState(initial.pageSize);
  const [resetToken, setResetToken] = useState(0);
  /** The header the results are sorted by. Kept across new searches; cleared by Reset. */
  const [sort, setSort] = useState<StructureSort | null>(initial.sort);

  useEffect(() => {
    writeSessionState(STRUCTURE_SEARCH_STATE_KEY, {
      criteria,
      submitted,
      page,
      pageSize,
      sort,
    } satisfies SavedStructureSearch);
  }, [criteria, submitted, page, pageSize, sort]);
  /**
   * The ticked structures, by id. Kept while the user pages and sorts, so one action can cover rows
   * from several pages — which is why each carries its name and what blocks its delete: its row
   * may not be on screen when the action runs. Cleared by a new search or Reset, which change the
   * result set the ticks were made in.
   */
  const [selected, setSelected] = useState<ReadonlyMap<string, SelectedStructure>>(new Map());

  /** True while the archive confirmation is open. */
  const [confirmingArchive, setConfirmingArchive] = useState(false);
  const archive = useArchiveStructures();
  const { display } = useNotification();

  /** True while the delete confirmation is open. */
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  /** True while the Update Repair Responsibility dialog is open. */
  const [reassigning, setReassigning] = useState(false);

  const toggle = useCallback((row: StructureSearchResult) => {
    setSelected((current) => {
      const next = new Map(current);
      if (!next.delete(row.id)) {
        next.set(row.id, {
          name: row.structureName || row.id,
          siteId: row.siteId || null,
          deleteBlockers: row.deleteBlockers ?? null,
        });
      }
      return next;
    });
  }, []);

  /**
   * Delete opens the confirmation — unless nothing ticked can be deleted, when there is nothing to
   * confirm and the user is told why instead.
   */
  const startDelete = useCallback(() => {
    const { deletable, blocked } = splitForDelete(selected);
    if (deletable.length > 0) {
      setConfirmingDelete(true);
      return;
    }
    display({
      kind: 'error',
      title: 'None of the selected structures can be deleted',
      subtitle: `${blocked.map(blockedLine).join('; ')}.`,
      timeout: 0,
    });
  }, [selected, display]);

  // Carbon's Pagination is one-based; the backend is zero-based.
  const results = useStructureSearch(submitted, page - 1, pageSize, sort);

  const structureTypeClassCodes = useStructureTypeClassCodes();
  const superstructureTypeCodes = useSuperstructureTypeCodes();
  const structureCurbTypeCodes = useStructureCurbTypeCodes();
  const culvertTypeCodes = useCulvertTypeCodes();
  const siteStatusCodes = useSiteStatusCodes();
  const siteTypeCodes = useSiteTypeCodes();
  const specialAccessCodes = useSpecialAccessCodes();
  const specialEquipmentCodes = useSpecialEquipmentCodes();
  const forestDistricts = useForestDistricts();
  const managementAreas = useManagementAreas(criteria.orgUnit);

  const referenceData = useStructureReferenceDataState();

  // A failed lookup is an empty list, not a blocked form — every criterion is optional. The
  // failure is reported above the form instead.
  const codeTables = useMemo<CodeTables>(
    () => ({
      structureTypeClassCodes: structureTypeClassCodes.data ?? [],
      superstructureTypeCodes: superstructureTypeCodes.data ?? [],
      structureCurbTypeCodes: structureCurbTypeCodes.data ?? [],
      culvertTypeCodes: culvertTypeCodes.data ?? [],
      siteStatusCodes: siteStatusCodes.data ?? [],
      siteTypeCodes: siteTypeCodes.data ?? [],
      specialAccessCodes: specialAccessCodes.data ?? [],
      specialEquipmentCodes: specialEquipmentCodes.data ?? [],
      forestDistricts: forestDistricts.data ?? [],
      managementAreas: managementAreas.data ?? [],
    }),
    [
      structureTypeClassCodes.data,
      superstructureTypeCodes.data,
      structureCurbTypeCodes.data,
      culvertTypeCodes.data,
      siteStatusCodes.data,
      siteTypeCodes.data,
      specialAccessCodes.data,
      specialEquipmentCodes.data,
      forestDistricts.data,
      managementAreas.data,
    ],
  );

  const updateCriteria = useCallback(
    <K extends keyof StructureSearchCriteria>(field: K, value: StructureSearchCriteria[K]) => {
      setCriteria((current) => ({
        ...current,
        [field]: value,
        // A management area belongs to one district; keeping it after the district changes would
        // apply a filter the user can no longer see.
        ...(field === 'orgUnit' ? { managementOrgUnit: '' } : {}),
      }));
    },
    [],
  );

  const search = useCallback(() => {
    setPage(1);
    setSelected(new Map());
    setSubmitted(criteria);
  }, [criteria]);

  const reset = useCallback(() => {
    setCriteria(EMPTY_CRITERIA);
    setSubmitted(null);
    setPage(1);
    setSort(null);
    setSelected(new Map());
    setResetToken((token) => token + 1);
  }, []);

  // Undefined leaves the checkbox column off the table for a role that may not act on structures.
  const selection = canEdit ? selected : undefined;

  return (
    <Grid fullWidth className="default-grid structure-search-grid">
      <PageTitle
        title="Structure Search"
        subtitle="Find a bridge or culvert by its type, its site, its maintainer or its replacement dates."
        breadCrumbs={[{ name: 'Inventory', path: '/inventory' }]}
      />

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
            data-testid="structure-search-codes-error"
          />
        </Column>
      )}

      <Column sm={4} md={8} lg={16}>
        <StructureSearchCriteriaForm
          criteria={criteria}
          codeTables={codeTables}
          codeTablesLoading={referenceData.isLoading}
          managementAreasLoading={managementAreas.isFetching}
          onChange={updateCriteria}
          resetToken={resetToken}
          onSearch={search}
          onReset={reset}
        />
      </Column>

      {submitted !== null && (
        <Column sm={4} md={8} lg={16}>
          {results.isError ? (
            <InlineNotification
              kind="error"
              lowContrast
              hideCloseButton
              title="The search could not be run"
              subtitle="Nothing was changed. Try again, or narrow the criteria."
              data-testid="structure-search-error"
            />
          ) : (
            <StructureSearchResults
              results={results.data?.content ?? []}
              totalItems={results.data?.totalElements ?? 0}
              loading={results.isPending || results.isPlaceholderData}
              page={page}
              pageSize={pageSize}
              onPageChange={({ page: nextPage, pageSize: nextPageSize }) => {
                setPage(nextPage);
                setPageSize(nextPageSize);
              }}
              sort={sort}
              selected={selection}
              canDelete={canDelete}
              onToggle={toggle}
              onClearSelection={() => setSelected(new Map())}
              onArchive={() => setConfirmingArchive(true)}
              onDelete={startDelete}
              onUpdateRepairResponsibility={() => setReassigning(true)}
              onSortChange={(next) => {
                setSort(next);
                // Back to page one: page 4 in one order is a different twenty rows in another.
                setPage(1);
              }}
            />
          )}
        </Column>
      )}

      {/* Legacy's window.confirm(), with the count added: a tick survives paging here, so some of
          the structures being archived may be on pages not in view. */}
      <DestructiveModal
        open={confirmingArchive}
        title="Archive structures"
        message={
          `Are you sure you would like to archive ${selectedStructures(selected.size)}? ` +
          'Archived structures cannot be recovered.'
        }
        confirmButtonText="Archive"
        loading={archive.isPending}
        onCancel={() => setConfirmingArchive(false)}
        onConfirm={() => {
          archive.mutate([...selected.keys()], {
            onSuccess: ({ archivedCount }) => {
              setConfirmingArchive(false);
              setSelected(new Map());
              // Legacy's "{0} structure(s) were successfully archived.", with the plural resolved.
              display({
                kind: 'success',
                title: `${archivedCount} structure${archivedCount === 1 ? ' was' : 's were'} archived`,
                timeout: 4000,
              });
            },
            onError: (error) => {
              setConfirmingArchive(false);
              // The ticks are kept, so the user can try again without finding them all again. A
              // toast, as Site Search reports a failed delete: nothing on screen changed.
              display({
                kind: 'error',
                title: 'The structures were not archived',
                subtitle: apiErrorMessage(
                  error,
                  'Nothing was changed. Try again, or contact support if this continues.',
                ),
                timeout: 0,
              });
            },
          });
        }}
      />

      {/* Mounted only while open. Carbon keeps a closed modal in the page, which would leave a
          second "Designated Maintainer" field beside the search form's for a screen reader, and
          mounting afresh starts each opening with nothing picked. */}
      {reassigning && (
        <RepairResponsibilityDialog
          open
          selected={selected}
          onClose={() => setReassigning(false)}
          onUpdated={() => setSelected(new Map())}
        />
      )}

      <DeleteStructuresDialog
        open={confirmingDelete}
        selected={selected}
        onClose={() => setConfirmingDelete(false)}
        onDeleted={(ids) =>
          setSelected((current) => {
            const next = new Map(current);
            ids.forEach((id) => next.delete(id));
            return next;
          })
        }
      />
    </Grid>
  );
};

export default StructureSearchPage;
