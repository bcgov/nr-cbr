import type { CancelablePromise } from '@/config/api/CancelablePromise';
import type {
  ReportFormat,
  StructureInventorySummaryCriteria,
} from '@/pages/Reports/StructureInventorySummary/types';

import { HttpClient } from '@/config/api/types';

/** `maintainerLabel` is what the maintainer lookup displays; the server filters on the number. */
const DISPLAY_ONLY: ReadonlySet<string> = new Set(['maintainerLabel']);

/** The criteria as query parameters: only what was set, text trimmed, a toggle only when on. */
const populated = (criteria: object): Record<string, string | boolean> => {
  const query: Record<string, string | boolean> = {};
  for (const [field, value] of Object.entries(criteria)) {
    if (DISPLAY_ONLY.has(field)) {
      continue;
    }
    if (typeof value === 'string' && value.trim() !== '') {
      query[field] = value.trim();
    } else if (value === true) {
      query[field] = true;
    }
  }
  return query;
};

/** The Reports menu's reports, each produced on the server as a file to download. */
export class ReportService extends HttpClient {
  /**
   * Structure Inventory – Summary (legacy CBR008), as a PDF in legacy's layout or a CSV of the same
   * columns. A GET, the criteria as query parameters; 400 with `fieldErrors` for a malformed bound.
   */
  structureInventorySummary(
    criteria: StructureInventorySummaryCriteria,
    format: ReportFormat,
  ): CancelablePromise<Blob> {
    return this.doRequest<Blob>(this.config, {
      method: 'GET',
      url: '/v1/reports/structure-inventory-summary',
      query: { ...populated(criteria), format },
      responseType: 'blob',
    });
  }
}
