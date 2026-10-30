import { useCallback, useMemo } from 'react';

import { areaGroup, roadFields } from '../criteriaGroups';
import MaintainerFields from '../MaintainerFields';
import ReportCriteriaForm from '../ReportCriteriaForm';
import ReportPageLayout from '../ReportPageLayout';
import { useCommonCodeTables } from '../useCommonCodeTables';
import { useReportCriteria } from '../useReportCriteria';
import { useReportDownload } from '../useReportDownload';

import {
  CLOSE_PROXIMITY_CHOICES,
  EMPTY_CRITERIA,
  INSPECTIONS_DUE_STATE_KEYS,
  NO_SPECIAL_EQUIPMENT,
  SORT_OPTIONS,
  type InspectionsDueCriteria,
  type InspectionsDueSort,
  type InspectionsDueVariant,
} from './types';
import { criteriaErrors } from './validation';

import type { SortOption } from '../ReportOutput';
import type { ReportFormat } from '../types';
import type { FC } from 'react';

import {
  useInspectionsDueReferenceDataState,
  useSpecialEquipmentCodes,
  useStructureInspectionStatusCodes,
  useStructureTypeClassCodes,
} from '@/hooks/useConfiguration';
import {
  useInspectionsBlankReport,
  useInspectionsDueDetailedReport,
  useInspectionsDueSummaryReport,
} from '@/hooks/useReports';

/**
 * PDF to print, CSV to work with, KMZ to see on a map — a button each. Legacy offered PDF, Word
 * and Excel; the KMZ is the Structure Inventory – Detailed one, for the structures listed.
 */
const FORMATS = ['CSV', 'KMZ', 'PDF'] as const;

/** The Blank's: its forms are for printing, not for a map. */
const BLANK_FORMATS = ['CSV', 'PDF'] as const;

/** What sets the three reports apart. */
type Variant = {
  id: string;
  title: string;
  subtitle: string;
  stateKey: string;
  /** The Due reports' orders; the Blank's is fixed. */
  sortOptions: readonly SortOption<InspectionsDueSort>[];
  /** The Blank asks for a Structure #, and not for structures with no date. */
  blank: boolean;
  /** The file's name before the date. */
  filename: string;
  formats: readonly ReportFormat[];
};

const VARIANTS: Record<InspectionsDueVariant, Variant> = {
  summary: {
    id: 'inspections-due-summary',
    title: 'Inspections Due – Summary',
    subtitle: 'One line per structure: filter by when its next inspection is planned.',
    stateKey: INSPECTIONS_DUE_STATE_KEYS.summary,
    sortOptions: SORT_OPTIONS,
    blank: false,
    filename: 'InspectionsDueSummaryReport',
    formats: FORMATS,
  },
  detailed: {
    id: 'inspections-due-detailed',
    title: 'Inspections Due – Detailed',
    subtitle: 'Each structure in detail: filter by when its next inspection is planned.',
    stateKey: INSPECTIONS_DUE_STATE_KEYS.detailed,
    sortOptions: SORT_OPTIONS,
    blank: false,
    filename: 'InspectionsDueDetailedReport',
    formats: FORMATS,
  },
  blank: {
    id: 'inspections-blank',
    title: 'Inspections – Blank Report',
    subtitle: 'A blank inspection form per structure, to fill in on site.',
    stateKey: INSPECTIONS_DUE_STATE_KEYS.blank,
    sortOptions: [],
    blank: true,
    filename: 'InspectionsBlankReport',
    formats: BLANK_FORMATS,
  },
};

/**
 * Inspections Due – Summary (legacy CBR004), – Detailed (legacy CBR003) and Inspections – Blank
 * Report (legacy CBR010): the structures whose next inspection is planned, filtered by when, by
 * the structure and by where it is held.
 *
 * <p>Legacy equivalents: `showInspectionsDueSummaryReport.do`, `showInspectionsDueDetailedReport.do`
 * and `showInspectionsBlankReport.do`, each gated on its `/show…Report`, which every role that can
 * read holds. The Due pages are one form in legacy; the Blank's asks the same but for Structure # in
 * place of No Next Planned Inspection Date and Sort By — so they are one form here. Laid out in the
 * reports' four-column grid: the dates and the inspection status; the structure; where it is held
 * and its road; who maintains it and what reaching it takes. Labels are legacy's.
 *
 * <p>Next Planned Inspection Date is a range of days, as legacy's, both ends included. No Next
 * Planned Inspection Date clears and sets aside the dates: legacy applied both, which can match
 * nothing. Close Proximity is legacy's either/Yes/No select, and Special Equipment Requirements
 * keeps legacy's "None".
 *
 * <p>The criteria are kept for the browser tab, each report its own; Clear goes back to the
 * defaults. Download PDF and Download CSV fetch the report from the server and save it.
 */
