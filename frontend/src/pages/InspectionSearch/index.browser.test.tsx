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
  getStructureTypeClassCodes: vi.fn(),
  getInspectionTypeCodes: vi.fn(),
  getInspectionReportStatusCodes: vi.fn(),
  getForestDistricts: vi.fn(),
  getBusinessAreas: vi.fn(),
  getManagementAreas: vi.fn(),
}));
const searchApi = vi.hoisted(() => ({
  searchInspections: vi.fn(),
  deleteInspection: vi.fn(),
}));

// The delete outcome is a toast, so the page reads the notification context. Mocked rather than
// wrapped in a real NotificationProvider so the assertions are on what the page asked to show, not
// on Carbon's rendering of it — which NotificationProvider's own test already covers.
const display = vi.hoisted(() => vi.fn());
vi.mock('@/context/notification/useNotification', () => ({
  useNotification: () => ({ display }),
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
  default: { configuration: api, inspectionSearch: searchApi },
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

/** One page of results, shaped as the backend's PagedResponse. */
const page = (content: unknown[], totalElements = content.length) => ({
  content,
  totalElements,
  totalPages: Math.max(Math.ceil(totalElements / 20), 1),
  pageNumber: 0,
  pageSize: 20,
});

const inspectionRow = {
  id: '42',
  inspectionDate: '2026-06-15',
  inspectionReportStatusCode: 'SUB',
  inspectionReportStatusDescription: 'Submitted',
  siteAtTimeOfInspection: 'SITE-1',
  structureName: 'BR000001',
  orgUnitCode: 'DPG',
  orgUnitName: 'Prince George Natural Resource District',
  forestServiceRoad: 'Deadman FSR',
  pointOfCommencementDistance: '12.50',
  crossingName: 'Deadman Creek',
  forestFileId: 'R00123',
  roadSectionId: '01',
};

beforeEach(() => {
  Object.values(api).forEach((fn) => {
    fn.mockReset();
    fn.mockResolvedValue([]);
  });
  searchApi.searchInspections.mockReset();
  searchApi.searchInspections.mockResolvedValue(page([]));
  searchApi.deleteInspection.mockReset();
  searchApi.deleteInspection.mockResolvedValue(undefined);
  // Shared across tests because vi.hoisted runs once — without this a "was a toast shown"
  // assertion passes on a call the previous test made.
  display.mockClear();
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

  it('disables Reviewed By, which has nothing behind it yet', async () => {
    // The one filter on this form with no source: decision D4 has not settled whether reviewers
    // come from STRUCTURE_INSPECTION_REVIEWER or from FAM. Left enabled it would offer a filter
    // whose only option is "Anyone" — the same as not filtering — so a user could believe they had
    // narrowed a search when they had not.
    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId('inspection-search-inspectionReviewerId')).toBeDisabled();
    });
    expect(screen.getByText('Not available yet')).toBeInTheDocument();
  });

  it('leaves every other filter usable', async () => {
    // The reviewer is disabled on its own account, not because the form is still loading — so the
    // rest of the form must not be disabled with it.
    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId('inspection-search-inspectionTypeCode')).toBeEnabled();
    });
    expect(screen.getByTestId('inspection-search-siteId')).toBeEnabled();
  });

  it('is not badged as under construction, because it works', async () => {
    // The badge tells users not to trust what they are seeing. Searching, paging, sorting and
    // deleting all run against real endpoints now, so it would be saying something untrue about
    // results that are correct. The one unfinished control says so on itself instead.
    renderPage();

    await waitFor(() => {
      expect(screen.getByText('Inspection Search')).toBeInTheDocument();
    });
    expect(screen.queryByText(/under construction/i)).toBeNull();
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
  it('runs an unfiltered search rather than refusing it, as Site Search does', async () => {
    // Legacy refuses this on all five of its search forms with errors.search.select, because its
    // query was unpaginated and "no criteria" meant every inspection in the province at once. The
    // search is paged server-side now, so "show me everything, twenty at a time" is an ordinary
    // request — and the two search screens in this app must agree about it.
    renderPage();

    search();

    expect(await screen.findByTestId('inspection-search-results')).toBeInTheDocument();
    expect(searchApi.searchInspections).toHaveBeenCalled();
  });

  it('rejects a month that is not yyyy/mm, on the box that holds it', () => {
    renderPage();

    type('inspectionDateStart', '2026-01');
    search();

    expect(screen.getByText('Enter a month as yyyy/mm, e.g. 2026/01')).toBeInTheDocument();
    expect(searchApi.searchInspections).not.toHaveBeenCalled();
  });

  it('accepts a one-digit month, which the legacy SimpleDateFormat also accepts', () => {
    renderPage();

    type('inspectionDateStart', '2026/1');
    search();

    expect(screen.queryByText('Enter a month as yyyy/mm, e.g. 2026/01')).toBeNull();
    expect(searchApi.searchInspections).toHaveBeenCalled();
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
    // The month never reaches the request: flatpickr erased it, and the criterion went with it.
    expect(searchApi.searchInspections).toHaveBeenCalledWith(
      expect.objectContaining({ inspectionDateStart: '' }),
      0,
      20,
    );
  });

  it('rejects a range that ends before it starts', () => {
    // Legacy accepts this and turns it into a BETWEEN that cannot match, so the screen reports no
    // inspections — indistinguishable from a month with none in it.
    renderPage();

    type('inspectionDateStart', '2026/06');
    type('inspectionDateEnd', '2026/01');
    search();

    expect(screen.getByText('The end month cannot be before the start month')).toBeInTheDocument();
    expect(searchApi.searchInspections).not.toHaveBeenCalled();
  });
});

