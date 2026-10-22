import { criteriaFields } from './criteriaFields';
import { ReportButtons, SortBy, type SortOption } from './ReportOutput';

import type { CriteriaFields } from './criteriaGroups';
import type { ReportFormat } from './types';
import type { ReactNode, SubmitEventHandler } from 'react';

import { useFieldErrors } from '@/hooks/useFieldErrors';

type Props<S extends string, C extends { sortBy: S }> = {
  /** The prefix of every box's id and test id — the report's. */
  id: string;
  criteria: C;
  /** The report's orders, for Sort By; none for a report whose order is fixed. */
  sortOptions: readonly SortOption<S>[];
  /** The form's rules applied to the current criteria. */
  errors: Partial<Record<keyof C & string, string>>;
  /** The formats offered, a Download button each. */
  formats: readonly ReportFormat[];
  /** Disables the selects while their contents are still unknown. */
  codeTablesLoading: boolean;
  onChange: <K extends keyof C>(field: K, value: C[K]) => void;
  /** Runs the report as this format, once the criteria pass. */
  onRun: (format: ReportFormat) => void;
  /** The format being produced, while one is: the buttons wait, and that one says so. */
  running: ReportFormat | null;
  onReset: () => void;
  /** The form's boxes, built from the shared builders, in the form's own layout. */
  children: (fields: CriteriaFields<C>) => ReactNode;
};

/**
 * A report's criteria form: the boxes it lays out, then Sort By (where the order is the user's) and a
 * Download button per format. Every error shows once its box is left, and all of them on a
 * Download, which runs only when there are none; Enter in a box runs the PDF, the primary button —
 * unless a report is already on its way.
 */
const ReportCriteriaForm = <S extends string, C extends { sortBy: S }>({
  id,
  criteria,
  sortOptions,
  errors,
  formats,
  codeTablesLoading,
  onChange,
  onRun,
  running,
  onReset,
  children,
}: Props<S, C>) => {
  const { shown, leave, submit: check } = useFieldErrors<keyof C & string>(errors);
  const fields = criteriaFields({ id, criteria, shown, leave, onChange, codeTablesLoading });

  const run = (format: ReportFormat) => {
    if (check()) {
      onRun(format);
    }
  };

  const submit: SubmitEventHandler<HTMLFormElement> = (event) => {
    event.preventDefault();
    if (running === null) {
      run('PDF');
    }
  };

  return (
    // `noValidate`: the form's own rules show their messages beside each box; the browser's — a
    // month picker's `pattern` — would otherwise stop the submit with a bubble of its own instead.
    <form noValidate onSubmit={submit} data-testid={`${id}-form`} className="report-criteria__form">
      <div className="report-criteria__grid">{children(fields)}</div>
      {sortOptions.length > 0 && (
        <SortBy
          id={id}
          options={sortOptions}
          sortBy={criteria.sortBy}
          onChange={(sortBy) => onChange('sortBy', sortBy as C['sortBy'])}
        />
      )}
      <ReportButtons id={id} formats={formats} running={running} onRun={run} onReset={onReset} />
    </form>
  );
};

export default ReportCriteriaForm;
