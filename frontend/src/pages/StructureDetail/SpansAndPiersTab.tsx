import { Column as PierIcon, Ruler } from '@carbon/icons-react';
import {
  InlineNotification,
  SkeletonText,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react';

import Card from './Card';
import { describe, number } from './format';

import type { FC } from 'react';

import { useStructureSpansAndPiers } from '@/hooks/useStructureSearch';
import { apiErrorMessage } from '@/utils/apiError';

type Props = {
  structureId: string;
  /** True once the tab has been opened; nothing is fetched before. */
  opened: boolean;
};

/**
 * Legacy's Spans & Piers tab (`pierSpanTab.jsp`), shown for bridges only: the spans by span
 * number, then the piers by pier number with their type. Read-only for now; legacy's Add, Update
 * and Delete (Level 2) come with the page's editing.
 */
const SpansAndPiersTab: FC<Props> = ({ structureId, opened }) => {
  const loaded = useStructureSpansAndPiers(structureId, opened);

  if (loaded.isError) {
    return (
      <InlineNotification
        kind="error"
        lowContrast
        hideCloseButton
        title="Spans and piers could not be loaded"
        subtitle={apiErrorMessage(loaded.error, 'Try again in a moment.')}
        data-testid="structure-spans-piers-error"
      />
    );
  }

  if (!loaded.data) {
    // The test id on a wrapper: Carbon repeats a paragraph skeleton's props on every line.
    return (
      <div data-testid="structure-spans-piers-loading">
        <SkeletonText paragraph lineCount={6} />
      </div>
    );
  }

  const { spans, piers } = loaded.data;

  return (
    <div className="structure-detail__tab-panel" data-testid="structure-spans-piers-tab">
      <Card title="Spans" icon={Ruler} testId="structure-section-spans">
        <p className="structure-detail__note structure-detail__note--first">
          Span # is numbered Left Bank to Right Bank (Looking Downstream).
        </p>
        {spans.length === 0 ? (
          <p className="structure-detail__empty">No spans.</p>
        ) : (
          <Table size="md" useZebraStyles aria-label="Spans">
            <TableHead>
              <TableRow>
                <TableHeader>Span #</TableHeader>
                <TableHeader>Length (metres)</TableHeader>
              </TableRow>
            </TableHead>
            <TableBody>
              {spans.map((span) => (
                <TableRow key={span.id}>
                  <TableCell>{number(span.number)}</TableCell>
                  <TableCell>{number(span.lengthMetres)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>

      <Card title="Piers" icon={PierIcon} testId="structure-section-piers">
        {piers.length === 0 ? (
          <p className="structure-detail__empty">No piers.</p>
        ) : (
          <Table size="md" useZebraStyles aria-label="Piers">
            <TableHead>
              <TableRow>
                <TableHeader>Pier #</TableHeader>
                <TableHeader>Type</TableHeader>
              </TableRow>
            </TableHead>
            <TableBody>
              {piers.map((pier) => (
                <TableRow key={pier.id}>
                  <TableCell>{number(pier.number)}</TableCell>
                  <TableCell>{describe(pier.type)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>
    </div>
  );
};

export default SpansAndPiersTab;