describe('InspectionSearchPage — searching', () => {
  it('shows the results block only once a search has run', async () => {
    renderPage();

    expect(screen.queryByTestId('inspection-search-results')).toBeNull();
    expect(screen.queryByTestId('inspection-search-loading')).toBeNull();

    type('siteId', '12345');
    search();

    // The table is preceded by its own loading state now that a request is really made, so this
    // waits rather than asserting straight through it.
    expect(await screen.findByTestId('inspection-search-results')).toBeInTheDocument();
  });

  it('sends the submitted criteria to the endpoint, not the live form state', async () => {
    renderPage();

    type('siteId', '12345');
    search();
    await screen.findByTestId('inspection-search-results');
    // Editing after the search must not re-run it: the results on screen belong to what was
    // submitted, and a keystroke changing them under the user would be worse than stale.
    type('siteId', '99999');

    expect(searchApi.searchInspections).toHaveBeenCalledTimes(1);
    expect(searchApi.searchInspections).toHaveBeenCalledWith(
      expect.objectContaining({ siteId: '12345' }),
      0,
      20,
    );
  });

  it('renders the rows the endpoint returned', async () => {
    searchApi.searchInspections.mockResolvedValue(page([inspectionRow]));
    renderPage();

    type('siteId', '12345');
    search();

    await screen.findByTestId('inspection-search-results');
    expect(screen.getByText('BR000001')).toBeInTheDocument();
    // The application's date format, the same one every other screen uses.
    expect(screen.getByText('Jun 15, 2026')).toBeInTheDocument();
    expect(screen.getByText('Submitted')).toBeInTheDocument();
  });

  it('asks for a zero-based page, because Carbon counts from one and the backend does not', async () => {
    searchApi.searchInspections.mockResolvedValue(page([inspectionRow], 100));
    renderPage();
    type('siteId', '12345');
    search();
    await screen.findByTestId('inspection-search-results');

    fireEvent.click(screen.getByLabelText('Next page'));

    await waitFor(() => {
      expect(searchApi.searchInspections).toHaveBeenLastCalledWith(
        expect.objectContaining({ siteId: '12345' }),
        1,
        20,
      );
    });
  });

  it('says the search failed rather than showing an empty table', async () => {
    // An empty table and a failed request look identical to a user, and one of them means "try
    // again". The results component is not rendered at all in this branch.
    searchApi.searchInspections.mockRejectedValue(new Error('boom'));
    renderPage();

    type('siteId', '12345');
    search();

    expect(await screen.findByTestId('inspection-search-error')).toBeInTheDocument();
    expect(screen.queryByTestId('inspection-search-results')).toBeNull();
  });

  it('puts the results away again when the form is cleared', () => {
    renderPage();
    type('siteId', '12345');
    search();

    fireEvent.click(screen.getByTestId('inspection-search-reset'));

    expect(screen.queryByTestId('inspection-search-results')).toBeNull();
    expect(screen.getByTestId('inspection-search-siteId')).toHaveValue('');
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

  it('fills every code-table filter from its own endpoint', async () => {
    // Four lists of the same shape, read through four hooks, assembled into one object. Crossing
    // two of them over compiles and renders a perfectly plausible form, so each stub carries a
    // value that says where it came from.
    api.getStructureTypeClassCodes.mockResolvedValue([
      { code: 'BRIDGE', description: 'Forest service bridge' },
    ]);
    api.getInspectionTypeCodes.mockResolvedValue([{ code: 'ROUT', description: 'Routine' }]);
    api.getInspectionReportStatusCodes.mockResolvedValue([
      { code: 'SUB', description: 'Submitted' },
    ]);
    api.getBusinessAreas.mockResolvedValue([
      { orgUnitNo: '1833', orgUnitCode: 'TBA', orgUnitName: 'Babine Business Area' },
    ]);
    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId('inspection-search-structureTypeClassCode')).toHaveTextContent(
        'BRIDGE - Forest service bridge',
      );
    });
    expect(screen.getByTestId('inspection-search-inspectionTypeCode')).toHaveTextContent(
      'ROUT - Routine',
    );
    expect(screen.getByTestId('inspection-search-inspectionReportStatusCode')).toHaveTextContent(
      'SUB - Submitted',
    );
    expect(screen.getByTestId('inspection-search-businessAreaOrgUnitNo')).toHaveTextContent(
      'TBA - Babine Business Area',
    );
  });

  it('offers the offline and accepted statuses a data-entry form would hide', async () => {
    // OFL is machine-set by the offline checkout and ACC is an expired status that saving rewrites
    // to RVD — so both are unsettable, and both sit on rows this screen exists to find. The backend
    // returns them for that reason; the form must not filter them back out.
    api.getInspectionReportStatusCodes.mockResolvedValue([
      { code: 'ACC', description: 'Accepted' },
      { code: 'OFL', description: 'Offline' },
    ]);
    renderPage();

    await waitFor(() => {
      expect(screen.getByRole('option', { name: 'OFL - Offline' })).toBeInTheDocument();
    });
    expect(screen.getByRole('option', { name: 'ACC - Accepted' })).toBeInTheDocument();
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

    expect(await screen.findByTestId('inspection-search-results')).toBeInTheDocument();
  });
});