const InspectionsDuePage: FC<{ variant: InspectionsDueVariant }> = ({ variant }) => {
  const config = VARIANTS[variant];
  const { criteria, updateCriteria, reset, resetToken } = useReportCriteria(
    config.stateKey,
    EMPTY_CRITERIA,
  );
  const { codeTables, managementAreasLoading } = useCommonCodeTables(criteria.orgUnit);
  const typeClassCodes = useStructureTypeClassCodes();
  const inspectionStatusCodes = useStructureInspectionStatusCodes();
  const specialEquipmentCodes = useSpecialEquipmentCodes();
  const referenceData = useInspectionsDueReferenceDataState();
  const errors = useMemo(() => criteriaErrors(criteria), [criteria]);

  // The hooks are called every render; each variant takes its own report.
  const reports = {
    summary: useInspectionsDueSummaryReport(),
    detailed: useInspectionsDueDetailedReport(),
    blank: useInspectionsBlankReport(),
  };
  const { run, running } = useReportDownload({
    report: reports[variant],
    criteria,
    filename: config.filename,
  });

  // No Next Planned Inspection Date sets the dates aside: cleared, so they can neither conflict
  // with it nor hold an error the user can no longer reach.
  const onChange = useCallback(
    <K extends keyof InspectionsDueCriteria>(field: K, value: InspectionsDueCriteria[K]) => {
      updateCriteria(field, value);
      if (field === 'noNextPlannedInspectionDate' && value === true) {
        updateCriteria('nextPlannedInspectionDateStart', '');
        updateCriteria('nextPlannedInspectionDateEnd', '');
      }
    },
    [updateCriteria],
  );

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
        formats={config.formats}
        codeTablesLoading={referenceData.isLoading}
        onChange={onChange}
        onRun={run}
        running={running}
        onReset={reset}
      >
        {(fields) => (
          <>
            {/* When it is due, and its inspection status */}
            {config.blank && fields.text('structureName', 'Structure #', 14)}
            {/* Keyed on the switch: a calendar box keeps what was typed until it remounts. */}
            <div
              key={String(criteria.noNextPlannedInspectionDate)}
              className="report-criteria__wide"
            >
              {fields.dateRange(
                'nextPlannedInspectionDateStart',
                'nextPlannedInspectionDateEnd',
                'Next Planned Inspection Date',
                criteria.noNextPlannedInspectionDate,
              )}
            </div>
            {!config.blank &&
              fields.toggle('noNextPlannedInspectionDate', 'No Next Planned Inspection Date')}
            {fields.codeSelect(
              'structureInspectionStatusCode',
              'Inspection Status',
              'Any status',
              inspectionStatusCodes.data ?? [],
            )}

            {/* The structure */}
            {fields.codeSelect(
              'siteTypeCode',
              'Site Type',
              'Any site type',
              codeTables.siteTypeCodes,
            )}
            {fields.codeSelect(
              'structureTypeClassCode',
              'Type/Class',
              'Any type/class',
              typeClassCodes.data ?? [],
            )}
            {fields.codeSelect(
              'siteStatusCode',
              'Site Status',
              'Any status',
              codeTables.siteStatusCodes,
            )}
            {fields.select(
              'closeProximity',
              'Close Proximity Inspection Required?',
              'Either',
              CLOSE_PROXIMITY_CHOICES,
            )}

            {/* Where the structure is held, and its road */}
            {areaGroup(fields, codeTables, {
              criteria,
              loading: referenceData.isLoading || managementAreasLoading,
            })}
            {roadFields(fields)}

            {/* Who maintains it, and what reaching it takes */}
            <MaintainerFields
              id={config.id}
              clientNumber={criteria.clientNumber}
              clientLocationCode={criteria.clientLocationCode}
              maintainerLabel={criteria.maintainerLabel}
              onChange={updateCriteria}
            />
            {fields.codeSelect(
              'specialAccessCode',
              'Special Access Requirements',
              'Any requirement',
              codeTables.specialAccessCodes,
            )}
            {fields.codeSelect(
              'specialEquipmentCode',
              'Special Equipment Requirements',
              'Any requirement',
              specialEquipmentCodes.data ?? [],
              [{ value: NO_SPECIAL_EQUIPMENT, text: 'None' }],
            )}
          </>
        )}
      </ReportCriteriaForm>
    </ReportPageLayout>
  );
};

export default InspectionsDuePage;
