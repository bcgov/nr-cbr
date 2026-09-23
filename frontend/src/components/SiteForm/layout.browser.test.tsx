import { TextInput } from '@carbon/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { page } from '@vitest/browser/context';
import { describe, expect, it, vi } from 'vitest';

import { EMPTY_SITE } from './types';

import SiteForm from './index';

// The app's real stylesheet, Carbon included. Every other browser test loads only the component's
// own SCSS, which is enough to assert a rule of ours is applied — but not to measure a layout,
// because Carbon supplies the widths and heights the rules act on.
import '@/styles/index.scss';

// The client lookup inside the form reaches for the API; nothing here exercises it.
vi.mock('@/services/APIs', () => ({ default: { client: { searchClients: vi.fn() } } }));

const codeTables = {
  siteStatusCodes: [],
  siteTypeCodes: [],
  structureInspectionStatusCodes: [],
  specialAccessCodes: [],
  forestDistricts: [],
  recreationDistricts: [],
  managementAreas: [],
  businessAreas: [],
};

const renderForm = () =>
  render(
    <QueryClientProvider client={new QueryClient()}>
      <SiteForm
        values={EMPTY_SITE}
        errors={{}}
        codeTables={codeTables}
        onChange={() => {}}
        onSettle={() => {}}
        onSave={() => {}}
        onFindRoad={() => {}}
      />
    </QueryClientProvider>,
  );

const box = (testId: string) => screen.getByTestId(testId).getBoundingClientRect();

describe('SiteForm — read-only cells beside inputs', () => {
  it('puts a value on the same line as the input beside it', async () => {
    // A read-only cell is label-above-value where an input is label-above-box, so without a
    // field-height band the value floats at the top of the row and the input sits half an inch
    // below it. Measured on the centres, which is what the eye reads as "the same line".
    // Wide enough for the grid's four columns, or the two cells are not in one row.
    await page.viewport(1400, 900);
    renderForm();

    const value = document
      .querySelector('.read-only-field__value')
      ?.getBoundingClientRect() as DOMRect;
    const input = screen.getByTestId('site-form-forestFileId').getBoundingClientRect();

    expect(value.top + value.height / 2).toBeCloseTo(input.top + input.height / 2, 0);
  });
});

describe('SiteForm — the Project File ID# row', () => {
  it('keeps Br. narrow, as the two-character value it holds', () => {
    // It was sized by a `:last-child` rule that stopped matching when the lookup button was added
    // beside it, so it silently grew to the width of a full field.
    renderForm();

    expect(box('site-form-roadSectionId').width).toBeLessThan(box('site-form-forestFileId').width);
  });

  it('keeps the button beside the field rather than adrift of it', () => {
    // The same lapsed `:last-child` rule gave the button a six-rem box it had no use for, and the
    // space after the icon read as a gap.
    renderForm();

    const gap = box('site-form-find-road').left - box('site-form-roadSectionId').right;

    // Measured from the input to the button, so it includes the inset Carbon leaves inside the
    // input's own wrapper — the visible gap between the two grey boxes is smaller than this.
    expect(gap).toBeGreaterThanOrEqual(0);
    expect(gap).toBeLessThanOrEqual(10);
  });
});

describe('SiteForm — Capital Road', () => {
  it('sits on the same line as the control beside it, not a label above it', async () => {
    // A checkbox carries its text beside the box, so it has no label row of its own — without one
    // reserved it starts where every other field's *label* starts, a line above the inputs it
    // shares a row with. Measured on the centres, which is what the eye reads as "the same line".
    await page.viewport(1400, 900);
    renderForm();

    // The wrapper, not the input: Carbon's native checkbox is a 1px-tall hidden box and the tick
    // the user sees is drawn by the label's ::before.
    const checkbox = (
      document.querySelector('.site-form__checkbox .cds--checkbox-wrapper') as HTMLElement
    ).getBoundingClientRect();
    expect(checkbox.top).toBeCloseTo(box('site-form-userKm').top, 0);
  });
});

