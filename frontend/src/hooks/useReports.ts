import { useMutation } from '@tanstack/react-query';

import type { MonitorsSummaryCriteria } from '@/pages/Reports/MonitorsSummary/types';
import type { RepairsSummaryCriteria } from '@/pages/Reports/RepairsSummary/types';
import type { StructureInventoryDetailedCriteria } from '@/pages/Reports/StructureInventoryDetailed/types';
import type {
  ReportFormat,
  StructureInventorySummaryCriteria,
} from '@/pages/Reports/StructureInventorySummary/types';

import API from '@/services/APIs';

/**
 * Produces the Structure Inventory – Summary report as a file. A mutation rather than a query: each
 * press asks for a fresh report, and nothing is cached or refetched behind the user's back.
 */
export const useStructureInventorySummaryReport = () =>
  useMutation({
    mutationFn: ({
      criteria,
      format,
    }: {
      criteria: StructureInventorySummaryCriteria;
      format: ReportFormat;
    }) => API.report.structureInventorySummary(criteria, format),
  });

/** Produces the Structure Inventory – Detailed report as a file. A mutation, as the Summary's is. */
export const useStructureInventoryDetailedReport = () =>
  useMutation({
    mutationFn: ({
      criteria,
      format,
    }: {
      criteria: StructureInventoryDetailedCriteria;
      format: ReportFormat;
    }) => API.report.structureInventoryDetailed(criteria, format),
  });

/** Produces one structure's Structure Detail Report as a file. A mutation, as the others are. */
export const useStructureDetailReport = () =>
  useMutation({
    mutationFn: ({ structureId, format }: { structureId: string; format: ReportFormat }) =>
      API.report.structureDetail(structureId, format),
  });

/** Produces the Repairs Summary report as a file. A mutation, as the others are. */
export const useRepairsSummaryReport = () =>
  useMutation({
    mutationFn: ({
      criteria,
      format,
    }: {
      criteria: RepairsSummaryCriteria;
      format: ReportFormat;
    }) => API.report.repairsSummary(criteria, format),
  });

/** Produces the Monitors Summary report as a file. A mutation, as the others are. */
export const useMonitorsSummaryReport = () =>
  useMutation({
    mutationFn: ({
      criteria,
      format,
    }: {
      criteria: MonitorsSummaryCriteria;
      format: ReportFormat;
    }) => API.report.monitorsSummary(criteria, format),
  });
