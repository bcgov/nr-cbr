import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import SiteSearchPage from './index';

import type { CodeOption } from '@/types/configuration';

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

// Mocked at the service rather than at the hook, so the query key, the fallback to an empty list
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
        <SiteSearchPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
};

const statuses: CodeOption[] = [
  { code: 'ACT', description: 'Active' },
  { code: 'DEC', description: 'Decommissioned' },
];

beforeEach(() => {
  Object.values(api).forEach((fn) => {
    fn.mockReset();
    fn.mockResolvedValue([]);
  });
});

const search = () => fireEvent.click(screen.getByTestId('site-search-submit'));

describe('SiteSearchPage — criteria form', () => {
  it('offers every criterion the legacy form does', () => {
    // Field-for-field with site_search.jsp. A missing criterion is a search someone can no longer
    // run, and there is no error to notice — the form just quietly does less.
    renderPage();

    for (const field of [
      'siteId',
      'siteStatusCode',
      'forestFileId',
      'roadSectionId',
      'structureInspectionStatusCode',
      'forestServiceRoad',
      'clientNumber',
      'crossingName',
      'clientLocationCode',
      'orgUnit',
      'kiloStart',
      'kiloEnd',
      'managementOrgUnit',
      'userKmStart',
      'userKmEnd',
      'specialAccessCode',
      'siteTypeCode',
      'primaryUserName',
      'incomplete',
      'capitalRoad',
    ]) {
      expect(screen.getByTestId(`site-search-${field}`)).toBeInTheDocument();
    }
  });

  it('keeps the legacy labels, which is the vocabulary the business uses', () => {
    renderPage();

    expect(screen.getByLabelText('Site #')).toBeInTheDocument();
    expect(screen.getByLabelText('Project File ID#')).toBeInTheDocument();
    expect(screen.getByLabelText('Designated Maintainer Client Number')).toBeInTheDocument();
    // The two filters are Carbon Toggles, which label a `switch` by aria-labelledby rather than a
    // <label for>. Asserting the role as well as the name keeps this honest about the control type.
    expect(screen.getByRole('switch', { name: /Incomplete Data\?/ })).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: /Capital Road/ })).toBeInTheDocument();
  });

  it('enforces the legacy maxlengths', () => {
    // These mirror the underlying column widths, so a longer value could never have matched.
    renderPage();

    expect(screen.getByTestId('site-search-siteId')).toHaveAttribute('maxlength', '14');
    expect(screen.getByTestId('site-search-clientLocationCode')).toHaveAttribute('maxlength', '2');
    expect(screen.getByTestId('site-search-primaryUserName')).toHaveAttribute('maxlength', '35');
  });

  it('records what the user types', () => {
    renderPage();
    const siteId = screen.getByTestId('site-search-siteId');

    fireEvent.change(siteId, { target: { value: '12345' } });

    expect(siteId).toHaveValue('12345');
  });

  it('toggles the two boolean filters', () => {
    renderPage();
    const incomplete = screen.getByRole('switch', { name: /Incomplete Data\?/ });
    expect(incomplete).not.toBeChecked();

    fireEvent.click(incomplete);

    expect(incomplete).toBeChecked();
  });

  it('clears every criterion at once', () => {
    renderPage();
    const siteId = screen.getByTestId('site-search-siteId');
    fireEvent.change(siteId, { target: { value: '12345' } });

    fireEvent.click(screen.getByTestId('site-search-reset'));

    expect(siteId).toHaveValue('');
  });
});

describe('SiteSearchPage — results', () => {
  it('shows no results table until a search is run', () => {
    // Legacy gates the whole block on `<c:if test="${search}">`. An empty table on an untouched
    // page reads as "no sites match", which is a different statement.
    renderPage();

    expect(screen.queryByTestId('site-search-results')).not.toBeInTheDocument();
  });

  it('shows the table, empty, once a search has been run', () => {
    renderPage();

    search();

    expect(screen.getByTestId('site-search-results')).toBeInTheDocument();
    expect(screen.getByText('No sites found.')).toBeInTheDocument();
  });

  it('renders the legacy columns in the legacy order', () => {
    renderPage();
    search();

    const headers = screen.getAllByRole('columnheader').map((cell) => cell.textContent);
    expect(headers).toEqual([
      'Site #',
      'District Code',
      'Forest Service Road',
      'KM',
      'Crossing Name',
      'Project File ID#-Br.',
      'Status',
    ]);
  });
});

describe('SiteSearchPage — the Delete column is privilege-gated', () => {
  it('is absent for a user without the destructive capability', () => {
    // Legacy wraps this column in <cbr:authorize grantedAction="/deleteSite">. An action you cannot
    // perform is never rendered — not disabled (cbr-navigation.local.md §2).
    renderPage(false);
    search();

    const headers = screen.getAllByRole('columnheader').map((cell) => cell.textContent);
    expect(headers).not.toContain('Delete');
  });

  it('is present for CBR_LEVEL_2 and above', () => {
    renderPage(true);
    search();

    const headers = screen.getAllByRole('columnheader').map((cell) => cell.textContent);
    expect(headers).toContain('Delete');
  });
});

