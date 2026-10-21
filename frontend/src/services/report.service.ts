import type { CancelablePromise } from '@/config/api/CancelablePromise';
import type { RepairsSummaryCriteria } from '@/pages/Reports/RepairsSummary/types';
import type { StructureInventoryDetailedCriteria } from '@/pages/Reports/StructureInventoryDetailed/types';
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

  /**
   * Structure Inventory – Detailed (legacy CBR007): a PDF in legacy's layout — a block of fields
   * per structure — or a CSV of legacy's fuller export. A GET, the criteria as query parameters.
   */
  structureInventoryDetailed(
    criteria: StructureInventoryDetailedCriteria,
    format: ReportFormat,
  ): CancelablePromise<Blob> {
    return this.doRequest<Blob>(this.config, {
      method: 'GET',
      url: '/v1/reports/structure-inventory-detailed',
      query: { ...populated(criteria), format },
      responseType: 'blob',
    });
  }

  /**
   * Structure Detail Report (legacy CBR006): the full record of one structure, as a PDF in legacy's
   * layout or a CSV of the same fields. 404 when the structure no longer exists or has been
   * archived.
   */
  structureDetail(structureId: string, format: ReportFormat): CancelablePromise<Blob> {
    return this.doRequest<Blob>(this.config, {
      method: 'GET',
      url: `/v1/reports/structure-detail/${encodeURIComponent(structureId)}`,
      query: { format },
      responseType: 'blob',
    });
  }

  /**
   * Repairs Summary (legacy CBR005): the repairs recorded against structures, a page per structure
   * grouped by inspection, as a PDF in legacy's layout. A GET, the criteria as query parameters.
   */
  repairsSummary(criteria: RepairsSummaryCriteria, format: ReportFormat): CancelablePromise<Blob> {
    return this.doRequest<Blob>(this.config, {
      method: 'GET',
      url: '/v1/reports/repairs-summary',
      query: { ...populated(criteria), format },
      responseType: 'blob',
    });
  }
}
