import { Add, Column as PierIcon, Edit, Ruler, TrashCan } from '@carbon/icons-react';
import {
  Button,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react';
import { useState } from 'react';

import Card from '@/pages/StructureDetail/Card';
import PierDialog from '@/pages/StructureDetail/PierDialog';
import SpanDialog from '@/pages/StructureDetail/SpanDialog';

import type { BridgePier, BridgeSpan } from '@/pages/StructureDetail/spansAndPiersResponse';
import type { FC } from 'react';

import { usePierTypeCodes } from '@/hooks/useConfiguration';
import { describe, number } from '@/pages/StructureDetail/format';

type Props = {
  spans: BridgeSpan[];
  piers: BridgePier[];
  onSpans: (spans: BridgeSpan[]) => void;
  onPiers: (piers: BridgePier[]) => void;
  /** What is wrong with the lists as a whole — too few spans, or as many piers as spans. */
  spansError?: string;
  piersError?: string;
};

/** Edit and Delete for one row, as the Spans & Piers tab's. */
const RowActions: FC<{
  kind: 'span' | 'pier';
  item: BridgeSpan | BridgePier;
  onEdit: () => void;
  onDelete: () => void;
}> = ({ kind, item, onEdit, onDelete }) => (
  <TableCell className="structure-detail__nowrap">
    <Button
      kind="ghost"
      size="sm"
      renderIcon={Edit}
      data-testid={`add-structure-${kind}-edit-${item.id}`}
      onClick={onEdit}
    >
      Edit{' '}
      <span className="cds--visually-hidden">
        {kind} {number(item.number)}
      </span>
    </Button>
    <Button
      kind="danger--ghost"
      size="sm"
      renderIcon={TrashCan}
      data-testid={`add-structure-${kind}-delete-${item.id}`}
      onClick={onDelete}
    >
      Delete{' '}
      <span className="cds--visually-hidden">
        {kind} {number(item.number)}
      </span>
    </Button>
  </TableCell>
);

let nextId = 0;
/** An id for a row not yet saved, unique on the page. */
const newId = () => {
  nextId += 1;
  return `new-${nextId}`;
};

const byNumber = <T extends { number: number | null }>(rows: T[]) =>
  [...rows].sort((a, b) => (a.number ?? 0) - (b.number ?? 0));

/**
 * A new bridge's spans and piers — the Spans & Piers tab's tables and dialogs, kept on the page
 * until the structure is saved, when all of them are written with it. At least one span is needed,
 * and more spans than piers, as legacy.
 */
const SpansAndPiersSection: FC<Props> = ({
  spans,
  piers,
  onSpans,
  onPiers,
  spansError,
  piersError,
}) => {
  const pierTypes = usePierTypeCodes();
  const [editingSpan, setEditingSpan] = useState<BridgeSpan | 'new' | null>(null);
  const [editingPier, setEditingPier] = useState<BridgePier | 'new' | null>(null);

  const typeOf = (code: string) => ({
    code,
    description: pierTypes.data?.find((option) => option.code === code)?.description ?? code,
  });

  return (
    <>
      <Card
        title="Spans"
        icon={Ruler}
        testId="add-structure-spans"
        action={
          <Button
            kind="tertiary"
            size="sm"
            renderIcon={Add}
            data-testid="add-structure-span-add"
            onClick={() => setEditingSpan('new')}
          >
            Add span
          </Button>
        }
      >
        <p className="structure-detail__note structure-detail__note--first">
          Span # is numbered Left Bank to Right Bank (Looking Downstream).
        </p>
        {spansError && (
          <p
            className="structure-detail__upload-error"
            role="alert"
            data-testid="add-structure-spans-error"
          >
            {spansError}
          </p>
        )}
        {spans.length === 0 ? (
          <p className="structure-detail__empty">No spans yet. A bridge needs at least one.</p>
        ) : (
          <Table
            size="md"
            useZebraStyles
            aria-label="Spans"
            className="structure-detail__bridge-table"
          >
            <TableHead>
              <TableRow>
                <TableHeader>Span #</TableHeader>
                <TableHeader>Length (metres)</TableHeader>
                <TableHeader>Actions</TableHeader>
              </TableRow>
            </TableHead>
            <TableBody>
              {spans.map((span) => (
                <TableRow key={span.id} data-testid={`add-structure-span-${span.id}`}>
                  <TableCell>{number(span.number)}</TableCell>
                  <TableCell>{number(span.lengthMetres)}</TableCell>
                  <RowActions
                    kind="span"
                    item={span}
                    onEdit={() => setEditingSpan(span)}
                    onDelete={() => onSpans(spans.filter((each) => each !== span))}
                  />
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>
      <hr className="add-structure__divider" />

      <Card
        title="Piers"
        icon={PierIcon}
        testId="add-structure-piers"
        action={
          <Button
            kind="tertiary"
            size="sm"
            renderIcon={Add}
            data-testid="add-structure-pier-add"
            onClick={() => setEditingPier('new')}
          >
            Add pier
          </Button>
        }
      >
        {piersError && (
          <p
            className="structure-detail__upload-error"
            role="alert"
            data-testid="add-structure-piers-error"
          >
            {piersError}
          </p>
        )}
        {piers.length === 0 ? (
          <p className="structure-detail__empty">No piers.</p>
        ) : (
          <Table
            size="md"
            useZebraStyles
            aria-label="Piers"
            className="structure-detail__bridge-table"
          >
            <TableHead>
              <TableRow>
                <TableHeader>Pier #</TableHeader>
                <TableHeader>Type</TableHeader>
                <TableHeader>Actions</TableHeader>
              </TableRow>
            </TableHead>
            <TableBody>
              {piers.map((pier) => (
                <TableRow key={pier.id} data-testid={`add-structure-pier-${pier.id}`}>
                  <TableCell>{number(pier.number)}</TableCell>
                  <TableCell>{describe(pier.type)}</TableCell>
                  <RowActions
                    kind="pier"
                    item={pier}
                    onEdit={() => setEditingPier(pier)}
                    onDelete={() => onPiers(piers.filter((each) => each !== pier))}
                  />
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>

      {editingSpan !== null && (
        <SpanDialog
          structureId=""
          span={editingSpan === 'new' ? null : editingSpan}
          spans={spans}
          hasBridgeRecord
          onClose={() => setEditingSpan(null)}
          onSubmit={(request) => {
            const span: BridgeSpan = {
              id: editingSpan === 'new' ? newId() : editingSpan.id,
              number: request.number,
              lengthMetres: request.lengthMetres,
            };
            onSpans(
              byNumber(
                editingSpan === 'new'
                  ? [...spans, span]
                  : spans.map((each) => (each === editingSpan ? span : each)),
              ),
            );
          }}
        />
      )}
      {editingPier !== null && (
        <PierDialog
          structureId=""
          pier={editingPier === 'new' ? null : editingPier}
          piers={piers}
          spanCount={spans.length}
          hasBridgeRecord
          onClose={() => setEditingPier(null)}
          onSubmit={(request) => {
            const pier: BridgePier = {
              id: editingPier === 'new' ? newId() : editingPier.id,
              number: request.number,
              type: typeOf(request.typeCode),
            };
            onPiers(
              byNumber(
                editingPier === 'new'
                  ? [...piers, pier]
                  : piers.map((each) => (each === editingPier ? pier : each)),
              ),
            );
          }}
        />
      )}
    </>
  );
};

export default SpansAndPiersSection;