describe('SiteForm — the three narrow fields', () => {
  it('sit together on one line rather than a column each', async () => {
    // Kilometres, User Kilometres and Capital Road are a short number, a short number and a tick
    // box. A column each left most of three columns empty and pushed them a third of the form
    // apart.
    await page.viewport(1400, 900);
    renderForm();

    const kilometres = box('site-form-pointOfCommencementDistance');
    const userKm = box('site-form-userKm');
    const checkbox = (
      document.querySelector('.site-form__checkbox .cds--checkbox-wrapper') as HTMLElement
    ).getBoundingClientRect();

    expect(userKm.top).toBeCloseTo(kilometres.top, 0);
    expect(checkbox.top).toBeCloseTo(kilometres.top, 0);
    expect(userKm.left - kilometres.right).toBeLessThanOrEqual(16);
    expect(checkbox.left - userKm.right).toBeLessThanOrEqual(16);
  });

  it('keeps each box the width of its value, not of its share of the row', async () => {
    // `NUMBER(8,2)` holds "999999.99" at the very longest.
    await page.viewport(1400, 900);
    renderForm();

    expect(box('site-form-pointOfCommencementDistance').width).toBeLessThan(
      box('site-form-crossingName').width / 2,
    );
    expect(box('site-form-userKm').width).toBeCloseTo(
      box('site-form-pointOfCommencementDistance').width,
      0,
    );
  });
});

describe('SiteForm — the road lookup button', () => {
  it('sits level with the boxes it belongs to, not with their labels', async () => {
    // It has no label of its own, so without one it starts at the top of the cell while the two
    // boxes beside it start below theirs. Here rather than in the page's own test file: Carbon
    // supplies the label margin the arithmetic depends on, and only this file loads it.
    await page.viewport(1400, 900);
    renderForm();

    expect(box('site-form-find-road').top).toBeCloseTo(box('site-form-roadSectionId').top, 0);
  });
});

describe('SiteForm — the narrow group on Add Site', () => {
  it('keeps Kilometres narrow and Capital Road beside it with User Kilometres gone', async () => {
    // Add Site leaves User Kilometres off — legacy will not let anyone set it — and a lone flex
    // child stretches to the whole column unless its basis wins. Carbon's own
    // `.cds--form-item { flex: 1 1 auto }` is one class, so a `> *` selector ties it and loses on
    // source order; the rule is prefixed with the grid to outrank it.
    await page.viewport(1400, 900);
    render(
      <QueryClientProvider client={new QueryClient()}>
        <SiteForm
          values={EMPTY_SITE}
          errors={{}}
          codeTables={codeTables}
          hiddenFields={new Set(['userKm'])}
          onChange={() => {}}
          onSettle={() => {}}
          onSave={() => {}}
          onFindRoad={() => {}}
        />
      </QueryClientProvider>,
    );

    expect(screen.queryByTestId('site-form-userKm')).not.toBeInTheDocument();

    const kilometres = box('site-form-pointOfCommencementDistance');
    const checkbox = (
      document.querySelector('.site-form__checkbox .cds--checkbox-wrapper') as HTMLElement
    ).getBoundingClientRect();

    expect(kilometres.width).toBeLessThan(box('site-form-crossingName').width / 2);
    // Capital Road closes up beside it rather than staying where the second box would have been.
    expect(checkbox.left - kilometres.right).toBeLessThanOrEqual(16);
    expect(checkbox.top).toBeCloseTo(kilometres.top, 0);
  });
});

describe('SiteForm — label spacing', () => {
  it('leaves the same gap below a label as every other screen', async () => {
    // A plain Carbon field rendered beside the form is the control. This form reserves a label
    // height of its own so that rows line up; reserving two lines put it 16px out of step with
    // Site Search and everything else.
    await page.viewport(1400, 900);
    render(
      <QueryClientProvider client={new QueryClient()}>
        <div>
          <TextInput id="plain" data-testid="plain" labelText="Plain Carbon field" />
          <SiteForm
            values={EMPTY_SITE}
            errors={{}}
            codeTables={codeTables}
            onChange={() => {}}
            onSettle={() => {}}
            onSave={() => {}}
            onFindRoad={() => {}}
          />
        </div>
      </QueryClientProvider>,
    );

    const gap = (labelFor: string, inputId: string) => {
      const label = document.querySelector(`label[for="${labelFor}"]`) as HTMLElement;
      return box(inputId).top - label.getBoundingClientRect().top;
    };

    expect(gap('site-form-siteId', 'site-form-siteId')).toBeCloseTo(gap('plain', 'plain'), 0);
  });

  it('reserves one line for a label, because no label needs two', async () => {
    // What lets the gap above match. If a label is ever added that wraps, this fails here rather
    // than that row quietly dropping out of line with the rest.
    await page.viewport(1400, 900);
    renderForm();

    const labels = Array.from(
      document.querySelectorAll('.site-form__fields .cds--label'),
    ) as HTMLElement[];

    expect(labels.length).toBeGreaterThan(10);
    for (const label of labels) {
      const lineHeight = parseFloat(getComputedStyle(label).lineHeight);
      expect(label.getBoundingClientRect().height).toBeLessThanOrEqual(lineHeight);
    }
  });
});
