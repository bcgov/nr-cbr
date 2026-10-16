import { Add, Report } from '@carbon/icons-react';
import { Button, Select, SelectItem, SkeletonText, TextInput } from '@carbon/react';
import { useState } from 'react';

import Card from '@/pages/StructureDetail/Card';

import {
  NEEDS_COMMENT,
  isCommentMessage,
  itemKey,
  type Answer,
  type Answers,
} from './inspectionRules';

import type {
  BridgePart,
  InspectionFormResponse,
  InspectionQuestion,
  InspectionSubsection,
} from './inspectionForm';
import type { ChangeEvent, FC, ReactNode } from 'react';

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

/** One question: its answer (a list, or typed), and its comment. */
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
  const set = (change: Partial<Answer>) => onAnswer({ ...current, ...change });
  const id = key.replaceAll('.', '-');
  // A message about the comment shows under the comment box; any other, under the answer.
  const message = shown[key];
  const commentMessage = message && isCommentMessage(message) ? message : undefined;
  const answerMessage = message && !commentMessage ? message : undefined;
  // Poor and Unrated need a comment: said in the box, with the app's red asterisk beside it.
  const commentRequired = NEEDS_COMMENT.has(current.answer);
  return (
    <div className="new-inspection__question" data-testid={id}>
      <span className="new-inspection__question-label">{question.description}</span>
      {question.answers.length > 0 ? (
        <Select
          id={`${id}-answer`}
          data-testid={`${id}-answer`}
          labelText={`Answer: ${question.description}`}
          hideLabel
          value={current.answer}
          invalid={Boolean(answerMessage)}
          invalidText={answerMessage}
          onChange={(event) => set({ answer: event.target.value })}
          onBlur={() => onLeave(key)}
        >
          <SelectItem value="" text="Not answered" />
          {question.answers.map((option) => (
            <SelectItem key={option.code} value={option.code} text={option.description} />
          ))}
        </Select>
      ) : (
        <TextInput
          id={`${id}-answer`}
          data-testid={`${id}-answer`}
          labelText={`Answer: ${question.description}`}
          hideLabel
          maxLength={255}
          value={current.answer}
          invalid={Boolean(answerMessage)}
          invalidText={answerMessage}
          onChange={(event: ChangeEvent<HTMLInputElement>) => set({ answer: event.target.value })}
          onBlur={() => onLeave(key)}
        />
      )}
      <div className="new-inspection__comment">
        <span className="required-asterisk new-inspection__comment-mark" aria-hidden="true">
          {commentRequired ? '*' : ''}
        </span>
        <TextInput
          id={`${id}-comment`}
          data-testid={`${id}-comment`}
          labelText={`Comment: ${question.description}${commentRequired ? ' (required)' : ''}`}
          hideLabel
          placeholder={commentRequired ? 'Comment (required)' : 'Comment'}
          value={current.comment}
          invalid={Boolean(commentMessage)}
          invalidText={commentMessage}
          onChange={(event: ChangeEvent<HTMLInputElement>) => set({ comment: event.target.value })}
          onBlur={() => onLeave(key)}
        />
      </div>
    </div>
  );
};

/** The stringers already answered for this group and span, and any added since. */
const stringersOf = (answers: Answers, subsection: string, spanId: number | null): number[] =>
  [
    ...new Set(
      Object.values(answers)
        .filter(
          (each) =>
            each.subsectionCode === subsection &&
            each.spanId === spanId &&
            each.stringerNumber !== null,
        )
        .map((each) => each.stringerNumber as number),
    ),
  ].sort((a, b) => a - b);

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
 * The stringer questions for one span (or the whole culvert): a block per stringer, headed "Stringer
 * N", stacked, and Add stringer under them.
 */
const Stringers: FC<
  RowProps & { questions: InspectionQuestion[]; where: Where; idPrefix: string }
> = ({ questions, where, idPrefix, ...rest }) => {
  const [added, setAdded] = useState<number[]>([]);
  const stringers = [
    ...new Set([...stringersOf(rest.answers, rest.subsection.code, where.spanId), ...added]),
  ].sort((a, b) => a - b);
  const addStringer = () => setAdded((current) => [...current, Math.max(0, ...stringers) + 1]);
  return (
    <div className="new-inspection__stringers" data-testid={`${idPrefix}-stringers`}>
      {stringers.map((number) => (
        <section
          key={number}
          className="new-inspection__stringer"
          data-testid={`${idPrefix}-stringer-${number}`}
        >
          <h4 className="new-inspection__stringer-title">Stringer {number}</h4>
          <Questions questions={questions} where={{ ...where, stringerNumber: number }} {...rest} />
        </section>
      ))}
      {stringers.length === 0 && <p className="structure-detail__empty">No stringers yet.</p>}
      <div>
        <Button
          kind="tertiary"
          size="sm"
          renderIcon={Add}
          data-testid={`${idPrefix}-stringer-add`}
          onClick={addStringer}
        >
          Add stringer
        </Button>
      </div>
    </div>
  );
};

/**
 * One question group as a card. A group asked of each span or pier stacks a block per span or pier,
 * each headed "Span N" / "Pier N"; a span's stringers stack inside it, each headed "Stringer N".
 */
const SubsectionCard: FC<RowProps & { spans: BridgePart[]; piers: BridgePart[] }> = ({
  spans,
  piers,
  ...rest
}) => {
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
        <Stringers questions={stringerQuestions} where={where} idPrefix={idPrefix} {...rest} />
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
    <Card title={subsection.description} icon={Report} testId={testId}>
      <p className="structure-detail__note structure-detail__note--first">
        A comment is needed for any answer of Poor or Unrated.
      </p>
      {body}
    </Card>
  );
};

/**
 * The Condition tab: legacy's inspection subsections, one card each, in its order. Deck,
 * Superstructure and Stringers are answered for each span, Piers for each pier — stacked in the
 * card, each under its own heading; Stringers' and the wood box culvert's stringer questions for
 * each stringer added.
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
