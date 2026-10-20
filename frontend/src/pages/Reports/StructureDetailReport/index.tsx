import { Search as SearchIcon } from '@carbon/icons-react';
import { Button, InlineNotification } from '@carbon/react';
import { useCallback, useEffect, useState } from 'react';

import { criteriaFields } from '../criteriaFields';
import MaintainerFields from '../MaintainerFields';
import { reportFilename } from '../reportFilename';
import ReportPageLayout from '../ReportPageLayout';
import { useReportCriteria } from '../useReportCriteria';

import StructureDetailResults from './StructureDetailResults';
import {
  EMPTY_CRITERIA,
  toStructureSearch,
  type ReportFormat,
  type StructureDetailReportCriteria,
} from './types';

import type { StructureSearchCriteria, StructureSearchResult } from '@/pages/StructureSearch/types';
import type { FC, SubmitEventHandler } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import {
  useForestDistricts,
  useManagementAreas,
  useSiteTypeCodes,
  useStructureDetailReportReferenceDataState,
} from '@/hooks/useConfiguration';
import { useStructureDetailReport } from '@/hooks/useReports';
import { useStructureSearch } from '@/hooks/useStructureSearch';
import { apiErrorMessage } from '@/utils/apiError';
import { saveFile } from '@/utils/saveFile';
import { readSessionState, writeSessionState } from '@/utils/sessionState';

/** Where the page keeps its criteria for the tab. */
export const STRUCTURE_DETAIL_REPORT_STATE_KEY = 'cbr.report.structureDetail';

/** Where it keeps the search that was run, and the page of its results being shown. */
const SEARCH_STATE_KEY = `${STRUCTURE_DETAIL_REPORT_STATE_KEY}.search`;

const ID = 'structure-detail-report';

type SavedSearch = {
  submitted: StructureSearchCriteria | null;
  page: number;
  pageSize: number;
};

/**
 * Structure Detail Report — the full record of one structure (legacy CBR006).
 *
 * <p>Legacy equivalent: `showStructureDetailReport.do` → `structure_detail.jsp`, gated on
 * `/showStructureDetailReport`, which every role that can read holds. Unlike the Inventory reports
 * it is of one structure: legacy's criteria find structures — through the same structure search
 * Structure Search runs, archived ones left out — and each one found has a Download button per
 * format for its report.
 *
 * <p>The criteria and the search are kept for the browser tab, as on the search pages; Clear
 * forgets both. A row's PDF or CSV downloads as `StructureDetailReport_<Structure #>_<date>`.
 */
const StructureDetailReportPage: FC = () => {
  const { criteria, updateCriteria, reset, resetToken } = useReportCriteria(
    STRUCTURE_DETAIL_REPORT_STATE_KEY,
    EMPTY_CRITERIA,
  );
  const [search, setSearch] = useState<SavedSearch>(() => ({
    submitted: null,
    page: 1,
    pageSize: 20,
    ...readSessionState<Partial<SavedSearch>>(SEARCH_STATE_KEY),
  }));
  const { display } = useNotification();

  useEffect(() => {
    writeSessionState(SEARCH_STATE_KEY, search);
  }, [search]);

  const siteTypeCodes = useSiteTypeCodes();
  const forestDistricts = useForestDistricts();
  const managementAreas = useManagementAreas(criteria.orgUnit);
  const referenceData = useStructureDetailReportReferenceDataState();

  // Carbon's Pagination is one-based; the backend is zero-based.
  const results = useStructureSearch(search.submitted, search.page - 1, search.pageSize);

  const { text, codeSelect, orgUnitSelect } = criteriaFields<StructureDetailReportCriteria>({
    id: ID,
    criteria,
    // Nothing here can be malformed: every box is a code or free text.
    shown: {},
    leave: () => undefined,
    onChange: updateCriteria,
    codeTablesLoading: referenceData.isLoading,
  });

  const submit: SubmitEventHandler<HTMLFormElement> = (event) => {
    event.preventDefault();
    setSearch((current) => ({ ...current, submitted: toStructureSearch(criteria), page: 1 }));
  };

  const clear = useCallback(() => {
    reset();
    setSearch((current) => ({ ...current, submitted: null, page: 1 }));
  }, [reset]);

  const report = useStructureDetailReport();
  const running = report.isPending ? report.variables : null;

  const run = useCallback(
    (structure: StructureSearchResult, format: ReportFormat) => {
      report.mutate(
        { structureId: structure.id, format },
        {
          onSuccess: (file) =>
            saveFile(
              file,
              reportFilename(
                `StructureDetailReport_${structure.structureName || structure.id}`,
                format,
              ),
            ),
          onError: (error) => {
            display({
              kind: 'error',
              title: `The ${format} could not be produced`,
              subtitle: apiErrorMessage(error, 'Try again. Contact support if this continues.'),
              timeout: 0,
            });
          },
        },
      );
    },
    [report, display],
  );

  return (
    <ReportPageLayout
      id={ID}
      title="Structure Detail Report"
      subtitle="The full record of one structure: find it, then download its report."
      codeTablesFailed={referenceData.isError}
    >
      <form
        key={resetToken}
        onSubmit={submit}
        data-testid={`${ID}-form`}
        className="report-criteria__form"
      >
        <div className="report-criteria__grid">
          {text('structureName', 'Structure #', 14)}
          {codeSelect('siteTypeCode', 'Site Type', 'Any site type', siteTypeCodes.data ?? [])}
          <div className="report-criteria__paired">
            {text('forestFileId', 'Project File ID#', 10)}
            {text('roadSectionId', 'Br.', 30)}
          </div>
          {orgUnitSelect('orgUnit', 'Forest District', 'Any district', forestDistricts.data ?? [])}
          {orgUnitSelect(
            'managementOrgUnit',
            'Management Area',
            criteria.orgUnit === '' ? 'Select a forest district first' : 'Any management area',
            managementAreas.data ?? [],
            referenceData.isLoading || managementAreas.isFetching,
          )}
          <MaintainerFields
            id={ID}
            clientNumber={criteria.clientNumber}
            clientLocationCode={criteria.clientLocationCode}
            maintainerLabel={criteria.maintainerLabel}
            onChange={updateCriteria}
          />
        </div>
        <div className="report-criteria__actions">
          <Button kind="ghost" type="button" onClick={clear} data-testid={`${ID}-reset`}>
            Clear
          </Button>
          <Button type="submit" renderIcon={SearchIcon} data-testid={`${ID}-search`}>
            Search
          </Button>
        </div>
      </form>

      {search.submitted !== null && (
        <div className="report-criteria__results-column">
          {results.isError ? (
            <InlineNotification
              kind="error"
              lowContrast
              hideCloseButton
              title="The search could not be run"
              subtitle="Try again, or narrow the criteria."
              data-testid={`${ID}-error`}
            />
          ) : (
            <StructureDetailResults
              results={results.data?.content ?? []}
              totalItems={results.data?.totalElements ?? 0}
              loading={results.isPending || results.isPlaceholderData}
              page={search.page}
              pageSize={search.pageSize}
              onPageChange={({ page, pageSize }) =>
                setSearch((current) => ({ ...current, page, pageSize }))
              }
              onRun={run}
              running={running}
            />
          )}
        </div>
      )}
    </ReportPageLayout>
  );
};

export default StructureDetailReportPage;
