import { Select, SelectItem } from '@carbon/react';

import { requiredLabel } from '@/utils/requiredLabel';

import type { FC } from 'react';

type Props = {
  id: string;
  label: string;
  value: string;
  options: { code: string; description: string }[];
  loading: boolean;
  error: string | undefined;
  onChange: (code: string) => void;
  onBlur: () => void;
};

/** A repair's or monitoring item's status, required, from the statuses this user may set. */
const StatusSelect: FC<Props> = ({
  id,
  label,
  value,
  options,
  loading,
  error,
  onChange,
  onBlur,
}) => (
  <Select
    id={id}
    data-testid={id}
    labelText={requiredLabel(label, true)}
    value={value}
    disabled={loading}
    invalid={Boolean(error)}
    invalidText={error}
    onChange={(event) => onChange(event.target.value)}
    onBlur={onBlur}
  >
    <SelectItem value="" text="Choose a status" />
    {options.map((option) => (
      <SelectItem key={option.code} value={option.code} text={option.description} />
    ))}
  </Select>
);

export default StatusSelect;
