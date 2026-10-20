import type { ReportFormat } from './types';

/**
 * "StructureInventorySummaryReport_2026-10-20.pdf" — the name the server gives a report's file:
 * legacy's name for it, dated today. The page saves under it, as the response's bytes reach it
 * without the server's headers.
 */
export const reportFilename = (report: string, format: ReportFormat): string => {
  const today = new Date();
  const pad = (value: number) => String(value).padStart(2, '0');
  const date = `${today.getFullYear()}-${pad(today.getMonth() + 1)}-${pad(today.getDate())}`;
  return `${report}_${date}.${format.toLowerCase()}`;
};
