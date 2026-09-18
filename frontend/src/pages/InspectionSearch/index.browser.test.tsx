import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import InspectionSearchPage from './index';

import type { OrgUnitOption } from '@/types/configuration';

const authorization = vi.hoisted(() => ({ canDelete: false }));
const api = vi.hoisted(() => ({
  getSiteStatusCodes: vi.fn(),
  getStructureInspectionStatusCodes: vi.fn(),
  getSpecialAccessCodes: vi.fn(),
  getSiteTypeCodes: vi.fn(),
  getForestDistricts: vi.fn(),
  getManagementAreas: vi.fn(),
}));

vi.mock('@/hooks/useAuthorization', () => ({
  useAuthorization: () => authorization,
}));

vi.mock('@/context/pageTitle/usePageTitle', () => ({
  usePageTitle: () => ({ setPageTitle: vi.fn(), pageTitle: '' }),
}));

// Mocked at the service rather than at the hook, so the query keys, the fallback to an empty list
// and the error branch are all exercised by these tests instead of being stubbed past.
vi.mock('@/services/APIs', () => ({
  default: { configuration: api },
}));

const renderPage = (canDelete = false) => {
  authorization.canDelete = canDelete;
  // retry: false so a rejected lookup surfaces immediately; the app's own default already disables
  // retries, but a test should not depend on that staying true.
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <InspectionSearchPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
};

const districts: OrgUnitOption[] = [
  { orgUnitNo: '10', orgUnitCode: 'DPG', orgUnitName: 'Prince George Natural Resource District' },
  { orgUnitNo: '20', orgUnitCode: 'DCK', orgUnitName: 'Chilliwack Natural Resource District' },
];

const areas: OrgUnitOption[] = [
  { orgUnitNo: '11', orgUnitCode: 'DVA', orgUnitName: 'Vanderhoof Forest District' },
];

beforeEach(() => {
  Object.values(api).forEach((fn) => {
    fn.mockReset();
    fn.mockResolvedValue([]);
  });
});

const search = () => fireEvent.click(screen.getByTestId('inspection-search-submit'));
const type = (field: string, value: string) =>
  fireEvent.change(screen.getByTestId(`inspection-search-${field}`), { target: { value } });
const box = (field: string) => screen.getByTestId(`inspection-search-${field}`) as HTMLInputElement;

/** The day cells of whichever calendar is currently open, ignoring the neighbouring months. */
const openCalendarDays = () => {
  const calendars = document.querySelectorAll('.flatpickr-calendar.open');
  const calendar = calendars[calendars.length - 1];
  return Array.from(
    calendar.querySelectorAll<HTMLElement>('.flatpickr-day:not(.prevMonthDay):not(.nextMonthDay)'),
  );
};

describe('InspectionSearchPage — criteria form', () => {
  it('offers every criterion the legacy form does', () => {
    // Field-for-field with inspection_search.jsp. A missing criterion is a search someone can no
    // longer run, and there is no error to notice — the form just quietly does less.
    renderPage();

    for (const field of [
      'siteId',
      'structureName',
      'structureTypeClassCode',
      'inspectionTypeCode',
      'inspectionReportStatusCode',
      'forestFileId',
      'roadSectionId',
      'forestServiceRoad',
      'inspectionDateStart',
      'inspectionDateEnd',
      'orgUnitNo',
      'managementOrgUnitNo',
      'businessAreaOrgUnitNo',
      'inspectorName',
      'inspectionReviewerId',
      'closeProximity',
      'mostRecentInspections',
      'findChangedReviewed',
      'findMovedStructures',
    ]) {
      expect(screen.getByTestId(`inspection-search-${field}`)).toBeInTheDocument();
    }
  });

  it('does not offer Road Responsibility', () => {
    // The legacy form still carries the property, but the block that renders it is commented out in
    // the JSP, so no user has been able to set it for years. Porting it would add a criterion the
    // screen it came from does not have.
    renderPage();

    expect(screen.queryByTestId('inspection-search-roadResponsibilityCode')).toBeNull();
  });

  it('labels each filter with its question and states the switch position in words', () => {
    // The Site Search arrangement: question above, switch and its state below. The question is also
    // the control's accessible name, which getByRole is what checks.
    renderPage();

    const toggle = screen.getByRole('switch', { name: 'Most Recent Inspections Only?' });

    expect(toggle).toBeInTheDocument();
    expect(toggle.closest('.cds--toggle')).toHaveTextContent('Most Recent Inspections Only?');
    expect(toggle.closest('.cds--toggle')).toHaveTextContent('Off');
  });

  it('lays the four filters out two to a column', () => {
    // Four across leaves the longest question wrapping while the other three sit on one line. Two
    // columns of two give each question the room to be one line, and leave the other half of the
    // form's width to Sort By.
    renderPage();

    const grid = document.querySelector('.inspection-search__toggles-grid');

    expect(grid?.querySelectorAll('.cds--toggle')).toHaveLength(4);
  });

  it('stacks the Sort By options rather than letting them wrap into each other', () => {
    // Both labels wrap in half a two-column cell, and a wrapped horizontal radio puts its second
    // line under the other option's button — four fragments where there are two choices.
    renderPage();

    expect(document.querySelector('.cds--radio-button-group')).toHaveClass(
      'cds--radio-button-group--vertical',
    );
  });

  it('defaults Sort By to structure then date, as the legacy reset() does', () => {
    renderPage();

    expect(screen.getByTestId('inspection-search-sortBy-structure')).toBeChecked();
    expect(screen.getByTestId('inspection-search-sortBy-project')).not.toBeChecked();
  });
});

