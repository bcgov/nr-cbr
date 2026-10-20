import { Download } from '@carbon/icons-react';
import {
  DataTableSkeleton,
  InlineLoading,
  OverflowMenu,
  OverflowMenuItem,
  Pagination,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react';

import ExternalLink from '@/components/core/ExternalLink';

import type { ReportFormat } from './types';
import type { StructureSearchResult } from '@/pages/StructureSearch/types';
import type { FC } from 'react';

/** Legacy's results columns, then the report's buttons. */
const COLUMNS = [
  'Structure #',
  'Site #',
  'District',
  'Forest Service Road',
  'KM',
  'Crossing Name',
  'Type/Class',
  'Project File ID#-Br.',
] as const;

const FORMATS: readonly ReportFormat[] = ['CSV', 'PDF'];

type Props = {
  results: StructureSearchResult[];
  totalItems: number;
  loading: boolean;
  page: number;
  pageSize: number;
  onPageChange: (next: { page: number; pageSize: number }) => void;
  /** Produces the report of this structure, as this format. */
  onRun: (structure: StructureSearchResult, format: ReportFormat) => void;
  /** The structure and format being produced, while one is: every menu waits. */
  running: { structureId: string; format: ReportFormat } | null;
};

/**
 * The structures the search found — legacy's results table, paged — each with a Download menu of
 * the formats of its Structure Detail Report. Legacy linked the structure's name to its report;
 * here the name opens the structure's page in a new tab, as on Structure Search, and the menu makes
 * the report.
 */
const StructureDetailResults: FC<Props> = ({
  results,
  totalItems,
  loading,
  page,
  pageSize,
  onPageChange,
  onRun,
  running,
}) => {
  if (loading) {
    return (
      <DataTableSkeleton
        role="progressbar"
        aria-label="Searching"
        data-testid="structure-detail-report-loading"
        columnCount={COLUMNS.length + 1}
        rowCount={5}
        showHeader={false}
        showToolbar={false}
      />
    );
  }

  return (
    <>
      <TableContainer
        className="bordered-table report-criteria__results"
        data-testid="structure-detail-report-results"
      >
        <div className="cds--data-table-header">
          <h4 className="cds--data-table-header__title" id="structure-detail-report-results-title">
            Structures — {totalItems} match{totalItems === 1 ? '' : 'es'}
          </h4>
        </div>
        <Table useZebraStyles size="lg" aria-labelledby="structure-detail-report-results-title">
          <TableHead>
            <TableRow>
              {COLUMNS.map((label) => (
                <TableHeader key={label}>{label}</TableHeader>
              ))}
              <TableHeader>Report</TableHeader>
            </TableRow>
          </TableHead>
          <TableBody>
            {results.length === 0 && (
              <TableRow>
                <TableCell colSpan={COLUMNS.length + 1}>No structures found.</TableCell>
              </TableRow>
            )}
            {results.map((row) => (
              <TableRow key={row.id} data-testid={`structure-detail-report-row-${row.id}`}>
                <TableCell>
                  <ExternalLink to={`/inventory/structure/${row.id}`}>
                    {row.structureName || row.id}
                  </ExternalLink>
                </TableCell>
                <TableCell>{row.siteId}</TableCell>
                <TableCell>
                  {[row.orgUnitCode, row.orgUnitName].filter(Boolean).join(' — ')}
                </TableCell>
                <TableCell>{row.forestServiceRoad}</TableCell>
                <TableCell className="report-criteria__numeric">{row.kilometres}</TableCell>
                <TableCell>{row.crossingName}</TableCell>
                <TableCell>{row.structureTypeClass}</TableCell>
                <TableCell>
                  {[row.forestFileId, row.roadSectionId].filter(Boolean).join('-')}
                </TableCell>
                <TableCell>
                  {running?.structureId === row.id ? (
                    <InlineLoading
                      description={`Producing ${running.format}…`}
                      data-testid={`structure-detail-report-producing-${row.id}`}
                    />
                  ) : (
                    <OverflowMenu
                      renderIcon={Download}
                      size="sm"
                      flipped
                      className="report-criteria__download"
                      iconDescription={`Download ${row.structureName || row.id}`}
                      disabled={running !== null}
                      data-testid={`structure-detail-report-download-${row.id}`}
                    >
                      {FORMATS.map((format) => (
                        <OverflowMenuItem
                          key={format}
                          itemText={format}
                          onClick={() => onRun(row, format)}
                          data-testid={`structure-detail-report-run-${format}-${row.id}`}
                        />
                      ))}
                    </OverflowMenu>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>

      <Pagination
        data-testid="structure-detail-report-pagination"
        page={page}
        pageSize={pageSize}
        pageSizes={[20, 50, 100]}
        totalItems={totalItems}
        onChange={({ page: nextPage, pageSize: nextPageSize }) =>
          onPageChange({ page: nextPage, pageSize: nextPageSize })
        }
      />
    </>
  );
};

export default StructureDetailResults;
