import ReadOnlyField from '@/components/core/ReadOnlyField';

import { describe, isOther } from './format';

import type { CodeValue } from './structureResponse';
import type { FC } from 'react';

type Props = {
  label: string;
  value: CodeValue;
  comment: string | null;
  otherLabel?: string;
};

/** A code field, followed by its "If Other, please specify" comment when the code is Other. */
const CodeWithOther: FC<Props> = ({
  label,
  value,
  comment,
  otherLabel = 'If Other, please specify',
}) => (
  <>
    <ReadOnlyField label={label} value={describe(value)} />
    {isOther(value) && <ReadOnlyField label={otherLabel} value={comment} />}
  </>
);

export default CodeWithOther;
