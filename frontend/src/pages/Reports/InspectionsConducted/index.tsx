import { useMemo } from 'react';

import { areaGroup, roadFields } from '../criteriaGroups';
import MaintainerFields from '../MaintainerFields';
import ReportCriteriaForm from '../ReportCriteriaForm';
import ReportPageLayout from '../ReportPageLayout';
import { useCommonCodeTables } from '../useCommonCodeTables';
import { useReportCriteria } from '../useReportCriteria';
import { useReportDownload } from '../useReportDownload';

import {
  DETAIL_SORT_OPTIONS,
  EMPTY_DETAIL_CRITERIA,
  EMPTY_SUMMARY_CRITERIA,
  INSPECTIONS_CONDUCTED_STATE_KEYS,
  SUMMARY_SORT_OPTIONS,
  type InspectionsConductedCriteria,
  type InspectionsConductedSort,
  type InspectionsConductedVariant,
} from './types';
import { criteriaErrors } from './validation';

import type { SortOption } from '../ReportOutput';
import type { FC } from 'react';

import {
  useInspectionReportStatusCodes,
  useInspectionsConductedReferenceDataState,
  useInspectionTypeCodes,
} from '@/hooks/useConfiguration';
import {
  useInspectionsConductedDetailReport,
  useInspectionsConductedSummaryReport,
} from '@/hooks/useReports';

/**
 * PDF to print, CSV to work with, KMZ to see on a map — a button each. Legacy offered PDF, Word
 * and Excel; the KMZ is the Structure Inventory – Detailed one, for the structures listed.
 */
const FORMATS = ['CSV', 'KMZ', 'PDF'] as const;

/** What sets the two reports apart. */
type Variant = {
  id: string;
  title: string;
  subtitle: string;
  stateKey: string;
  empty: InspectionsConductedCriteria;
  sortOptions: readonly SortOption<InspectionsConductedSort>[];
  /** The Detail asks for the inspection type; the Summary does not. */
  inspectionType: boolean;
  /** The file's name before the date. */
  filename: string;
};

const VARIANTS: Record<InspectionsConductedVariant, Variant> = {
  summary: {
    id: 'inspections-conducted-summary',
    title: 'Inspections Conducted – Summary',
    subtitle: 'One line per inspection: filter by when it was done, its status and who did it.',
    stateKey: INSPECTIONS_CONDUCTED_STATE_KEYS.summary,
    empty: EMPTY_SUMMARY_CRITERIA,
    sortOptions: SUMMARY_SORT_OPTIONS,
    inspectionType: false,
    filename: 'InspectionsConductedSummaryReport',
  },
  detail: {
    id: 'inspections-conducted-detail',
    title: 'Inspections Conducted – Detail',
    subtitle:
      'Each inspection in full: filter by its type, when it was done, its status and who did it.',
    stateKey: INSPECTIONS_CONDUCTED_STATE_KEYS.detail,
    empty: EMPTY_DETAIL_CRITERIA,
    sortOptions: DETAIL_SORT_OPTIONS,
    inspectionType: true,
    filename: 'InspectionsConductedDetailReport',
  },
};

/**
 * Inspections Conducted – Summary (legacy CBR002) and – Detail (legacy CBR001): the inspections
 * done, filtered by when, by status and by who did them.
 *
 * <p>Legacy equivalents: `showInspectionsConductedReport.do` → `inspections_conducted.jsp` and
 * `showInspectionReport.do` → `inspection_detail.jsp`, each gated on its `/show…Report`, which
 * every role that can read holds. The two pages ask the same things — the Detail also the
 * inspection type, Routine unless changed — so they are one form here. Laid out in the reports'
 * four-column grid: the structure and the inspection; where the structure is held and its road;
 * who maintains it and who inspected; then the three switches. Labels are legacy's; Inspection
 * Date is a month range, as on Inspection Search.
 *
 * <p>The criteria are kept for the browser tab, each report its own; Clear goes back to the
 * defaults. Download PDF and Download CSV fetch the report from the server and save it.
 */
const InspectionsConductedPage: FC<{ variant: InspectionsConductedVariant }> = ({ variant }) => {
  const config = VARIANTS[variant];
  const { criteria, updateCriteria, reset, resetToken } = useReportCriteria(
    config.stateKey,
    config.empty,
  );
  const { codeTables, managementAreasLoading } = useCommonCodeTables(criteria.orgUnit);
  const reportStatusCodes = useInspectionReportStatusCodes();
  const inspectionTypeCodes = useInspectionTypeCodes();
  const referenceData = useInspectionsConductedReferenceDataState();
  const errors = useMemo(() => criteriaErrors(criteria), [criteria]);

  // Both mutations, as hooks are called every render; the page's variant picks its own.
  const summaryReport = useInspectionsConductedSummaryReport();
  const detailReport = useInspectionsConductedDetailReport();
  const { run, running } = useReportDownload({
    report: variant === 'summary' ? summaryReport : detailReport,
    criteria,
    filename: config.filename,
  });

  return (
    <ReportPageLayout
      id={config.id}
      title={config.title}
      subtitle={config.subtitle}
      codeTablesFailed={referenceData.isError}
    >
      <ReportCriteriaForm
        key={`${variant}-${resetToken}`}
        id={config.id}
        criteria={criteria}
        sortOptions={config.sortOptions}
        errors={errors}
        formats={FORMATS}
        codeTablesLoading={referenceData.isLoading}
        onChange={updateCriteria}
        onRun={run}
        running={running}
        onReset={reset}
      >
        {(fields) => (
          <>
            {/* The structure and the inspection */}
            {fields.text('structureName', 'Structure #', 14)}
            {fields.codeSelect(
              'siteTypeCode',
              'Site Type',
              'Any site type',
              codeTables.siteTypeCodes,
            )}
            {config.inspectionType &&
              fields.codeSelect(
                'inspectionTypeCode',
                'Inspection Type',
                'Any inspection type',
                inspectionTypeCodes.data ?? [],
              )}
            {fields.codeSelect(
              'inspectionReportStatusCode',
              'Inspection Report Status',
              'Any status',
              reportStatusCodes.data ?? [],
            )}
            {fields.monthRange('inspectionDateStart', 'inspectionDateEnd', 'Inspection Date')}

            {/* Where the structure is held, and its road */}
            {areaGroup(fields, codeTables, {
              criteria,
              loading: referenceData.isLoading || managementAreasLoading,
            })}
            {roadFields(fields)}

            {/* Who maintains it, and who inspected */}
            <MaintainerFields
              id={config.id}
              clientNumber={criteria.clientNumber}
              clientLocationCode={criteria.clientLocationCode}
              maintainerLabel={criteria.maintainerLabel}
              onChange={updateCriteria}
            />
            {fields.text('inspectorName', 'Inspector Name', 255)}

            {/* The switches, a cell each */}
            {fields.toggle('closeProximity', 'Close Proximity Inspection Required?')}
            {fields.toggle('mostRecentInspections', 'Most Recent Inspections Only?')}
            {fields.toggle('professionalInspection', 'Professional Inspection Required?')}
          </>
        )}
      </ReportCriteriaForm>
    </ReportPageLayout>
  );
};

export default InspectionsConductedPage;
