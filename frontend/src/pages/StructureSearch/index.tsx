import { Column, Grid, InlineNotification } from '@carbon/react';
import { useCallback, useMemo, useState } from 'react';

import PageTitle from '@/components/core/PageTitle';

import StructureSearchCriteriaForm, { type CodeTables } from './StructureSearchCriteria';
import StructureSearchResults from './StructureSearchResults';
import { EMPTY_CRITERIA, type StructureSearchCriteria, type StructureSort } from './types';

import './structureSearch.scss';

import type { FC } from 'react';

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
import { useStructureSearch } from '@/hooks/useStructureSearch';

/**
 * Structure Search — Inventory's third screen, after Add Site.
 *
 * <p>Legacy equivalent: `showStructureSearch.do` → `structure_search.jsp`, gated on
 * `/showStructureSearch`, which every role that can read holds. Search only for now: legacy's bulk
 * Archive, Delete and Update Repair Responsibility are not here.
 *
 * <p>Built the same way as Site Search: the form edits live criteria, Search snapshots them, and
 * the query is keyed on the snapshot. Paging and ordering are the server's — district, road, Br.,
 * km, as legacy orders them.
 */
const StructureSearchPage: FC = () => {
  const [criteria, setCriteria] = useState<StructureSearchCriteria>(EMPTY_CRITERIA);
  /** The criteria the visible results belong to; `null` until the first search. */
  const [submitted, setSubmitted] = useState<StructureSearchCriteria | null>(null);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [resetToken, setResetToken] = useState(0);
  /** The header the results are sorted by. Kept across new searches; cleared by Reset. */
  const [sort, setSort] = useState<StructureSort | null>(null);

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
    setSubmitted(criteria);
  }, [criteria]);

  const reset = useCallback(() => {
    setCriteria(EMPTY_CRITERIA);
    setSubmitted(null);
    setPage(1);
    setSort(null);
    setResetToken((token) => token + 1);
  }, []);

  return (
    <Grid fullWidth className="default-grid structure-search-grid">
      <PageTitle
        title="Structure Search"
        experimental
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
              onSortChange={(next) => {
                setSort(next);
                // Back to page one: page 4 in one order is a different twenty rows in another.
                setPage(1);
              }}
            />
          )}
        </Column>
      )}
    </Grid>
  );
};

export default StructureSearchPage;
