import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { EMPTY_SITE, type SiteFormValues } from './types';

import SiteForm from './index';

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

const renderForm = (props: {
  values?: Partial<SiteFormValues>;
  errors?: Record<string, string>;
  warnings?: Record<string, string>;
  onChange?: (field: string, value: unknown) => void;
  isEditable?: () => boolean;
}) =>
  render(
    <QueryClientProvider client={new QueryClient()}>
      <SiteForm
        values={{ ...EMPTY_SITE, ...props.values }}
        errors={props.errors ?? {}}
        warnings={props.warnings ?? {}}
        codeTables={codeTables}
        onChange={props.onChange ?? (() => {})}
        isEditable={props.isEditable}
        onSettle={() => {}}
        onSave={() => {}}
        onFindRoad={() => {}}
      />
    </QueryClientProvider>,
  );

describe('SiteForm — Site #', () => {
  it('upper-cases the number when the user leaves the box', () => {
    // `CROSSING_SITE_ID` is matched exactly, so a lower-case entry is a different site. Legacy
    // folds it in `setSiteNumberToUpperCase()`, wired to the field's `onchange`.
    const onChange = vi.fn();
    renderForm({ values: { siteId: 'bowron-001' }, onChange });

    fireEvent.blur(screen.getByTestId('site-form-siteId'), {
      target: { value: 'bowron-001' },
    });

    expect(onChange).toHaveBeenCalledWith('siteId', 'BOWRON-001');
  });

  it('leaves a number that is already upper case alone', () => {
    // Firing a change with the value it already holds would mark a pristine form dirty.
    const onChange = vi.fn();
    renderForm({ values: { siteId: 'BOWRON-001' }, onChange });

    fireEvent.blur(screen.getByTestId('site-form-siteId'), {
      target: { value: 'BOWRON-001' },
    });

    expect(onChange).not.toHaveBeenCalled();
  });
});

describe('SiteForm — the warning tier', () => {
  it('shows a warning message beside the box it belongs to', () => {
    renderForm({
      values: { latitudeDegrees: '70' },
      warnings: { latitudeDegrees: 'Outside BC — latitude is usually 48–60.' },
    });

    expect(screen.getByText('Outside BC — latitude is usually 48–60.')).toBeInTheDocument();
  });

  it('leaves a field with neither in its ordinary state', () => {
    renderForm({ values: { latitudeDegrees: '53' } });

    const input = screen.getByTestId('site-form-latitudeDegrees');
    expect(input.getAttribute('aria-invalid')).not.toBe('true');
  });
});

describe('SiteForm — the longitude sign', () => {
  it('prints a minus in front of the longitude boxes, as legacy does', () => {
    // `site.jsp:1036` puts a literal "-" in the cell before the three boxes. The boxes take the
    // magnitude, so without it the form shows 122 where the column holds -122.
    renderForm({});

    expect(screen.getByTestId('site-form-longitude-sign')).toHaveTextContent('\u2212');
  });

  it('puts it before the degrees box rather than anywhere else in the row', () => {
    renderForm({});

    const sign = screen.getByTestId('site-form-longitude-sign');
    const degrees = screen.getByTestId('site-form-longitudeDegrees');

    expect(sign.compareDocumentPosition(degrees) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('gives latitude no sign, which is positive in British Columbia', () => {
    renderForm({});

    const signs = screen.getAllByTestId('site-form-longitude-sign');
    expect(signs).toHaveLength(1);
  });

  it('carries the sign into the read-only value too', () => {
    // Legacy's "-" sits outside its <c:choose>, so it shows in both branches.
    renderForm({
      values: { longitudeDegrees: '122', longitudeMinutes: '30', longitudeSeconds: '15.5' },
      isEditable: () => false,
    });

    expect(screen.getByText('\u2212122° 30′ 15.5″')).toBeInTheDocument();
  });
});
