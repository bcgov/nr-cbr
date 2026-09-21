import { TextInput } from '@carbon/react';
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { requiredLabel } from '@/utils/requiredLabel';

import '@/styles/_overrides.scss';

describe('requiredLabel', () => {
  it('appends a marker to a required label', () => {
    render(<TextInput id="a" labelText={requiredLabel('Kilometres', true)} />);

    expect(screen.getByText('Kilometres').textContent).toContain('*');
  });

  it('leaves an optional label alone', () => {
    render(<TextInput id="a" labelText={requiredLabel('User Kilometres')} />);

    expect(screen.getByText('User Kilometres').textContent).not.toContain('*');
  });

  it('hides the marker from a screen reader, which hears "required" from the field itself', () => {
    render(<TextInput id="a" labelText={requiredLabel('Kilometres', true)} />);

    expect(document.querySelector('.required-asterisk')).toHaveAttribute('aria-hidden', 'true');
  });

  it('does not make the field taller than an optional one beside it', () => {
    // The marker is 1.1rem inside a 0.75rem label. Left with a line height of its own it grew the
    // label, so a required field's input sat a few pixels below its neighbour's and a row of
    // fields stopped lining up. Asserted on the rendered position, because the cause is a line box
    // and nothing about the markup would look wrong.
    render(
      <div style={{ display: 'flex' }}>
        <TextInput id="required" labelText={requiredLabel('Kilometres', true)} />
        <TextInput id="optional" labelText={requiredLabel('User Kilometres')} />
      </div>,
    );

    const required = document.querySelector('#required') as HTMLElement;
    const optional = document.querySelector('#optional') as HTMLElement;

    expect(required.getBoundingClientRect().top).toBeCloseTo(
      optional.getBoundingClientRect().top,
      1,
    );
  });
});