describe('InspectionSearchPage — validation', () => {
  it('refuses an empty search instead of returning every inspection', () => {
    // Legacy's errors.search.select. The results block must stay away entirely — legacy renders it
    // only once a search has run.
    renderPage();

    search();

    expect(screen.getByTestId('inspection-search-empty-error')).toBeInTheDocument();
    expect(screen.queryByTestId('inspection-search-results')).toBeNull();
  });

  it('clears the empty-form message as soon as a criterion is entered', () => {
    renderPage();
    search();

    type('siteId', '12345');

    expect(screen.queryByTestId('inspection-search-empty-error')).toBeNull();
  });

  it('does not accept "structures at previous sites" as the only criterion', () => {
    // It is not a filter — it changes what Site # matches — so on its own it narrows nothing.
    // Legacy lets it past this guard and then silently runs no query at all.
    renderPage();

    fireEvent.click(screen.getByTestId('inspection-search-findMovedStructures'));
    search();

    expect(screen.getByTestId('inspection-search-empty-error')).toBeInTheDocument();
  });

  it('rejects a month that is not yyyy/mm, on the box that holds it', () => {
    renderPage();

    type('inspectionDateStart', '2026-01');
    search();

    expect(screen.getByText('Enter a month as yyyy/mm, e.g. 2026/01')).toBeInTheDocument();
    expect(screen.queryByTestId('inspection-search-results')).toBeNull();
  });

  it('accepts a one-digit month, which the legacy SimpleDateFormat also accepts', () => {
    renderPage();

    type('inspectionDateStart', '2026/1');
    search();

    expect(screen.queryByText('Enter a month as yyyy/mm, e.g. 2026/01')).toBeNull();
    expect(screen.getByTestId('inspection-search-results')).toBeInTheDocument();
  });

  it('fills the month from the calendar, discarding the day', async () => {
    // The criterion is a month, so the day the calendar makes the user choose is not kept. Legacy's
    // popup was formatted yyyy/MM and threw the day away too.
    const user = userEvent.setup();
    renderPage();

    await user.click(box('inspectionDateStart'));
    await user.click(openCalendarDays()[9]);

    expect(box('inspectionDateStart').value).toMatch(/^\d{4}\/(0[1-9]|1[0-2])$/);
  });

  it('lets one end of the range stand on its own', async () => {
    // "Everything from this month onwards" is a whole criterion. Carbon's range picker treats a
    // lone month as an unfinished gesture and clears it on the next click outside, which is why
    // this is two single pickers rather than one range.
    const user = userEvent.setup();
    renderPage();

    await user.click(box('inspectionDateStart'));
    await user.click(openCalendarDays()[9]);
    await user.click(document.body);

    expect(box('inspectionDateStart').value).toMatch(/^\d{4}\/(0[1-9]|1[0-2])$/);
    expect(box('inspectionDateEnd').value).toBe('');
  });

  it('keeps a month that is only half typed', async () => {
    // The calendar is told about a month only once it is one. Handing it "2026/0" would have it
    // blank the box mid-keystroke, because flatpickr answers an unreadable value by erasing it.
    const user = userEvent.setup();
    renderPage();

    await user.click(box('inspectionDateStart'));
    await user.keyboard('2026/0');

    expect(box('inspectionDateStart').value).toBe('2026/0');
  });

  it('pads a one-digit month once the box is left', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(box('inspectionDateStart'));
    await user.keyboard('2026/3');
    await user.click(document.body);

    expect(box('inspectionDateStart').value).toBe('2026/03');
  });

  it('empties a box whose text is not a month, rather than searching on it', async () => {
    // The box and the criterion have to agree. flatpickr clears text it cannot read when the box is
    // left; without the blur handler copying that back, the search would still be filtering on a
    // month nothing on the screen shows.
    const user = userEvent.setup();
    renderPage();

    await user.click(box('inspectionDateStart'));
    await user.keyboard('2026-01');
    await user.click(document.body);
    search();

    expect(box('inspectionDateStart').value).toBe('');
    expect(screen.getByTestId('inspection-search-empty-error')).toBeInTheDocument();
  });

  it('rejects a range that ends before it starts', () => {
    // Legacy accepts this and turns it into a BETWEEN that cannot match, so the screen reports no
    // inspections — indistinguishable from a month with none in it.
    renderPage();

    type('inspectionDateStart', '2026/06');
    type('inspectionDateEnd', '2026/01');
    search();

    expect(screen.getByText('The end month cannot be before the start month')).toBeInTheDocument();
    expect(screen.queryByTestId('inspection-search-results')).toBeNull();
  });
});

