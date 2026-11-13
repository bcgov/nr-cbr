import { screen, waitFor, within } from '@testing-library/react';
import { userEvent } from 'vitest/browser';

/**
 * Drives a `MultiCodeSelect` (Carbon's MultiSelect or FilterableMultiSelect) by its test id, the
 * way a user does: open it, tick, close. Its options exist only while the list is open, so a test
 * reads them through {@link multiSelectOptions} rather than from the DOM at rest.
 */

const box = (testId: string): HTMLElement => screen.getByTestId(testId);

const trigger = (testId: string): HTMLElement => within(box(testId)).getByRole('combobox');

const isOpen = (testId: string): boolean => within(box(testId)).queryAllByRole('option').length > 0;

const open = async (testId: string): Promise<void> => {
  if (!isOpen(testId)) {
    await userEvent.click(trigger(testId));
  }
  await waitFor(() => expect(isOpen(testId)).toBe(true));
};

const close = async (testId: string): Promise<void> => {
  if (isOpen(testId)) {
    await userEvent.keyboard('{Escape}');
  }
  await waitFor(() => expect(isOpen(testId)).toBe(false));
};

const optionTexts = (testId: string, selectedOnly: boolean): string[] =>
  within(box(testId))
    .getAllByRole('option')
    .filter((option) => !selectedOnly || option.getAttribute('aria-selected') === 'true')
    .map((option) => option.textContent?.trim() ?? '');

/** The texts of every option, in the list's order. Opens the list and closes it again. */
export const multiSelectOptions = async (testId: string): Promise<string[]> => {
  await open(testId);
  const texts = optionTexts(testId, false);
  await close(testId);
  return texts;
};

/** The texts of the options ticked. Opens the list and closes it again. */
export const multiSelectSelected = async (testId: string): Promise<string[]> => {
  await open(testId);
  const texts = optionTexts(testId, true);
  await close(testId);
  return texts;
};

/** Ticks (or unticks) each option named — its full text, e.g. `ACT - Active` — then closes. */
export const toggleMultiSelect = async (testId: string, ...texts: string[]): Promise<void> => {
  await open(testId);
  // One click after another, as a user ticks them: chained, since clicks run together would race.
  await texts.reduce(
    (clicked, text) =>
      clicked.then(() => userEvent.click(within(box(testId)).getByRole('option', { name: text }))),
    Promise.resolve(),
  );
  await close(testId);
};

/** Whether the box can be opened. */
export const multiSelectDisabled = (testId: string): boolean =>
  (trigger(testId) as HTMLButtonElement | HTMLInputElement).disabled ||
  trigger(testId).getAttribute('aria-disabled') === 'true';

/**
 * The box's own text at rest — its placeholder, e.g. "Any status". The filterable box shows it as
 * the input's placeholder rather than as text.
 */
export const multiSelectText = (testId: string): string =>
  [box(testId).textContent ?? '', box(testId).querySelector('input')?.placeholder ?? '']
    .join(' ')
    .trim();