describe('InspectionSearchPage — deleting an offline inspection', () => {
  const offlineRow = {
    ...inspectionRow,
    inspectionReportStatusCode: 'OFL',
    inspectionReportStatusDescription: 'Offline',
  };

  /** Search, then press the delete icon on the one offline row, so the dialog is open. */
  const openDeleteConfirmation = async () => {
    searchApi.searchInspections.mockResolvedValue(page([offlineRow]));
    renderPage(true);
    type('siteId', '12345');
    search();
    await screen.findByTestId('inspection-search-results');

    fireEvent.click(screen.getByTestId(`inspection-delete-${offlineRow.id}`));
    await screen.findByText(/Are you sure you would like to delete offline inspection/);
  };

  it('asks before deleting, and says what else goes with it', async () => {
    // The cascade is the part a user cannot see: attachments, repairs, monitor items and the
    // status history go too, and nothing records that the inspection existed.
    await openDeleteConfirmation();

    expect(screen.getByText(/attachments, repairs, monitor items and history/)).toBeInTheDocument();
    expect(screen.getByText(/cannot be undone/)).toBeInTheDocument();
    expect(searchApi.deleteInspection).not.toHaveBeenCalled();
  });

  it('does not delete when the dialog is cancelled', async () => {
    await openDeleteConfirmation();

    fireEvent.click(screen.getByText('Cancel'));

    expect(searchApi.deleteInspection).not.toHaveBeenCalled();
  });

  it('deletes on confirm and says so', async () => {
    await openDeleteConfirmation();

    fireEvent.click(screen.getByText('Delete'));

    await waitFor(() => expect(searchApi.deleteInspection).toHaveBeenCalledWith(offlineRow.id));
    await waitFor(() => {
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({ kind: 'success', title: `Inspection ${offlineRow.id} deleted` }),
      );
    });
  });

  it('re-runs the search afterwards, so the total and the paging stay honest', async () => {
    // Dropping the row locally would leave "1 match" over an empty table. Refetching is one
    // request and cannot disagree with the server.
    await openDeleteConfirmation();
    const searchesBefore = searchApi.searchInspections.mock.calls.length;

    fireEvent.click(screen.getByText('Delete'));

    await waitFor(() => {
      expect(searchApi.searchInspections.mock.calls.length).toBeGreaterThan(searchesBefore);
    });
  });

  it('shows the server’s reason when the delete is refused', async () => {
    // A 409 means the inspection stopped being offline while the results were on screen. The status
    // it names is the only way the user learns that — the table they are looking at is stale.
    searchApi.deleteInspection.mockRejectedValue({
      body: { detail: 'Inspection 42 is SUB and cannot be deleted.' },
    });
    await openDeleteConfirmation();

    fireEvent.click(screen.getByText('Delete'));

    await waitFor(() => {
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({
          kind: 'error',
          title: 'The inspection was not deleted',
          subtitle: 'Inspection 42 is SUB and cannot be deleted.',
        }),
      );
    });
  });

  it('falls back to its own wording when the failure carries no explanation', async () => {
    searchApi.deleteInspection.mockRejectedValue({ body: null });
    await openDeleteConfirmation();

    fireEvent.click(screen.getByText('Delete'));

    await waitFor(() => {
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({ subtitle: expect.stringMatching(/could not be deleted/i) }),
      );
    });
  });

  it('offers no delete at all to a user without the capability', async () => {
    // Legacy wraps the control in <cbr:authorize grantedAction="/deleteInspection">. The backend
    // refuses as well; this is the half that stops the action being offered in the first place.
    searchApi.searchInspections.mockResolvedValue(page([offlineRow]));
    renderPage(false);
    type('siteId', '12345');
    search();
    await screen.findByTestId('inspection-search-results');

    expect(screen.queryByTestId(`inspection-delete-${offlineRow.id}`)).toBeNull();
  });

  it('offers no delete on a row that is not offline', async () => {
    // Only an offline inspection can be deleted, and the server enforces it — but the control is
    // not offered either, so the refusal is not something a user has to discover.
    searchApi.searchInspections.mockResolvedValue(page([inspectionRow]));
    renderPage(true);
    type('siteId', '12345');
    search();
    await screen.findByTestId('inspection-search-results');

    expect(screen.queryByTestId(`inspection-delete-${inspectionRow.id}`)).toBeNull();
  });
});
