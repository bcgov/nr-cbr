import { useMutation } from '@tanstack/react-query';

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
