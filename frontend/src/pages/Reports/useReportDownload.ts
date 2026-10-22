import { useCallback } from 'react';

import { reportFilename } from './reportFilename';

import type { ReportFormat } from './types';
import type { UseMutationResult } from '@tanstack/react-query';

import { useNotification } from '@/context/notification/useNotification';
import { apiErrorMessage } from '@/utils/apiError';
import { saveFile } from '@/utils/saveFile';

type Options<C> = {
  /**
   * The report's mutation — `useStructureInventorySummaryReport()` and its kin. None for a report
   * whose criteria page is built before the report: every format then says it is not available.
   */
  report?: UseMutationResult<Blob, unknown, { criteria: C; format: ReportFormat }>;
  criteria: C;
  /** The file's name before the date, e.g. `StructureInventorySummaryReport`. */
  filename: string;
  /** Formats offered on the page but not built yet: they say so rather than run. */
  notYetAvailable?: readonly ReportFormat[];
};

/**
 * Runs a report and saves what comes back, under legacy's name for its file, dated today. A
 * failure is a toast — nothing on the page changed. `running` is the format being produced, for
 * the buttons to wait on.
 */
export const useReportDownload = <C>({
  report,
  criteria,
  filename,
  notYetAvailable = [],
}: Options<C>) => {
  const { display } = useNotification();
  const running = report?.isPending ? (report.variables?.format ?? null) : null;

  const run = useCallback(
    (format: ReportFormat) => {
      if (!report || notYetAvailable.includes(format)) {
        display({
          kind: 'info',
          title: 'This report is not available yet',
          subtitle: `The criteria are valid. The ${format} is still being built.`,
          timeout: 6000,
        });
        return;
      }
      report.mutate(
        { criteria, format },
        {
          onSuccess: (file) => saveFile(file, reportFilename(filename, format)),
          onError: (error) => {
            display({
              kind: 'error',
              title: `The ${format} could not be produced`,
              subtitle: apiErrorMessage(
                error,
                'Try again, or narrow the criteria. Contact support if this continues.',
              ),
              timeout: 0,
            });
          },
        },
      );
    },
    [report, criteria, filename, notYetAvailable, display],
  );

  return { run, running };
};
