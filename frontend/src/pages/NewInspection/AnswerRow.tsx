import { Select, SelectItem, TextInput } from '@carbon/react';

import { requiredLabel } from '@/utils/requiredLabel';

import { NEEDS_COMMENT, NUMERIC_QUESTIONS, isCommentMessage } from './inspectionRules';
import { answerWarningOf } from './stringerRules';

import type { InspectionQuestion } from './inspectionForm';
import type { ChangeEvent, FC } from 'react';

type Props = {
  /** The id its boxes are named from: `{id}-answer`, `{id}-comment`. */
  id: string;
  question: InspectionQuestion;
  value: { answer: string; comment: string };
  onChange: (change: Partial<{ answer: string; comment: string }>) => void;
  onLeave: () => void;
  /** Whether the answer is required: a red asterisk beside the question. */
  required?: boolean;
  /** What is wrong with it now, if anything — shown under the answer or the comment it is about. */
  message?: string;
};

/**
 * One question: its wording, its answer (a list, or typed) and its comment. A message about the
 * comment shows under the comment box, any other under the answer; legacy's range warning under the
 * answer, never stopping a save.
 */
const AnswerRow: FC<Props> = ({ id, question, value, onChange, onLeave, required, message }) => {
  const commentMessage = message && isCommentMessage(message) ? message : undefined;
  const answerMessage = message && !commentMessage ? message : undefined;
  const warning = answerMessage ? undefined : answerWarningOf(question, value.answer);
  // Poor and Unrated need a comment: said in the box, with the app's red asterisk beside it.
  const commentRequired = NEEDS_COMMENT.has(value.answer);
  return (
    <div className="new-inspection__question" data-testid={id}>
      <span className="new-inspection__question-label">
        {requiredLabel(question.description, required)}
      </span>
      {question.answers.length > 0 ? (
        <Select
          id={`${id}-answer`}
          data-testid={`${id}-answer`}
          labelText={`Answer: ${question.description}${required ? ' (required)' : ''}`}
          hideLabel
          value={value.answer}
          invalid={Boolean(answerMessage)}
          invalidText={answerMessage}
          onChange={(event) => onChange({ answer: event.target.value })}
          onBlur={onLeave}
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
          labelText={`Answer: ${question.description}${required ? ' (required)' : ''}`}
          hideLabel
          maxLength={255}
          // A number's keypad on a phone or tablet, for the questions answered with one.
          inputMode={NUMERIC_QUESTIONS.has(question.code) ? 'decimal' : undefined}
          value={value.answer}
          invalid={Boolean(answerMessage)}
          invalidText={answerMessage}
          warn={Boolean(warning)}
          warnText={warning}
          onChange={(event: ChangeEvent<HTMLInputElement>) =>
            onChange({ answer: event.target.value })
          }
          onBlur={onLeave}
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
          value={value.comment}
          invalid={Boolean(commentMessage)}
          invalidText={commentMessage}
          onChange={(event: ChangeEvent<HTMLInputElement>) =>
            onChange({ comment: event.target.value })
          }
          onBlur={onLeave}
        />
      </div>
    </div>
  );
};

export default AnswerRow;
