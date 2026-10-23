import type { CancelablePromise } from '@/config/api/CancelablePromise';
import type { InspectionsConductedCriteria } from '@/pages/Reports/InspectionsConducted/types';
import type { InspectionsDueCriteria } from '@/pages/Reports/InspectionsDue/types';
import type { MonitorsSummaryCriteria } from '@/pages/Reports/MonitorsSummary/types';
import type { RepairsSummaryCriteria } from '@/pages/Reports/RepairsSummary/types';
import type { ReplacementStructuresSummaryCriteria } from '@/pages/Reports/ReplacementStructuresSummary/types';
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
    // The KMZ has a mapping of its own: the format is no other report's.
    if (format === 'KMZ') {
      return this.doRequest<Blob>(this.config, {
        method: 'GET',
        url: '/v1/reports/structure-inventory-detailed/kmz',
        query: populated(criteria),
        responseType: 'blob',
      });
    }
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

  /**
   * Monitors Summary (legacy CBR011): the monitoring items recorded against structures, a block
   * per structure grouped by inspection, as a PDF in legacy's layout. A GET, the criteria as query
   * parameters.
   */
  monitorsSummary(
    criteria: MonitorsSummaryCriteria,
    format: ReportFormat,
  ): CancelablePromise<Blob> {
    return this.doRequest<Blob>(this.config, {
      method: 'GET',
      url: '/v1/reports/monitors-summary',
      query: { ...populated(criteria), format },
      responseType: 'blob',
    });
  }

  /**
   * Inspections Conducted – Summary (legacy CBR002): a line per inspection, as a PDF in legacy's
   * landscape layout or a CSV of its columns. A GET, the criteria as query parameters; 400 with `fieldErrors` for an
   * Inspection Date that is not a month.
   */
  inspectionsConductedSummary(
    criteria: InspectionsConductedCriteria,
    format: ReportFormat,
  ): CancelablePromise<Blob> {
    return this.doRequest<Blob>(this.config, {
      method: 'GET',
      url: '/v1/reports/inspections-conducted-summary',
      query: { ...populated(criteria), format },
      responseType: 'blob',
    });
  }

  /**
   * Inspections Conducted – Detail (legacy CBR001): each inspection in full, a page of its own, as
   * a PDF in legacy's layout; or a CSV, a row per inspection item. A GET, the criteria as query parameters; 400 with `fieldErrors` for
   * an Inspection Date that is not a month.
   */
  inspectionsConductedDetail(
    criteria: InspectionsConductedCriteria,
    format: ReportFormat,
  ): CancelablePromise<Blob> {
    return this.doRequest<Blob>(this.config, {
      method: 'GET',
      url: '/v1/reports/inspections-conducted-detail',
      query: { ...populated(criteria), format },
      responseType: 'blob',
    });
  }

  /**
   * Inspections Due – Summary (legacy CBR004): a line per structure under its forest district, as
   * a PDF in legacy's landscape layout. A GET, the criteria as query parameters; 400 with
   * `fieldErrors` for a Next Planned Inspection Date that is not yyyy/mm/dd.
   */
  inspectionsDueSummary(
    criteria: InspectionsDueCriteria,
    format: ReportFormat,
  ): CancelablePromise<Blob> {
    return this.doRequest<Blob>(this.config, {
      method: 'GET',
      url: '/v1/reports/inspections-due-summary',
      query: { ...populated(criteria), format },
      responseType: 'blob',
    });
  }

  /**
   * Inspections Due – Detailed (legacy CBR003): a box per structure under its forest district, as
   * a PDF on legacy's portrait page. A GET, the criteria as query parameters; 400 with
   * `fieldErrors` for a Next Planned Inspection Date that is not yyyy/mm/dd.
   */
  inspectionsDueDetailed(
    criteria: InspectionsDueCriteria,
    format: ReportFormat,
  ): CancelablePromise<Blob> {
    return this.doRequest<Blob>(this.config, {
      method: 'GET',
      url: '/v1/reports/inspections-due-detailed',
      query: { ...populated(criteria), format },
      responseType: 'blob',
    });
  }

  /**
   * Inspections – Blank Report (legacy CBR010): a blank routine inspection form per structure, as
   * a PDF. A GET, the criteria as query parameters; 400 with `fieldErrors` for a Next Planned
   * Inspection Date that is not yyyy/mm/dd.
   */
  inspectionsBlank(
    criteria: InspectionsDueCriteria,
    format: ReportFormat,
  ): CancelablePromise<Blob> {
    return this.doRequest<Blob>(this.config, {
      method: 'GET',
      url: '/v1/reports/inspections-blank',
      query: { ...populated(criteria), format },
      responseType: 'blob',
    });
  }

  /** Replacement Structures Summary (legacy CBR009), as a PDF or a CSV. */
  replacementStructuresSummary(
    criteria: ReplacementStructuresSummaryCriteria,
    format: ReportFormat,
  ): CancelablePromise<Blob> {
    return this.doRequest<Blob>(this.config, {
      method: 'GET',
      url: '/v1/reports/replacement-structures-summary',
      query: { ...populated(criteria), format },
      responseType: 'blob',
    });
  }
}
