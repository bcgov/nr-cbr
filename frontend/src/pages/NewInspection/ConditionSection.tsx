import { Report } from '@carbon/icons-react';
import { SkeletonText } from '@carbon/react';

import Card from '@/pages/StructureDetail/Card';

import AnswerRow from './AnswerRow';
import { itemKey, type Answer, type Answers } from './inspectionRules';
import StringerTable from './StringerTable';

import type {
  BridgePart,
  InspectionFormResponse,
  InspectionQuestion,
  InspectionSubsection,
} from './inspectionForm';
import type { FC, ReactNode } from 'react';

type Props = {
  form: InspectionFormResponse | undefined;
  loading: boolean;
  answers: Answers;
  onAnswer: (answer: Answer) => void;
  /** The errors to show now, by {@link itemKey}. */
  shown: Record<string, string | undefined>;
  onLeave: (key: string) => void;
};

/** Where in the structure a set of answers is for: a span or pier, and a stringer. */
type Where = { spanId: number | null; pierId: number | null; stringerNumber: number | null };

const partLabel = (kind: string, part: BridgePart) => `${kind} ${part.number ?? part.id}`;

/** One question, its answer kept in the page's answers under its {@link itemKey}. */
const QuestionRow: FC<{
  subsection: InspectionSubsection;
  question: InspectionQuestion;
  where: Where;
  answers: Answers;
  onAnswer: (answer: Answer) => void;
  shown: Record<string, string | undefined>;
  onLeave: (key: string) => void;
}> = ({ subsection, question, where, answers, onAnswer, shown, onLeave }) => {
  const at = { subsectionCode: subsection.code, questionCode: question.code, ...where };
  const key = itemKey(at);
  const current = answers[key] ?? { ...at, answer: '', comment: '' };
  return (
    <AnswerRow
      id={key.replaceAll('.', '-')}
      question={question}
      value={current}
      message={shown[key]}
      onChange={(change) => onAnswer({ ...current, ...change })}
      onLeave={() => onLeave(key)}
    />
  );
};

type RowProps = Omit<Props, 'form' | 'loading'> & { subsection: InspectionSubsection };

/** A set of questions, one row each, all for the same span, pier and stringer. */
const Questions: FC<RowProps & { questions: InspectionQuestion[]; where: Where }> = ({
  questions,
  where,
  subsection,
  ...rest
}) => (
  <div className="new-inspection__questions">
    {questions.map((question) => (
      <QuestionRow
        key={question.code}
        subsection={subsection}
        question={question}
        where={where}
        {...rest}
      />
    ))}
  </div>
);

/**
 * One question group as a card. A group asked of each span or pier stacks a block per span or pier,
 * each headed "Span N" / "Pier N"; a span's stringers are a table in it, one stringer's form open at
 * a time (see `StringerTable`).
 */
export const SubsectionCard: FC<
  RowProps & {
    spans: BridgePart[];
    piers: BridgePart[];
    /** The card's own action, in its header — the inspection page's Edit. */
    action?: ReactNode;
    /** Under the questions: the inspection page's Cancel and Save. */
    footer?: ReactNode;
  }
> = ({ spans, piers, action, footer, ...rest }) => {
  const { subsection } = rest;
  const perSpan = subsection.scope === 'SPAN';
  const perPier = subsection.scope === 'PIER';
  const groupQuestions = subsection.questions.filter((question) => !question.perStringer);
  const stringerQuestions = subsection.questions.filter((question) => question.perStringer);
  const testId = `condition-${subsection.code}`;

  /** One span's, one pier's, or the structure's questions and stringers. */
  const block = (where: Where, idPrefix: string) => (
    <>
      {groupQuestions.length > 0 && (
        <Questions questions={groupQuestions} where={where} {...rest} />
      )}
      {stringerQuestions.length > 0 && (
        <StringerTable
          subsectionCode={subsection.code}
          questions={stringerQuestions}
          where={{ spanId: where.spanId, pierId: where.pierId }}
          answers={rest.answers}
          onAnswer={rest.onAnswer}
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
    body = parts.map((part) => {
      const idPrefix = `${testId}-${kind.toLowerCase()}-${part.id}`;
      return (
        <section key={part.id} className="new-inspection__part" data-testid={idPrefix}>
          <h3 className="new-inspection__part-title">{partLabel(kind, part)}</h3>
          {block(
            {
              spanId: perSpan ? Number(part.id) : null,
              pierId: perPier ? Number(part.id) : null,
              stringerNumber: null,
            },
            idPrefix,
          )}
        </section>
      );
    });
  }

  return (
    <Card title={subsection.description} icon={Report} testId={testId} action={action}>
      <p className="structure-detail__note structure-detail__note--first">
        A comment is needed for any answer of Poor or Unrated.
      </p>
      {body}
      {footer && <div className="new-inspection__card-footer">{footer}</div>}
    </Card>
  );
};

/**
 * The Condition tab: legacy's inspection subsections, one card each, in its order. Deck,
 * Superstructure and Stringers are answered for each span, Piers for each pier — stacked in the
 * card, each under its own heading; Stringers' and the wood box culvert's stringer questions in
 * a table of stringers.
 */
const ConditionSection: FC<Props> = ({ form, loading, ...rest }) => {
  // Before there are questions to show, the tab is still a card, as the structure page's tabs.
  const empty = (content: ReactNode) => (
    <Card title="Condition" icon={Report} testId="condition-empty">
      {content}
    </Card>
  );
  if (loading) return empty(<SkeletonText paragraph lineCount={6} />);
  if (!form) {
    return empty(
      <p className="structure-detail__empty" data-testid="condition-no-structure">
        Choose the structure on the Inspection tab to see its questions.
      </p>,
    );
  }
  if (form.subsections.length === 0) {
    return empty(
      <p className="structure-detail__empty">This structure has no inspection questions.</p>,
    );
  }
  return (
    <>
      {form.subsections.map((subsection) => (
        <SubsectionCard
          key={subsection.code}
          subsection={subsection}
          spans={form.spans}
          piers={form.piers}
          {...rest}
        />
      ))}
    </>
  );
};

export default ConditionSection;
