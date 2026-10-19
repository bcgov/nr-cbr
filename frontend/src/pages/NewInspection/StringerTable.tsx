import { Add, Edit, TrashCan } from '@carbon/icons-react';
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

import AnswerRow from './AnswerRow';
import { itemKey, type Answer, type Answers } from './inspectionRules';
import {
  asksDiameterOrWidth,
  requiredStringerCodes,
  stringerErrorsOf,
  stringersOf,
  type StringerValues,
} from './stringerRules';

import type { InspectionQuestion } from './inspectionForm';
import type { FC } from 'react';

import { holdFocus, useFieldErrors } from '@/hooks/useFieldErrors';

type Where = { spanId: number | null; pierId: number | null };

type Props = {
  subsectionCode: string;
  /** The questions asked of each stringer, in legacy's order. */
  questions: InspectionQuestion[];
  /** The span the stringers are under, or none for a wood box culvert. */
  where: Where;
  answers: Answers;
  onAnswer: (answer: Answer) => void;
  /** The id the table and its form are named from. */
  idPrefix: string;
};

/** A stringer's answer as the table reads it: a listed answer's description, or what was typed. */
const shown = (question: InspectionQuestion, answer: string | undefined) =>
  (answer && question.answers.find((option) => option.code === answer)?.description) ||
  answer ||
  '—';

const EMPTY = { answer: '', comment: '' };

/**
 * One stringer's form, under the table: its questions, the required ones marked, and legacy's
 * stringer rules (see `stringerErrorsOf`) — each error shown once its question is left, then live,
 * and every one at Save.
 */
const StringerForm: FC<{
  subsectionCode: string;
  questions: InspectionQuestion[];
  number: number;
  initial: StringerValues;
  /** The id of a question's boxes. */
  idOf: (questionCode: string) => string;
  idPrefix: string;
  onSave: (values: StringerValues) => void;
  onCancel: () => void;
}> = ({ subsectionCode, questions, number, initial, idOf, idPrefix, onSave, onCancel }) => {
  const [values, setValues] = useState<StringerValues>(initial);
  const fields = useFieldErrors<string>(stringerErrorsOf(subsectionCode, questions, values));
  const required = requiredStringerCodes(subsectionCode, questions);

  return (
    <section className="new-inspection__stringer-form" data-testid={`${idPrefix}-stringer-form`}>
      <h5 className="new-inspection__stringer-title">Stringer {number}</h5>
      {asksDiameterOrWidth(subsectionCode, questions) && (
        <p className="structure-detail__note structure-detail__note--first">
          Answer Diameter or Width X Depth, not both.
        </p>
      )}
      <div className="new-inspection__questions">
        {questions.map((question) => (
          <AnswerRow
            key={question.code}
            id={idOf(question.code)}
            question={question}
            value={values[question.code] ?? EMPTY}
            required={required.has(question.code)}
            message={fields.shown[question.code]}
            onLeave={() => fields.leave(question.code)}
            onChange={(change) => {
              setValues((current) => ({
                ...current,
                [question.code]: { ...(current[question.code] ?? EMPTY), ...change },
              }));
              fields.change(question.code);
            }}
          />
        ))}
      </div>
      <div className="structure-detail__edit-actions">
        <Button
          onMouseDown={holdFocus}
          kind="tertiary"
          size="sm"
          data-testid={`${idPrefix}-stringer-cancel`}
          onClick={onCancel}
        >
          Cancel
        </Button>
        <Button
          onMouseDown={holdFocus}
          kind="primary"
          size="sm"
          data-testid={`${idPrefix}-stringer-save`}
          onClick={() => {
            if (fields.submit()) onSave(values);
          }}
        >
          Save stringer
        </Button>
      </div>
    </section>
  );
};

/**
 * A span's stringers — legacy's stringer table and its one entry form: a row a stringer, its
 * answers in legacy's columns; "Add stringer" and each row's Edit open one stringer's form under
 * the table (see `StringerForm`), whose Save puts the answers in the inspection; Delete takes the stringer out.
 */
const StringerTable: FC<Props> = ({
  subsectionCode,
  questions,
  where,
  answers,
  onAnswer,
  idPrefix,
}) => {
  /** The stringer whose form is open — its number, and its stored answers — or none. */
  const [open, setOpen] = useState<{ number: number; values: StringerValues } | null>(null);
  const stringers = stringersOf(answers, subsectionCode, where);

  const at = (questionCode: string, stringerNumber: number) => ({
    subsectionCode,
    questionCode,
    ...where,
    stringerNumber,
  });
  const valueOf = (questionCode: string, stringerNumber: number) =>
    answers[itemKey(at(questionCode, stringerNumber))];

  const edit = (number: number) => {
    setOpen({
      number,
      values: Object.fromEntries(
        questions.map((question) => {
          const stored = valueOf(question.code, number);
          return [question.code, { answer: stored?.answer ?? '', comment: stored?.comment ?? '' }];
        }),
      ),
    });
  };
  const add = () => {
    setOpen({ number: Math.max(0, ...stringers) + 1, values: {} });
  };
  /** Writes every question of the stringer — blank ones too, which takes them out. */
  const write = (number: number, values: StringerValues) => {
    for (const question of questions) {
      onAnswer({
        ...at(question.code, number),
        answer: values[question.code]?.answer ?? '',
        comment: values[question.code]?.comment ?? '',
      });
    }
  };
  const remove = (number: number) => {
    write(number, {});
    if (open?.number === number) setOpen(null);
  };

  return (
    <div className="new-inspection__stringers" data-testid={`${idPrefix}-stringers`}>
      <div className="new-inspection__stringer-bar">
        <h4 className="new-inspection__stringer-title">Stringers</h4>
        <Button
          kind="tertiary"
          size="sm"
          renderIcon={Add}
          disabled={open !== null}
          data-testid={`${idPrefix}-stringer-add`}
          onClick={add}
        >
          Add stringer
        </Button>
      </div>
      {stringers.length === 0 ? (
        <p className="structure-detail__empty">No stringers yet.</p>
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
                      {shown(question, valueOf(question.code, number)?.answer)}
                    </TableCell>
                  ))}
                  <TableCell className="structure-detail__nowrap">
                    <Button
                      kind="ghost"
                      size="sm"
                      hasIconOnly
                      renderIcon={Edit}
                      iconDescription={`Edit stringer ${number}`}
                      disabled={open !== null}
                      data-testid={`${idPrefix}-stringer-edit-${number}`}
                      onClick={() => edit(number)}
                    />
                    <Button
                      kind="ghost"
                      size="sm"
                      hasIconOnly
                      renderIcon={TrashCan}
                      iconDescription={`Delete stringer ${number}`}
                      disabled={open !== null}
                      data-testid={`${idPrefix}-stringer-delete-${number}`}
                      onClick={() => remove(number)}
                    />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      {open && (
        <StringerForm
          key={open.number}
          subsectionCode={subsectionCode}
          questions={questions}
          number={open.number}
          initial={open.values}
          idOf={(questionCode) => itemKey(at(questionCode, open.number)).replaceAll('.', '-')}
          idPrefix={idPrefix}
          onSave={(values) => {
            write(open.number, values);
            setOpen(null);
          }}
          onCancel={() => setOpen(null)}
        />
      )}
    </div>
  );
};

export default StringerTable;
