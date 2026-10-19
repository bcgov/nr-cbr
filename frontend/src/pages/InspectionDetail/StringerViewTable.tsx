import { View } from '@carbon/icons-react';
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

import type { InspectionItem } from './inspectionDetail';
import type { InspectionQuestion } from '@/pages/NewInspection/inspectionForm';
import type { FC, ReactNode } from 'react';

type Props = {
  questions: InspectionQuestion[];
  stringers: number[];
  /** A stringer's answer to a question, if it has one. */
  itemOf: (questionCode: string, stringerNumber: number) => InspectionItem | undefined;
  /** One stringer's questions, answers and comments — what View opens. */
  details: (stringerNumber: number) => ReactNode;
  idPrefix: string;
};

/** The answer as the table reads it: a listed answer's description, or what was typed. */
const shown = (question: InspectionQuestion, item: InspectionItem | undefined) =>
  (item?.answer && question.answers.find((option) => option.code === item.answer)?.description) ||
  item?.answer ||
  '—';

/**
 * A span's recorded stringers, read — the same table as editing has: a row a stringer, its answers
 * in legacy's columns; each row's View opens that stringer's answers and comments under the table,
 * one at a time.
 */
const StringerViewTable: FC<Props> = ({ questions, stringers, itemOf, details, idPrefix }) => {
  const [open, setOpen] = useState<number | null>(null);
  return (
    <div className="new-inspection__stringers" data-testid={`${idPrefix}-stringers`}>
      <div className="new-inspection__stringer-bar">
        <h4 className="new-inspection__stringer-title">Stringers</h4>
      </div>
      {stringers.length === 0 ? (
        <p className="structure-detail__empty">No stringers recorded.</p>
      ) : (
        <div className="structure-detail__table-scroll">
          <Table size="sm" useZebraStyles aria-label="Stringers">
            <TableHead>
              <TableRow>
                <TableHeader>Stringer</TableHeader>
                {questions.map((question) => (
                  <TableHeader key={question.code}>{question.description}</TableHeader>
                ))}
                <TableHeader>
                  <span className="cds--visually-hidden">Actions</span>
                </TableHeader>
              </TableRow>
            </TableHead>
            <TableBody>
              {stringers.map((number) => (
                <TableRow key={number} data-testid={`${idPrefix}-stringer-${number}`}>
                  <TableCell>{number}</TableCell>
                  {questions.map((question) => (
                    <TableCell key={question.code}>
                      {shown(question, itemOf(question.code, number))}
                    </TableCell>
                  ))}
                  <TableCell className="structure-detail__nowrap">
                    <Button
                      kind="ghost"
                      size="sm"
                      renderIcon={View}
                      data-testid={`${idPrefix}-stringer-view-${number}`}
                      onClick={() => setOpen(open === number ? null : number)}
                    >
                      {open === number ? 'Hide' : 'View'}
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
      {open !== null && (
        <section
          className="new-inspection__stringer-form"
          data-testid={`${idPrefix}-stringer-details`}
        >
          <h5 className="new-inspection__stringer-title">Stringer {open}</h5>
          {details(open)}
        </section>
      )}
    </div>
  );
};

export default StringerViewTable;