describe('SiteSearchPage — the dropdowns come from the server', () => {
  it('fills every parameterless list from its own lookup', async () => {
    // Six lists, six near-identical wirings: crossing two of them produces a form that renders
    // fine and filters on the wrong column. Distinct values per list are what catch that.
    api.getStructureInspectionStatusCodes.mockResolvedValue([
      { code: 'INS', description: 'From the inspection lookup' },
    ]);
    api.getSpecialAccessCodes.mockResolvedValue([
      { code: 'ACC', description: 'From the access lookup' },
    ]);
    api.getSiteTypeCodes.mockResolvedValue([{ code: 'TYP', description: 'From the type lookup' }]);
    api.getForestDistricts.mockResolvedValue([
      { orgUnitNo: '18', orgUnitName: 'From the district lookup' },
    ]);
    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId('site-search-structureInspectionStatusCode')).toHaveTextContent(
        'From the inspection lookup',
      );
    });
    expect(screen.getByTestId('site-search-specialAccessCode')).toHaveTextContent(
      'From the access lookup',
    );
    expect(screen.getByTestId('site-search-siteTypeCode')).toHaveTextContent(
      'From the type lookup',
    );
    expect(screen.getByTestId('site-search-orgUnit')).toHaveTextContent('From the district lookup');
  });

  it('leaves Management Area empty until a district is chosen', async () => {
    api.getForestDistricts.mockResolvedValue([{ orgUnitNo: '18', orgUnitName: 'Prince George' }]);
    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId('site-search-orgUnit')).toHaveTextContent('Prince George');
    });
    expect(api.getManagementAreas).not.toHaveBeenCalled();
  });

  it('loads the management areas of the district that was picked', async () => {
    api.getForestDistricts.mockResolvedValue([{ orgUnitNo: '18', orgUnitName: 'Prince George' }]);
    api.getManagementAreas.mockResolvedValue([{ orgUnitNo: '26', orgUnitName: 'Robson Valley' }]);
    renderPage();
    await waitFor(() => {
      expect(screen.getByTestId('site-search-orgUnit')).toHaveTextContent('Prince George');
    });

    fireEvent.change(screen.getByTestId('site-search-orgUnit'), { target: { value: '18' } });

    await waitFor(() => {
      expect(screen.getByTestId('site-search-managementOrgUnit')).toHaveTextContent(
        'Robson Valley',
      );
    });
    expect(api.getManagementAreas).toHaveBeenCalledWith('18');
  });

  it('clears the chosen management area when the district changes', async () => {
    // The areas belong to the old district. Keeping the selection would submit a filter that is
    // not in the list the user can now see — and they would have no way to tell it was applied.
    api.getForestDistricts.mockResolvedValue([
      { orgUnitNo: '18', orgUnitName: 'Prince George' },
      { orgUnitNo: '21', orgUnitName: 'Thompson Rivers' },
    ]);
    api.getManagementAreas.mockResolvedValue([{ orgUnitNo: '26', orgUnitName: 'Robson Valley' }]);
    renderPage();
    await waitFor(() => {
      expect(screen.getByTestId('site-search-orgUnit')).toHaveTextContent('Prince George');
    });
    fireEvent.change(screen.getByTestId('site-search-orgUnit'), { target: { value: '18' } });
    await waitFor(() => {
      expect(screen.getByTestId('site-search-managementOrgUnit')).toHaveTextContent(
        'Robson Valley',
      );
    });
    fireEvent.change(screen.getByTestId('site-search-managementOrgUnit'), {
      target: { value: '26' },
    });
    expect(screen.getByTestId('site-search-managementOrgUnit')).toHaveValue('26');

    fireEvent.change(screen.getByTestId('site-search-orgUnit'), { target: { value: '21' } });

    expect(screen.getByTestId('site-search-managementOrgUnit')).toHaveValue('');
  });
});

describe('SiteSearchPage — the Status list comes from the server', () => {
  it('fills the Status select with what the endpoint returned', async () => {
    api.getSiteStatusCodes.mockResolvedValue(statuses);
    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId('site-search-siteStatusCode')).toHaveTextContent('Active');
    });
    expect(screen.getByTestId('site-search-siteStatusCode')).toHaveTextContent('Decommissioned');
  });

  it('keeps the display order the server sent, rather than sorting', async () => {
    // CROSSING_SITE_STATUS_XREF.DISPLAY_ORDER exists for no other purpose; re-sorting anywhere in
    // the chain silently throws it away, and alphabetical order looks plausible enough to pass
    // review.
    api.getSiteStatusCodes.mockResolvedValue([
      { code: 'Z', description: 'Zed, shown first' },
      { code: 'A', description: 'Ay, shown second' },
    ]);
    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId('site-search-siteStatusCode')).toHaveTextContent(
        'Zed, shown first',
      );
    });
    const options = Array.from(
      screen.getByTestId('site-search-siteStatusCode').querySelectorAll('option'),
    ).map((option) => option.value);
    expect(options).toEqual(['', 'Z', 'A']);
  });

  it('keeps the blank first option, which means "do not filter on status"', async () => {
    api.getSiteStatusCodes.mockResolvedValue(statuses);
    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId('site-search-siteStatusCode')).toHaveTextContent('Active');
    });
    expect(screen.getByTestId('site-search-siteStatusCode')).toHaveValue('');
  });

  it('still lets the user search when the lookup fails', async () => {
    // Every criterion is optional, so a missing Status list is a lost filter, not a lost page.
    // Blocking the form on it would turn a reference-data outage into an outage of the screen.
    api.getSiteStatusCodes.mockRejectedValue(new Error('500'));
    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId('site-search-codes-error')).toBeInTheDocument();
    });
    expect(screen.getByText('Some filters could not be loaded')).toBeInTheDocument();
    search();
    expect(screen.getByTestId('site-search-results')).toBeInTheDocument();
  });
});

describe('SiteSearchPage — backend status', () => {
  it('says plainly that the search itself is not wired yet', () => {
    // Running a search returns nothing and five of the six dropdowns are still empty. Without this
    // the page looks broken rather than unfinished.
    renderPage();

    expect(screen.getByText('Search is not connected yet')).toBeInTheDocument();
  });
});
