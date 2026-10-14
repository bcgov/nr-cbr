import { useCallback, useMemo, useState } from 'react';

type Messages<F extends string> = Partial<Record<F, string | undefined>>;

/**
 * Which of a form's errors to show, and when — the app's one rule for inline validation:
 *
 * <ul>
 *   <li>a field's error shows once the user has <b>left</b> the field, and from then on follows
 *       their corrections live;
 *   <li>a field not yet visited stays quiet until <b>Save</b>, which shows every error;
 *   <li>what the <b>server</b> refused shows beside its field until that field is changed.
 * </ul>
 *
 * <p>The form computes its own errors from its values on every render — `errors` — so what is shown
 * is always what the values say now, never a stale copy taken at Save.
 *
 * @param errors       the form's own rules applied to its current values
 * @param serverErrors what the last save was refused for, keyed the same way
 */
export const useFieldErrors = <F extends string>(
  errors: Messages<F>,
  serverErrors: Messages<F> = {},
) => {
  const [visited, setVisited] = useState<ReadonlySet<string>>(() => new Set());
  const [submitted, setSubmitted] = useState(false);
  /** Fields changed since the server last refused them, whose server message no longer applies. */
  const [changed, setChanged] = useState<ReadonlySet<string>>(() => new Set());

  /** For a field's `onBlur`: its error, if any, shows from now on. */
  const leave = useCallback(
    (field: F) => setVisited((prev) => (prev.has(field) ? prev : new Set(prev).add(field))),
    [],
  );

  /** For a field's change: the server's message about its old value is dropped. */
  const change = useCallback(
    (field: F) => setChanged((prev) => (prev.has(field) ? prev : new Set(prev).add(field))),
    [],
  );

  const ownErrors = useMemo(
    () =>
      Object.fromEntries(
        Object.entries(errors).filter(([, message]) => Boolean(message)),
      ) as Messages<F>,
    [errors],
  );

  /**
   * For Save: shows every error, and answers whether the form may be sent. The server's messages
   * are taken afresh from the next response.
   */
  const submit = useCallback((): boolean => {
    setSubmitted(true);
    setChanged(new Set());
    return Object.keys(ownErrors).length === 0;
  }, [ownErrors]);

  const shown: Messages<F> = {
    ...(Object.fromEntries(
      Object.entries(serverErrors).filter(([field, message]) => message && !changed.has(field)),
    ) as Messages<F>),
    ...(Object.fromEntries(
      Object.entries(ownErrors).filter(([field]) => submitted || visited.has(field)),
    ) as Messages<F>),
  };

  return { shown, leave, change, submit };
};

/**
 * For a form's Save and Cancel buttons' `onMouseDown`: keeps focus in the field being typed in.
 *
 * <p>Without it, pressing Save first takes focus from that field, its error appears, the layout
 * moves the button down, and the release lands where the button was — so the first click does
 * nothing. Save shows every error anyway, and Cancel has no use for one.
 */
export const holdFocus = (event: { preventDefault: () => void }) => event.preventDefault();
