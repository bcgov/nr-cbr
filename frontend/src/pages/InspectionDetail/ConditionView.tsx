import { Edit, Report } from '@carbon/icons-react';
import {
  Button,
  SkeletonText,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react';
import { useState } from 'react';

import Card from '@/pages/StructureDetail/Card';

import ConditionEditor from './ConditionEditor';
import StringerViewTable from './StringerViewTable';

import type { InspectionItem } from './inspectionDetail';
import type {
  BridgePart,
  InspectionFormResponse,
  InspectionQuestion,
  InspectionSubsection,
} from '@/pages/NewInspection/inspectionForm';
import type { FC, ReactNode } from 'react';

import '@/pages/NewInspection/newInspection.scss';

type Props = {
  form: InspectionFormResponse | undefined;
  loading: boolean;
  items: InspectionItem[];
  inspectionId: string;
  /** Whether the user may edit the cards — Level 0 and up, until Reviewed or Accepted. */
  editable: boolean;
};

/** Where in the structure an answer was given. */
type Where = { spanId: string | null; pierId: string | null; stringerNumber: number | null };

/** The key an answer is found under: its group, question, span, pier and stringer. */
const keyOf = (subsection: string, question: string, where: Where) =>
  [subsection, question, where.spanId, where.pierId, where.stringerNumber].join('|');

const partLabel = (kind: string, part: BridgePart) => `${kind} ${part.number ?? part.id}`;

/** The answer as the user reads it: a listed answer's description, or what was typed. */
const answerText = (question: InspectionQuestion, item: InspectionItem | undefined) => {
  if (!item?.answer) return null;
  return question.answers.find((option) => option.code === item.answer)?.description ?? item.answer;
};

/** A table of questions, answers and comments, a row each. */
const AnswerTable: FC<{ label: string; children: ReactNode }> = ({ label, children }) => (
  <div className="structure-detail__table-scroll">
    <Table size="sm" useZebraStyles aria-label={label}>
      <TableHead>
        <TableRow>
          <TableHeader>Question</TableHeader>
          <TableHeader>Answer</TableHeader>
          <TableHeader>Comments</TableHeader>
        </TableRow>
      </TableHead>
      <TableBody>{children}</TableBody>
    </Table>
  </div>
);

/** One question: its wording, its answer and its comment — "—" where none was given. */
const QuestionRow: FC<{ question: InspectionQuestion; item: InspectionItem | undefined }> = ({
  question,
  item,
}) => (
  <TableRow data-testid={`answer-${question.code}`}>
    <TableCell>{question.description}</TableCell>
    <TableCell>{answerText(question, item) ?? '—'}</TableCell>
    <TableCell className="inspection-detail__comment">{item?.comment || ''}</TableCell>
  </TableRow>
);

/**
 * One question group as a card, read only — as Add Inspection lays it out: a block per span or
 * pier under its heading, and a span's stringers as a table, each row's View opening its answers.
 */
const SubsectionCard: FC<{
  subsection: InspectionSubsection;
  spans: BridgePart[];
  piers: BridgePart[];
  answers: Map<string, InspectionItem>;
  stringersOf: (spanId: string | null) => number[];
  /** The card's Edit, when the user may edit it. */
  action?: ReactNode;
}> = ({ subsection, spans, piers, answers, stringersOf, action }) => {
  const perSpan = subsection.scope === 'SPAN';
  const perPier = subsection.scope === 'PIER';
  const groupQuestions = subsection.questions.filter((question) => !question.perStringer);
  const stringerQuestions = subsection.questions.filter((question) => question.perStringer);
  const testId = `condition-${subsection.code}`;

  const rows = (questions: InspectionQuestion[], where: Where) => (
    <AnswerTable label={subsection.description}>
      {questions.map((question) => (
        <QuestionRow
          key={question.code}
          question={question}
          item={answers.get(keyOf(subsection.code, question.code, where))}
        />
      ))}
    </AnswerTable>
  );

  const block = (where: Where, idPrefix: string) => (
    <>
      {groupQuestions.length > 0 && rows(groupQuestions, where)}
      {stringerQuestions.length > 0 && (
        <StringerViewTable
          questions={stringerQuestions}
          stringers={stringersOf(where.spanId)}
          itemOf={(questionCode, stringerNumber) =>
            answers.get(keyOf(subsection.code, questionCode, { ...where, stringerNumber }))
          }
          details={(stringerNumber) => rows(stringerQuestions, { ...where, stringerNumber })}
          idPrefix={idPrefix}
        />
      )}
    </>
  );

  const parts = perSpan ? spans : piers;
  const kind = perSpan ? 'Span' : 'Pier';
  let body: ReactNode;
  if (!perSpan && !perPier) {
    body = block({ spanId: null, pierId: null, stringerNumber: null }, testId);
  } else if (parts.length === 0) {
    body = (
      <p className="structure-detail__empty">
        {perSpan ? 'This bridge has no spans recorded.' : 'This bridge has no piers recorded.'}
      </p>
    );
  } else {
    body = parts.map((part) => (
      <section
        key={part.id}
        className="new-inspection__part"
        data-testid={`${testId}-${kind.toLowerCase()}-${part.id}`}
      >
        <h3 className="new-inspection__part-title">{partLabel(kind, part)}</h3>
        {block(
          {
            spanId: perSpan ? part.id : null,
            pierId: perPier ? part.id : null,
            stringerNumber: null,
          },
          `${testId}-${kind.toLowerCase()}-${part.id}`,
        )}
      </section>
    ));
  }

  return (
    <Card title={subsection.description} icon={Report} testId={testId} action={action}>
      {body}
    </Card>
  );
};

/** Whether a card shows the answer: its question is asked, at a span, pier and stringer it has. */
const isAsked = (item: InspectionItem, form: InspectionFormResponse): boolean => {
  const subsection = form.subsections.find((each) => each.code === item.subsectionCode);
  const question = subsection?.questions.find((each) => each.code === item.questionCode);
  if (!subsection || !question) return false;
  const spanOk =
    subsection.scope === 'SPAN'
      ? form.spans.some((span) => span.id === item.spanId)
      : item.spanId === null;
  const pierOk =
    subsection.scope === 'PIER'
      ? form.piers.some((pier) => pier.id === item.pierId)
      : item.pierId === null;
  return spanOk && pierOk && question.perStringer === (item.stringerNumber !== null);
};

/**
 * The Condition tab, read only: every question the structure is asked, in legacy's order, each with
 * its answer and comment or "—". An answer to a question no longer asked — retired since, or the
 * structure changed — is listed last, so nothing recorded is hidden.
 */
const ConditionView: FC<Props> = ({ form, loading, items, inspectionId, editable }) => {
  /** The card being edited — one at a time — or none. */
  const [editing, setEditing] = useState<string | null>(null);
  const empty = (content: ReactNode) => (
    <Card title="Condition" icon={Report} testId="condition-empty">
      {content}
    </Card>
  );
  if (loading) return empty(<SkeletonText paragraph lineCount={6} />);
  if (!form) {
    return empty(<p className="structure-detail__empty">The questions could not be loaded.</p>);
  }

  const answers = new Map(
    items.map((item) => [keyOf(item.subsectionCode, item.questionCode, item), item]),
  );
  const shown = new Set(
    items
      .filter((item) => isAsked(item, form))
      .map((item) => keyOf(item.subsectionCode, item.questionCode, item)),
  );
  const stringersBy = (subsection: string) => (spanId: string | null) =>
    [
      ...new Set(
        items
          .filter(
            (item) =>
              item.subsectionCode === subsection &&
              item.spanId === spanId &&
              item.stringerNumber !== null,
          )
          .map((item) => item.stringerNumber as number),
      ),
    ].sort((a, b) => a - b);
  const others = items.filter(
    (item) => !shown.has(keyOf(item.subsectionCode, item.questionCode, item)),
  );

  if (form.subsections.length === 0 && others.length === 0) {
    return empty(
      <p className="structure-detail__empty">This structure has no inspection questions.</p>,
    );
  }

  return (
    <>
      {form.subsections.map((subsection) =>
        editing === subsection.code ? (
          <ConditionEditor
            key={subsection.code}
            inspectionId={inspectionId}
            subsection={subsection}
            form={form}
            items={items}
            onDone={() => setEditing(null)}
          />
        ) : (
          <SubsectionCard
            key={subsection.code}
            subsection={subsection}
            spans={form.spans}
            piers={form.piers}
            answers={answers}
            stringersOf={stringersBy(subsection.code)}
            action={
              editable &&
              editing === null && (
                <Button
                  kind="tertiary"
                  size="sm"
                  renderIcon={Edit}
                  data-testid={`condition-${subsection.code}-edit`}
                  onClick={() => setEditing(subsection.code)}
                >
                  {`Edit ${subsection.description.toLowerCase()}`}
                </Button>
              )
            }
          />
        ),
      )}
      {others.length > 0 && (
        <Card title="Other recorded answers" icon={Report} testId="condition-other">
          <p className="structure-detail__note structure-detail__note--first">
            Answers to questions this structure is no longer asked, by their codes.
          </p>
          <AnswerTable label="Other recorded answers">
            {others.map((item) => (
              <TableRow key={keyOf(item.subsectionCode, item.questionCode, item)}>
                <TableCell>
                  {[
                    item.subsectionCode,
                    item.questionCode,
                    item.stringerNumber === null ? null : `stringer ${item.stringerNumber}`,
                  ]
                    .filter(Boolean)
                    .join(' · ')}
                </TableCell>
                <TableCell>{item.answer ?? '—'}</TableCell>
                <TableCell className="inspection-detail__comment">{item.comment ?? ''}</TableCell>
              </TableRow>
            ))}
          </AnswerTable>
        </Card>
      )}
    </>
  );
};

export default ConditionView;