describe('InspectionSearchPage — searching', () => {
  it('shows the results block only once a search has run', () => {
    renderPage();

    expect(screen.queryByTestId('inspection-search-results')).toBeNull();

    type('siteId', '12345');
    search();

    expect(screen.getByTestId('inspection-search-results')).toBeInTheDocument();
  });

  it('puts the results away again when the form is cleared', () => {
    renderPage();
    type('siteId', '12345');
    search();

    fireEvent.click(screen.getByTestId('inspection-search-reset'));

    expect(screen.queryByTestId('inspection-search-results')).toBeNull();
    expect(screen.getByTestId('inspection-search-siteId')).toHaveValue('');
  });

  it('says plainly that nothing is wired up yet', () => {
    // The screen ships ahead of its endpoint, so it has to say so. This test is expected to be
    // deleted along with the notice when the search is wired.
    renderPage();

    expect(screen.getByTestId('inspection-search-placeholder')).toBeInTheDocument();
  });
});

describe('InspectionSearchPage — reference data', () => {
  it('fills Forest District from the configuration endpoint', async () => {
    api.getForestDistricts.mockResolvedValue(districts);
    renderPage();

    await waitFor(() => {
      expect(
        screen.getByRole('option', { name: 'DPG - Prince George Natural Resource District' }),
      ).toBeInTheDocument();
    });
  });

  it('loads management areas for the chosen district, and not before', async () => {
    api.getForestDistricts.mockResolvedValue(districts);
    api.getManagementAreas.mockResolvedValue(areas);
    renderPage();

    // The districts have to be on the page first: a <select> silently ignores a value it has no
    // option for, so firing the change early leaves the criterion blank and the test green for the
    // wrong reason.
    await waitFor(() => {
      expect(screen.getByTestId('inspection-search-orgUnitNo')).toHaveTextContent(
        'DPG - Prince George Natural Resource District',
      );
    });
    expect(api.getManagementAreas).not.toHaveBeenCalled();

    fireEvent.change(screen.getByTestId('inspection-search-orgUnitNo'), {
      target: { value: '10' },
    });

    await waitFor(() => expect(api.getManagementAreas).toHaveBeenCalledWith('10'));
  });

  it('drops a chosen management area when the district changes underneath it', async () => {
    api.getForestDistricts.mockResolvedValue(districts);
    api.getManagementAreas.mockResolvedValue(areas);
    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId('inspection-search-orgUnitNo')).toHaveTextContent(
        'DPG - Prince George Natural Resource District',
      );
    });

    fireEvent.change(screen.getByTestId('inspection-search-orgUnitNo'), {
      target: { value: '10' },
    });
    await waitFor(() => {
      expect(screen.getByTestId('inspection-search-managementOrgUnitNo')).toHaveTextContent(
        'DVA - Vanderhoof Forest District',
      );
    });
    fireEvent.change(screen.getByTestId('inspection-search-managementOrgUnitNo'), {
      target: { value: '11' },
    });
    expect(screen.getByTestId('inspection-search-managementOrgUnitNo')).toHaveValue('11');

    fireEvent.change(screen.getByTestId('inspection-search-orgUnitNo'), {
      target: { value: '20' },
    });

    // Left alone it would submit a management area that is not in the list the user can now see — a
    // filter they cannot tell is applied.
    expect(screen.getByTestId('inspection-search-managementOrgUnitNo')).toHaveValue('');
  });

  it('says which filters are missing when a lookup fails, and still lets the search run', async () => {
    api.getForestDistricts.mockRejectedValue(new Error('boom'));
    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId('inspection-search-codes-error')).toBeInTheDocument();
    });

    type('siteId', '12345');
    search();

    expect(screen.getByTestId('inspection-search-results')).toBeInTheDocument();
  });
});
