import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { page, userEvent } from '@vitest/browser/context';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import StructureSearchPage from './index';

const api = vi.hoisted(() => ({
  getStructureTypeClassCodes: vi.fn(),
  getSuperstructureTypeCodes: vi.fn(),
  getStructureCurbTypeCodes: vi.fn(),
  getCulvertTypeCodes: vi.fn(),
  getSiteStatusCodes: vi.fn(),
  getSiteTypeCodes: vi.fn(),
  getSpecialAccessCodes: vi.fn(),
  getSpecialEquipmentCodes: vi.fn(),
  getForestDistricts: vi.fn(),
  getManagementAreas: vi.fn(),
}));

const authorization = vi.hoisted(() => ({ canEdit: false, canDelete: false }));
vi.mock('@/hooks/useAuthorization', () => ({
  useAuthorization: () => authorization,
}));

vi.mock('@/context/pageTitle/usePageTitle', () => ({
  usePageTitle: () => ({ setPageTitle: vi.fn(), pageTitle: '' }),
}));

// Mocked at the service rather than the hook, so the query key and the error branch are exercised.
const structureSearchApi = vi.hoisted(() => ({
  searchStructures: vi.fn(),
  archiveStructures: vi.fn(),
  deleteStructure: vi.fn(),
  updateRepairResponsibility: vi.fn(),
}));

// The outcome of an archive is a toast; asserted on what the page asked to show.
const display = vi.hoisted(() => vi.fn());
vi.mock('@/context/notification/useNotification', () => ({
  useNotification: () => ({ display }),
}));
const clientApi = vi.hoisted(() => ({ searchClients: vi.fn(), clientLocations: vi.fn() }));

vi.mock('@/services/APIs', () => ({
  default: { configuration: api, structureSearch: structureSearchApi, client: clientApi },
}));

const renderPage = (canEdit = false, canDelete = false, url = '/inventory/structure-search') => {
  authorization.canEdit = canEdit;
  authorization.canDelete = canDelete;
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[url]}>
        <StructureSearchPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
};

const emptyPage = (pageSize = 20) => ({
  content: [],
  totalElements: 0,
  totalPages: 0,
  pageNumber: 0,
  pageSize,
});

const structure = (id: string, overrides: Record<string, unknown> = {}) => ({
  id,
  structureName: `B${id}`,
  siteId: '12345',
  orgUnitCode: 'DPG',
  orgUnitName: 'Prince George Natural Resource District',
  forestServiceRoad: 'Bowron FSR',
  kilometres: '12.50',
  crossingName: 'Deadman Creek',
  structureTypeClass: 'PB - Permanent Bridge',
  forestFileId: 'R00123',
  roadSectionId: '01',
  clientNumber: '00001012',
  clientLocationCode: '01',
  clientName: 'CANFOR CORPORATION',
  ...overrides,
});

const search = () => fireEvent.click(screen.getByTestId('structure-search-submit'));

const searchAndWait = async () => {
  search();
  await waitFor(() => {
    expect(screen.getByTestId('structure-search-results')).toBeInTheDocument();
  });
};

/** The criteria the page sent on the most recent search. */
const sentCriteria = () => structureSearchApi.searchStructures.mock.calls.at(-1)?.[0];

beforeEach(() => {
  Object.values(api).forEach((fn) => {
    fn.mockReset();
    fn.mockResolvedValue([]);
  });
  structureSearchApi.searchStructures.mockReset();
  structureSearchApi.searchStructures.mockResolvedValue(emptyPage());
  structureSearchApi.archiveStructures.mockReset();
  structureSearchApi.archiveStructures.mockResolvedValue({ archivedCount: 0 });
  structureSearchApi.deleteStructure.mockReset();
  structureSearchApi.deleteStructure.mockResolvedValue(undefined);
  structureSearchApi.updateRepairResponsibility.mockReset();
  structureSearchApi.updateRepairResponsibility.mockResolvedValue({
    structureCount: 0,
    siteCount: 0,
  });
  display.mockClear();
  // The page keeps its search for the tab; without this one test's search greets the next.
  sessionStorage.clear();
  clientApi.searchClients.mockReset();
  clientApi.searchClients.mockResolvedValue([]);
  clientApi.clientLocations.mockReset();
  clientApi.clientLocations.mockResolvedValue([]);
});

describe('StructureSearchPage — criteria form', () => {
  it('offers every criterion the legacy form does, bar User Kilometres', () => {
    renderPage();

    for (const field of [
      'structureName',
      'downrated',
      'structureTypeClassCode',
      'portableStructure',
      'superstructureTypeCode',
      'structureCurbTypeCode',
      'culvertTypeCode',
      'incomplete',
      'siteId',
      'siteStatusCode',
      'forestFileId',
      'roadSectionId',
      'siteTypeCode',
      'forestServiceRoad',
      'kiloStart',
      'kiloEnd',
      'crossingName',
      'orgUnit',
      'includeArchived',
      'managementOrgUnit',
      'maintainer',
      'clientLocationCode',
      'loadRestrictionYearStart',
      'loadRestrictionYearEnd',
      'replacementYearStart',
      'replacementYearEnd',
      'closureYearStart',
      'closureYearEnd',
      'yearBuiltStart',
      'yearBuiltEnd',
      'specialAccessCode',
      'specialEquipmentCode',
    ]) {
      expect(screen.getByTestId(`structure-search-${field}`)).toBeInTheDocument();
    }
    // Dropped from Site Search, so dropped here too; the backend still accepts it.
    expect(screen.queryByTestId('structure-search-userKmStart')).not.toBeInTheDocument();
  });

  it('is no longer badged as under construction', () => {
    renderPage();

    expect(screen.queryByText('Under construction')).not.toBeInTheDocument();
  });

  it('keeps the legacy labels', () => {
    renderPage();

    expect(screen.getByLabelText('Structure #')).toBeInTheDocument();
    expect(screen.getByLabelText('Type/Class')).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: /Downrated Structure\?/ })).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: /Portable Structure\?/ })).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: /Incomplete Data\?/ })).toBeInTheDocument();
    expect(
      screen.getByRole('switch', { name: /Include Archived Structure\?/ }),
    ).toBeInTheDocument();
    expect(screen.getByText('Estimated Load Restriction')).toBeInTheDocument();
    expect(screen.getByText('Estimated Replacement')).toBeInTheDocument();
    expect(screen.getByText('Estimated Closure')).toBeInTheDocument();
    expect(screen.getByText('Year Superstructure Installed')).toBeInTheDocument();
  });

  it('leaves archived structures out by default', () => {
    renderPage();

    expect(screen.getByRole('switch', { name: /Include Archived Structure\?/ })).not.toBeChecked();
  });

  it('validates a year bound beside its box, and does not search', async () => {
    renderPage();

    fireEvent.change(screen.getByTestId('structure-search-closureYearStart'), {
      target: { value: '30' },
    });
    search();

    expect(await screen.findByText('Enter a year as yyyy, e.g. 2030')).toBeInTheDocument();
    expect(screen.getByTestId('structure-search-closureYearStart')).toHaveAttribute(
      'aria-invalid',
      'true',
    );
    expect(screen.getByTestId('structure-search-closureYearEnd')).not.toHaveAttribute(
      'aria-invalid',
      'true',
    );
    expect(structureSearchApi.searchStructures).not.toHaveBeenCalled();
  });

  it('validates a kilometre bound beside its box', async () => {
    renderPage();

    fireEvent.change(screen.getByTestId('structure-search-kiloEnd'), { target: { value: 'x' } });

    expect(await screen.findByText('Kilometres must be a number, e.g. 12.5')).toBeInTheDocument();
  });

  it('names each range box for a screen reader', () => {
    renderPage();

    expect(screen.getByLabelText('Estimated Closure From')).toBeInTheDocument();
    expect(screen.getByLabelText('Estimated Closure To')).toBeInTheDocument();
  });

  it('clears every criterion at once', () => {
    renderPage();
    const name = screen.getByTestId('structure-search-structureName');
    fireEvent.change(name, { target: { value: 'B100' } });
    const downrated = screen.getByRole('switch', { name: /Downrated Structure\?/ });
    fireEvent.click(downrated);

    fireEvent.click(screen.getByTestId('structure-search-reset'));

    expect(name).toHaveValue('');
    expect(downrated).not.toBeChecked();
  });
});

describe('StructureSearchPage — searching', () => {
  it('shows no results table until a search is run', () => {
    renderPage();

    expect(screen.queryByTestId('structure-search-results')).not.toBeInTheDocument();
  });

  it('opens already searched for a site when Site Detail sends one', async () => {
    // Display Structures on a site with several structures, as legacy's `location=site` does.
    renderPage(false, false, '/inventory/structure-search?siteId=BOWRON-001');

    await waitFor(() =>
      expect(structureSearchApi.searchStructures).toHaveBeenCalledWith(
        expect.objectContaining({ siteId: 'BOWRON-001' }),
        expect.anything(),
        expect.anything(),
        null,
      ),
    );
    expect(screen.getByTestId('structure-search-siteId')).toHaveValue('BOWRON-001');
  });

  it('does not search while the user is still typing', () => {
    renderPage();

    fireEvent.change(screen.getByTestId('structure-search-structureName'), {
      target: { value: 'B100' },
    });

    expect(structureSearchApi.searchStructures).not.toHaveBeenCalled();
  });

  it('sends the criteria the user submitted, toggles and years included', async () => {
    renderPage();
    fireEvent.change(screen.getByTestId('structure-search-structureName'), {
      target: { value: 'B100' },
    });
    fireEvent.click(screen.getByRole('switch', { name: /Downrated Structure\?/ }));
    fireEvent.click(screen.getByRole('switch', { name: /Include Archived Structure\?/ }));
    fireEvent.change(screen.getByTestId('structure-search-replacementYearStart'), {
      target: { value: '2030' },
    });

    await searchAndWait();

    expect(structureSearchApi.searchStructures).toHaveBeenCalledWith(
      expect.objectContaining({
        structureName: 'B100',
        downrated: true,
        includeArchived: true,
        replacementYearStart: '2030',
      }),
      0,
      20,
      null,
    );
  });

  it('shows the table, empty, once a search has been run', async () => {
    renderPage();

    await searchAndWait();

    expect(screen.getByText('No structures found.')).toBeInTheDocument();
  });

  it('says so when the search fails, rather than showing an empty table', async () => {
    structureSearchApi.searchStructures.mockRejectedValue(new Error('boom'));
    renderPage();

    search();

    expect(await screen.findByTestId('structure-search-error')).toBeInTheDocument();
    expect(screen.queryByText('No structures found.')).not.toBeInTheDocument();
  });

  it('asks for the next page zero-based, and goes back to page one on a new search', async () => {
    structureSearchApi.searchStructures.mockResolvedValue({
      ...emptyPage(),
      content: [structure('1')],
      totalElements: 45,
      totalPages: 3,
    });
    renderPage();
    await searchAndWait();

    fireEvent.click(screen.getByRole('button', { name: /next page/i }));
    await waitFor(() => {
      expect(structureSearchApi.searchStructures).toHaveBeenLastCalledWith(
        expect.anything(),
        1,
        20,
        null,
      );
    });

    search();
    await waitFor(() => {
      expect(structureSearchApi.searchStructures).toHaveBeenLastCalledWith(
        expect.anything(),
        0,
        20,
        null,
      );
    });
  });
});

describe('StructureSearchPage — results', () => {
  it('renders the legacy columns, with one Maintainer column', async () => {
    renderPage();
    await searchAndWait();

    // A sortable header also carries text only a screen reader hears, so the visible label is read.
    const headers = within(screen.getByTestId('structure-search-results'))
      .getAllByRole('columnheader')
      .map(
        (cell) => cell.querySelector('.cds--table-header-label')?.textContent ?? cell.textContent,
      );
    expect(headers).toEqual([
      'Structure #',
      'Site #',
      'District Code',
      'Forest Service Road',
      'KM',
      'Crossing Name',
      'Type/Class',
      'Project File ID#-Br.',
      'Maintainer',
    ]);
  });

  it('keeps every header on one line', async () => {
    renderPage(true);
    await searchAndWait();

    for (const header of within(screen.getByTestId('structure-search-results')).getAllByRole(
      'columnheader',
    )) {
      const label = header.querySelector('.cds--table-header-label') ?? header;
      expect(getComputedStyle(label).whiteSpace).toBe('nowrap');
    }
  });

  it('renders a row as the server returned it', async () => {
    structureSearchApi.searchStructures.mockResolvedValue({
      ...emptyPage(),
      content: [structure('7')],
      totalElements: 1,
      totalPages: 1,
    });
    renderPage();
    await searchAndWait();

    const row = within(screen.getByTestId('structure-row-7'));
    expect(row.getByText('B7')).toBeInTheDocument();
    expect(row.getByText('DPG — Prince George Natural Resource District')).toBeInTheDocument();
    expect(row.getByText('PB - Permanent Bridge')).toBeInTheDocument();
    expect(row.getByText('R00123-01')).toBeInTheDocument();
    expect(row.getByText('00001012-01 CANFOR CORPORATION')).toBeInTheDocument();
    expect(screen.getByText('Structures — 1 match')).toBeInTheDocument();
  });

  it('links the structure and the site to their detail pages', async () => {
    structureSearchApi.searchStructures.mockResolvedValue({
      ...emptyPage(),
      content: [structure('7')],
      totalElements: 1,
      totalPages: 1,
    });
    renderPage();
    await searchAndWait();

    // The site in a new tab, so the results stay put; the hidden text says so to a screen reader.
    const site = screen.getByRole('link', { name: '12345 (opens in a new tab)' });
    expect(site).toHaveAttribute('href', '/inventory/site/12345');
    expect(site).toHaveAttribute('target', '_blank');
    expect(site).toHaveAttribute('rel', 'noopener noreferrer');
    // By id, not by name: CROSSING_STRUCTURE_ID is the key, and a name is not unique.
    expect(screen.getByRole('link', { name: 'B7' })).toHaveAttribute(
      'href',
      '/inventory/structure/7',
    );
  });

  it('leaves the maintainer cell empty for a site with no maintainer', async () => {
    structureSearchApi.searchStructures.mockResolvedValue({
      ...emptyPage(),
      content: [structure('7', { clientNumber: '', clientLocationCode: '', clientName: '' })],
      totalElements: 1,
      totalPages: 1,
    });
    renderPage();
    await searchAndWait();

    const cells = within(screen.getByTestId('structure-row-7')).getAllByRole('cell');
    expect(cells.at(-1)).toHaveTextContent(/^$/);
  });
});

describe('StructureSearchPage — the dropdowns come from the server', () => {
  it('fills the structure lists from their own lookups', async () => {
    api.getSuperstructureTypeCodes.mockResolvedValue([{ code: 'STL', description: 'Steel' }]);
    api.getStructureCurbTypeCodes.mockResolvedValue([{ code: 'TMB', description: 'Timber' }]);
    api.getCulvertTypeCodes.mockResolvedValue([{ code: 'CSP', description: 'Corrugated' }]);
    api.getSpecialEquipmentCodes.mockResolvedValue([{ code: 'CRN', description: 'Crane' }]);
    renderPage();

    await waitFor(() => {
      expect(screen.getByTestId('structure-search-superstructureTypeCode')).toHaveTextContent(
        'STL - Steel',
      );
    });
    expect(screen.getByTestId('structure-search-structureCurbTypeCode')).toHaveTextContent(
      'TMB - Timber',
    );
    expect(screen.getByTestId('structure-search-culvertTypeCode')).toHaveTextContent(
      'CSP - Corrugated',
    );
    expect(screen.getByTestId('structure-search-specialEquipmentCode')).toHaveTextContent(
      'CRN - Crane',
    );
  });

  it('says some filters are missing when a lookup fails, and still searches', async () => {
    api.getCulvertTypeCodes.mockRejectedValue(new Error('boom'));
    renderPage();

    expect(await screen.findByTestId('structure-search-codes-error')).toBeInTheDocument();
    await searchAndWait();
  });

  it('clears the chosen management area when the district changes', async () => {
    api.getForestDistricts.mockResolvedValue([
      { orgUnitNo: '18', orgUnitCode: 'DPG', orgUnitName: 'Prince George' },
      { orgUnitNo: '21', orgUnitCode: 'DKA', orgUnitName: 'Thompson Rivers' },
    ]);
    api.getManagementAreas.mockResolvedValue([
      { orgUnitNo: '26', orgUnitCode: 'DRV', orgUnitName: 'Robson Valley' },
    ]);
    renderPage();
    await waitFor(() => {
      expect(screen.getByTestId('structure-search-orgUnit')).toHaveTextContent('Prince George');
    });
    fireEvent.change(screen.getByTestId('structure-search-orgUnit'), { target: { value: '18' } });
    await waitFor(() => {
      expect(screen.getByTestId('structure-search-managementOrgUnit')).toHaveTextContent(
        'Robson Valley',
      );
    });
    fireEvent.change(screen.getByTestId('structure-search-managementOrgUnit'), {
      target: { value: '26' },
    });

    fireEvent.change(screen.getByTestId('structure-search-orgUnit'), { target: { value: '21' } });

    expect(screen.getByTestId('structure-search-managementOrgUnit')).toHaveValue('');
  });
});

describe('StructureSearchPage — Designated Maintainer', () => {
  const canfor = {
    clientNumber: '00001012',
    clientLocnCode: '00',
    clientName: 'CANFOR CORPORATION',
    clientLocnName: null,
    city: 'Vancouver',
  };

  const maintainerField = () => screen.getByRole('combobox', { name: 'Designated Maintainer' });

  it('searches on the client number once a suggestion is picked', async () => {
    clientApi.searchClients.mockResolvedValue([canfor]);
    renderPage();

    await userEvent.fill(maintainerField(), 'canfor');
    await userEvent.click(await screen.findByText(/CANFOR CORPORATION/));
    await searchAndWait();

    expect(sentCriteria()).toMatchObject({ clientNumber: '00001012', primaryUserName: '' });
  });

  it('searches on the name when the text is not a pick', async () => {
    renderPage();

    await userEvent.fill(maintainerField(), 'canfor');
    await searchAndWait();

    expect(sentCriteria()).toMatchObject({ primaryUserName: 'canfor', clientNumber: '' });
  });
});

describe('StructureSearchPage — sorting by a header', () => {
  // Carbon puts a sortable header's own props on the button inside it, so the test id is the button.
  const sortBy = async (column: string) => {
    fireEvent.click(await screen.findByTestId(`structure-search-sort-${column}`));
    await screen.findByTestId('structure-search-results');
  };

  it('asks the server to sort every match, not the rows on screen', async () => {
    renderPage();
    await searchAndWait();

    await sortBy('TYPE_CLASS');

    await waitFor(() => {
      expect(structureSearchApi.searchStructures).toHaveBeenLastCalledWith(
        expect.anything(),
        0,
        20,
        { column: 'TYPE_CLASS', direction: 'ASC' },
      );
    });
  });

  it('turns descending on a second click and back to legacy order on a third', async () => {
    renderPage();
    await searchAndWait();

    await sortBy('STRUCTURE_NAME');
    await sortBy('STRUCTURE_NAME');
    await waitFor(() => {
      expect(structureSearchApi.searchStructures).toHaveBeenLastCalledWith(
        expect.anything(),
        0,
        20,
        { column: 'STRUCTURE_NAME', direction: 'DESC' },
      );
    });

    await sortBy('STRUCTURE_NAME');
    await waitFor(() => {
      expect(structureSearchApi.searchStructures).toHaveBeenLastCalledWith(
        expect.anything(),
        0,
        20,
        null,
      );
    });
  });

  it('goes back to page one when the order changes', async () => {
    structureSearchApi.searchStructures.mockResolvedValue({
      ...emptyPage(),
      content: [structure('1')],
      totalElements: 45,
      totalPages: 3,
    });
    renderPage();
    await searchAndWait();
    fireEvent.click(screen.getByLabelText('Next page'));
    await waitFor(() => {
      expect(structureSearchApi.searchStructures).toHaveBeenLastCalledWith(
        expect.anything(),
        1,
        20,
        null,
      );
    });

    await sortBy('MAINTAINER');

    await waitFor(() => {
      expect(structureSearchApi.searchStructures).toHaveBeenLastCalledWith(
        expect.anything(),
        0,
        20,
        { column: 'MAINTAINER', direction: 'ASC' },
      );
    });
  });

  it('forgets the order on Reset', async () => {
    renderPage();
    await searchAndWait();
    await sortBy('KILOMETRES');

    fireEvent.click(screen.getByTestId('structure-search-reset'));
    await searchAndWait();

    await waitFor(() => {
      expect(structureSearchApi.searchStructures).toHaveBeenLastCalledWith(
        expect.anything(),
        0,
        20,
        null,
      );
    });
  });
});

describe('StructureSearchPage — selecting structures', () => {
  const twoPages = () =>
    structureSearchApi.searchStructures.mockImplementation(
      (_criteria: unknown, pageNumber: number) =>
        Promise.resolve({
          ...emptyPage(),
          content: pageNumber === 0 ? [structure('1'), structure('2')] : [structure('3')],
          totalElements: 23,
          totalPages: 2,
        }),
    );

  const checkbox = (id: string) =>
    screen.getByRole('checkbox', { name: `Select structure B${id}` });

  it('offers no checkboxes to a reader', async () => {
    twoPages();
    renderPage();
    await searchAndWait();

    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
  });

  it('leaves out the selection bar until something is ticked', async () => {
    // Carbon's toolbar holds a full row even with its bar hidden — an empty band above the headers.
    twoPages();
    renderPage(true);
    await searchAndWait();

    expect(screen.queryByTestId('structure-search-selection')).not.toBeInTheDocument();
  });

  it('offers a checkbox per row to Level 1 and above, with no select-all', async () => {
    // Legacy gates the column on /deleteStructure or /updateRepairResponsibility, and has no
    // header checkbox.
    twoPages();
    renderPage(true);
    await searchAndWait();

    expect(checkbox('1')).not.toBeChecked();
    expect(checkbox('2')).not.toBeChecked();
    expect(screen.getAllByRole('checkbox')).toHaveLength(2);
  });

  it('counts the ticked structures', async () => {
    twoPages();
    renderPage(true);
    await searchAndWait();

    await userEvent.click(checkbox('1'));

    expect(checkbox('1')).toBeChecked();
    expect(await screen.findByText('1 structure selected')).toBeInTheDocument();
    expect(screen.getByTestId('structure-search-selection')).toHaveClass(
      'cds--batch-actions--active',
    );
  });

  it('keeps a tick across pages, and counts ticks on pages out of view', async () => {
    twoPages();
    renderPage(true);
    await searchAndWait();
    await userEvent.click(checkbox('1'));

    fireEvent.click(screen.getByLabelText('Next page'));
    await waitFor(() => {
      expect(screen.getByTestId('structure-row-3')).toBeInTheDocument();
    });
    await userEvent.click(checkbox('3'));
    expect(await screen.findByText('2 structures selected')).toBeInTheDocument();

    fireEvent.click(screen.getByLabelText('Previous page'));
    await waitFor(() => {
      expect(screen.getByTestId('structure-row-1')).toBeInTheDocument();
    });
    expect(checkbox('1')).toBeChecked();
    expect(checkbox('2')).not.toBeChecked();
  });

  it('keeps ticks when the order changes', async () => {
    twoPages();
    renderPage(true);
    await searchAndWait();
    await userEvent.click(checkbox('2'));

    fireEvent.click(await screen.findByTestId('structure-search-sort-STRUCTURE_NAME'));
    await screen.findByTestId('structure-search-results');

    await waitFor(() => {
      expect(checkbox('2')).toBeChecked();
    });
  });

  it('unticks on a second click', async () => {
    twoPages();
    renderPage(true);
    await searchAndWait();

    await userEvent.click(checkbox('1'));
    await userEvent.click(checkbox('1'));

    expect(checkbox('1')).not.toBeChecked();
    expect(screen.queryByTestId('structure-search-selection')).not.toBeInTheDocument();
  });

  it('clears every tick from the selection bar', async () => {
    twoPages();
    renderPage(true);
    await searchAndWait();
    await userEvent.click(checkbox('1'));
    await userEvent.click(checkbox('2'));

    await userEvent.click(screen.getByRole('button', { name: 'Clear selection' }));

    expect(checkbox('1')).not.toBeChecked();
    expect(checkbox('2')).not.toBeChecked();
  });

  it('clears the ticks on a new search, which is a different set of results', async () => {
    twoPages();
    renderPage(true);
    await searchAndWait();
    await userEvent.click(checkbox('1'));

    search();
    await waitFor(() => {
      expect(checkbox('1')).not.toBeChecked();
    });
  });
});

describe('StructureSearchPage — bulk action buttons', () => {
  const onePage = () =>
    structureSearchApi.searchStructures.mockResolvedValue({
      ...emptyPage(),
      content: [structure('1')],
      totalElements: 1,
      totalPages: 1,
    });

  const buttons = ['archive', 'delete', 'update-repair-responsibility'];

  it('offers none to a reader', async () => {
    onePage();
    renderPage();
    await searchAndWait();

    for (const name of buttons) {
      expect(screen.queryByTestId(`structure-search-${name}`)).not.toBeInTheDocument();
    }
  });

  it('offers Level 1 only Update Repair Responsibility', async () => {
    // Legacy gates Archive and Delete on /deleteStructure — Level 2 — and the update on
    // /updateRepairResponsibility.
    onePage();
    renderPage(true);
    await searchAndWait();

    expect(screen.getByTestId('structure-search-update-repair-responsibility')).toBeInTheDocument();
    expect(screen.queryByTestId('structure-search-archive')).not.toBeInTheDocument();
    expect(screen.queryByTestId('structure-search-delete')).not.toBeInTheDocument();
  });

  it('offers Level 2 all three', async () => {
    onePage();
    renderPage(true, true);
    await searchAndWait();

    expect(screen.getByTestId('structure-search-archive')).toHaveTextContent(/^Archive$/);
    expect(screen.getByTestId('structure-search-delete')).toHaveTextContent(/^Delete$/);
    expect(screen.getByTestId('structure-search-update-repair-responsibility')).toHaveTextContent(
      'Update Repair Responsibility',
    );
  });

  it('keeps them disabled until a structure is ticked', async () => {
    onePage();
    renderPage(true, true);
    await searchAndWait();
    for (const name of buttons) {
      expect(screen.getByTestId(`structure-search-${name}`)).toBeDisabled();
    }

    await userEvent.click(screen.getByRole('checkbox', { name: 'Select structure B1' }));

    for (const name of buttons) {
      expect(screen.getByTestId(`structure-search-${name}`)).toBeEnabled();
    }
  });

  it('sits on the title row, at its right-hand end', async () => {
    // Desktop width: on a narrow window the buttons wrap under the title on purpose.
    await page.viewport(1400, 900);
    onePage();
    renderPage(true, true);
    await searchAndWait();

    const title = screen.getByText('Structures — 1 match').getBoundingClientRect();
    const last = screen
      .getByTestId('structure-search-update-repair-responsibility')
      .getBoundingClientRect();
    const header = (
      document.querySelector('.structure-search__results-header') as HTMLElement
    ).getBoundingClientRect();

    expect(last.top).toBeLessThan(title.bottom);
    expect(last.bottom).toBeGreaterThan(title.top);
    expect(header.right - last.right).toBeLessThanOrEqual(32);
  });

  it('keeps the table named by its title', async () => {
    onePage();
    renderPage(true, true);
    await searchAndWait();

    expect(screen.getByRole('table', { name: 'Structures — 1 match' })).toBeInTheDocument();
  });
});

describe('StructureSearchPage — archiving', () => {
  const twoPages = () =>
    structureSearchApi.searchStructures.mockImplementation(
      (_criteria: unknown, pageNumber: number) =>
        Promise.resolve({
          ...emptyPage(),
          content: pageNumber === 0 ? [structure('1'), structure('2')] : [structure('3')],
          totalElements: 23,
          totalPages: 2,
        }),
    );

  const tick = (id: string) =>
    userEvent.click(screen.getByRole('checkbox', { name: `Select structure B${id}` }));

  const dialog = () => screen.getByRole('dialog', { name: 'Archive structures' });

  const startArchive = async (...ids: string[]) => {
    twoPages();
    renderPage(true, true);
    await searchAndWait();
    for (const id of ids) await tick(id);
    await userEvent.click(screen.getByTestId('structure-search-archive'));
  };

  it('asks first, in legacy words, naming how many', async () => {
    await startArchive('1', '2');

    expect(dialog()).toHaveTextContent(
      'Are you sure you would like to archive 2 selected structures? ' +
        'Archived structures cannot be recovered.',
    );
    expect(structureSearchApi.archiveStructures).not.toHaveBeenCalled();
  });

  it('names the count for one, too', async () => {
    await startArchive('1');

    expect(dialog()).toHaveTextContent('archive 1 selected structure?');
  });

  it('archives nothing when the dialog is cancelled, and keeps the ticks', async () => {
    await startArchive('1');

    await userEvent.click(within(dialog()).getByRole('button', { name: 'Cancel' }));

    expect(structureSearchApi.archiveStructures).not.toHaveBeenCalled();
    expect(screen.getByRole('checkbox', { name: 'Select structure B1' })).toBeChecked();
  });

  it('archives every ticked structure, including those on other pages', async () => {
    twoPages();
    renderPage(true, true);
    await searchAndWait();
    await tick('1');
    fireEvent.click(screen.getByLabelText('Next page'));
    await waitFor(() => {
      expect(screen.getByTestId('structure-row-3')).toBeInTheDocument();
    });
    await tick('3');
    await userEvent.click(screen.getByTestId('structure-search-archive'));

    await userEvent.click(within(dialog()).getByRole('button', { name: 'Archive' }));

    await waitFor(() => {
      expect(structureSearchApi.archiveStructures).toHaveBeenCalledWith(['1', '3']);
    });
  });

  it('reports the count, clears the ticks and re-runs the search', async () => {
    structureSearchApi.archiveStructures.mockResolvedValue({ archivedCount: 2 });
    await startArchive('1', '2');
    const searchesBefore = structureSearchApi.searchStructures.mock.calls.length;

    await userEvent.click(within(dialog()).getByRole('button', { name: 'Archive' }));

    await waitFor(() => {
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({ kind: 'success', title: '2 structures were archived' }),
      );
    });
    await waitFor(() => {
      expect(structureSearchApi.searchStructures.mock.calls.length).toBeGreaterThan(searchesBefore);
    });
    await waitFor(() => {
      expect(screen.getByRole('checkbox', { name: 'Select structure B1' })).not.toBeChecked();
    });
    // Carbon keeps a closed modal mounted; `is-visible` is what makes one open.
    await waitFor(() => {
      expect(document.querySelector('.cds--modal.is-visible')).toBeNull();
    });
  });

  it('says "1 structure was archived" for one', async () => {
    structureSearchApi.archiveStructures.mockResolvedValue({ archivedCount: 1 });
    await startArchive('1');

    await userEvent.click(within(dialog()).getByRole('button', { name: 'Archive' }));

    await waitFor(() => {
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({ title: '1 structure was archived' }),
      );
    });
  });

  it('reports a failure as a toast and keeps the ticks for another try', async () => {
    structureSearchApi.archiveStructures.mockRejectedValue(new Error('boom'));
    await startArchive('1');

    await userEvent.click(within(dialog()).getByRole('button', { name: 'Archive' }));

    await waitFor(() => {
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({ kind: 'error', title: 'The structures were not archived' }),
      );
    });
    expect(screen.getByRole('checkbox', { name: 'Select structure B1' })).toBeChecked();
  });
});

describe('StructureSearchPage — deleting', () => {
  /** B1 can be deleted; B2 has inspections and repairs; B3, on page two, can be deleted. */
  const twoPages = () =>
    structureSearchApi.searchStructures.mockImplementation(
      (_criteria: unknown, pageNumber: number) =>
        Promise.resolve({
          ...emptyPage(),
          content:
            pageNumber === 0
              ? [
                  structure('1', { deleteBlockers: [] }),
                  structure('2', { deleteBlockers: ['inspections', 'repairs'] }),
                ]
              : [structure('3', { deleteBlockers: [] })],
          totalElements: 23,
          totalPages: 2,
        }),
    );

  const checkbox = (id: string) =>
    screen.getByRole('checkbox', { name: `Select structure B${id}` });

  const dialog = () => screen.getByRole('dialog', { name: 'Delete structures' });

  const startDelete = async (...ids: string[]) => {
    twoPages();
    renderPage(true, true);
    await searchAndWait();
    for (const id of ids) await userEvent.click(checkbox(id));
    await userEvent.click(screen.getByTestId('structure-search-delete'));
  };

  const confirm = () => userEvent.click(within(dialog()).getByRole('button', { name: 'Delete' }));

  it('asks first, naming how many', async () => {
    await startDelete('1');

    expect(dialog()).toHaveTextContent(
      'Are you sure you would like to delete 1 selected structure? ' +
        'Deleted structures cannot be recovered.',
    );
    expect(structureSearchApi.deleteStructure).not.toHaveBeenCalled();
  });

  it('says beforehand which ticked structures will be skipped, and why', async () => {
    await startDelete('1', '2');

    expect(dialog()).toHaveTextContent('1 of 2 selected structures will be deleted.');
    expect(screen.getByTestId('structure-delete-skipped')).toHaveTextContent(
      'B2 has inspections and repairs',
    );
  });

  it('deletes nothing when cancelled', async () => {
    await startDelete('1');

    await userEvent.click(within(dialog()).getByRole('button', { name: 'Cancel' }));

    expect(structureSearchApi.deleteStructure).not.toHaveBeenCalled();
    expect(checkbox('1')).toBeChecked();
  });

  it('deletes only the structures that can be, one request each', async () => {
    await startDelete('1', '2');

    await confirm();

    await waitFor(() => {
      expect(structureSearchApi.deleteStructure).toHaveBeenCalledTimes(1);
    });
    expect(structureSearchApi.deleteStructure).toHaveBeenCalledWith('1');
  });

  it('deletes ticked structures on other pages too', async () => {
    twoPages();
    renderPage(true, true);
    await searchAndWait();
    await userEvent.click(checkbox('1'));
    fireEvent.click(screen.getByLabelText('Next page'));
    await waitFor(() => {
      expect(screen.getByTestId('structure-row-3')).toBeInTheDocument();
    });
    await userEvent.click(checkbox('3'));
    await userEvent.click(screen.getByTestId('structure-search-delete'));

    await confirm();

    await waitFor(() => {
      expect(structureSearchApi.deleteStructure).toHaveBeenCalledTimes(2);
    });
    expect(structureSearchApi.deleteStructure).toHaveBeenNthCalledWith(1, '1');
    expect(structureSearchApi.deleteStructure).toHaveBeenNthCalledWith(2, '3');
  });

  it('sends each delete only once the one before has finished', async () => {
    // One at a time on purpose, so a large selection does not reach the server as a burst.
    let finishFirst: () => void = () => {};
    structureSearchApi.deleteStructure.mockImplementationOnce(
      () =>
        new Promise<void>((done) => {
          finishFirst = done;
        }),
    );
    twoPages();
    renderPage(true, true);
    await searchAndWait();
    await userEvent.click(checkbox('1'));
    fireEvent.click(screen.getByLabelText('Next page'));
    await waitFor(() => {
      expect(screen.getByTestId('structure-row-3')).toBeInTheDocument();
    });
    await userEvent.click(checkbox('3'));
    await userEvent.click(screen.getByTestId('structure-search-delete'));

    await confirm();

    await waitFor(() => {
      expect(structureSearchApi.deleteStructure).toHaveBeenCalledTimes(1);
    });
    expect(structureSearchApi.deleteStructure).not.toHaveBeenCalledWith('3');

    finishFirst();

    await waitFor(() => {
      expect(structureSearchApi.deleteStructure).toHaveBeenCalledWith('3');
    });
  });

  it('reports what went and what was skipped, and keeps the skipped ones ticked', async () => {
    await startDelete('1', '2');

    await confirm();

    await waitFor(() => {
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({
          kind: 'success',
          title: '1 structure deleted',
          subtitle: '1 structure skipped, and still ticked.',
        }),
      );
    });
    // B2 can still be archived instead, without finding it again.
    await waitFor(() => {
      expect(checkbox('2')).toBeChecked();
    });
  });

  it('re-runs the search afterwards', async () => {
    await startDelete('1');
    const searchesBefore = structureSearchApi.searchStructures.mock.calls.length;

    await confirm();

    await waitFor(() => {
      expect(structureSearchApi.searchStructures.mock.calls.length).toBeGreaterThan(searchesBefore);
    });
  });

  it("shows the server's reason when it refuses one, and keeps it ticked", async () => {
    // Something was added since the search ran; the server checks again.
    structureSearchApi.deleteStructure.mockRejectedValue(
      Object.assign(new Error('Conflict'), {
        body: { detail: 'Structure B1 has inspections and cannot be deleted.' },
      }),
    );
    await startDelete('1');

    await confirm();

    await waitFor(() => {
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({
          kind: 'error',
          title: '1 structure not deleted',
          subtitle: 'Structure B1 has inspections and cannot be deleted.',
        }),
      );
    });
    expect(display).not.toHaveBeenCalledWith(expect.objectContaining({ kind: 'success' }));
    expect(checkbox('1')).toBeChecked();
  });

  it('words a server error itself, rather than showing what the server said', async () => {
    // A missing grant put Hibernate's exception text and the SQL in front of the user.
    structureSearchApi.deleteStructure.mockRejectedValue(
      Object.assign(new Error('Internal Server Error'), {
        status: 500,
        body: { message: 'Request processing failed: org.hibernate.exception.SQLGrammarException' },
      }),
    );
    await startDelete('1');

    await confirm();

    await waitFor(() => {
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({
          kind: 'error',
          title: '1 structure not deleted',
          subtitle: 'B1 could not be deleted.',
        }),
      );
    });
  });

  it('opens no dialog when nothing ticked can be deleted, and says why', async () => {
    await startDelete('2');

    expect(document.querySelector('.cds--modal.is-visible')).toBeNull();
    expect(display).toHaveBeenCalledWith(
      expect.objectContaining({
        kind: 'error',
        title: 'None of the selected structures can be deleted',
        subtitle: 'B2 has inspections and repairs.',
      }),
    );
  });
});

describe('StructureSearchPage — updating repair responsibility', () => {
  /** B1 and B2 stand on site S-1, B3 on S-2, B4 on no site. */
  const onePage = () =>
    structureSearchApi.searchStructures.mockResolvedValue({
      ...emptyPage(),
      content: [
        structure('1', { siteId: 'S-1' }),
        structure('2', { siteId: 'S-1' }),
        structure('3', { siteId: 'S-2' }),
        structure('4', { siteId: '' }),
      ],
      totalElements: 4,
      totalPages: 1,
    });

  const canfor = {
    clientNumber: '00001012',
    clientLocnCode: '00',
    clientName: 'CANFOR CORPORATION',
    clientLocnName: null,
    city: 'Vancouver',
  };

  const tick = (id: string) =>
    userEvent.click(screen.getByRole('checkbox', { name: `Select structure B${id}` }));

  const dialog = () => screen.getByRole('dialog', { name: 'Update repair responsibility' });

  const open = async (...ids: string[]) => {
    onePage();
    renderPage(true);
    await searchAndWait();
    for (const id of ids) await tick(id);
    await userEvent.click(screen.getByTestId('structure-search-update-repair-responsibility'));
  };

  const pickMaintainer = async () => {
    clientApi.searchClients.mockResolvedValue([canfor]);
    clientApi.clientLocations.mockResolvedValue([
      { ...canfor, clientLocnCode: '01', clientLocnName: 'NORTHERN', city: 'PRINCE GEORGE' },
    ]);
    await userEvent.fill(
      within(dialog()).getByRole('combobox', { name: 'Designated Maintainer' }),
      'canfor',
    );
    await userEvent.click(await within(dialog()).findByText(/CANFOR CORPORATION/));
    await waitFor(() => {
      expect(screen.getByTestId('repair-responsibility-location')).toBeEnabled();
    });
    await userEvent.selectOptions(screen.getByTestId('repair-responsibility-location'), '01');
  };

  const update = () => userEvent.click(within(dialog()).getByRole('button', { name: 'Update' }));

  it('is offered to Level 1, who cannot archive or delete', async () => {
    await open('1');

    expect(dialog()).toBeInTheDocument();
  });

  it('says how many sites will change, counting a shared site once', async () => {
    await open('1', '2', '3');

    expect(dialog()).toHaveTextContent(
      'This sets the maintainer of 2 sites. Every structure on those sites, ticked or not, ' +
        'will have the new maintainer.',
    );
  });

  it('says "that site" for one', async () => {
    await open('1', '2');

    expect(dialog()).toHaveTextContent(
      'This sets the maintainer of 1 site. Every structure on that site',
    );
  });

  it('says when a ticked structure stands on no site', async () => {
    await open('1', '4');

    expect(dialog()).toHaveTextContent(
      '1 structure selected stand on no site and will be skipped.',
    );
  });

  it('asks for a maintainer beside the field, and sends nothing without one', async () => {
    await open('1');

    await update();

    expect(within(dialog()).getByText('Designated Maintainer is required.')).toBeInTheDocument();
    expect(structureSearchApi.updateRepairResponsibility).not.toHaveBeenCalled();
  });

  it('puts the two pickers on one row, and its buttons bottom-right', async () => {
    await page.viewport(1400, 900);
    await open('1');

    const maintainer = within(dialog())
      .getByRole('combobox', { name: 'Designated Maintainer' })
      .getBoundingClientRect();
    const location = screen.getByTestId('repair-responsibility-location').getBoundingClientRect();
    // Within a few pixels: the combo box's input sits inside a bordered wrapper, the select does not.
    expect(Math.abs(location.top - maintainer.top)).toBeLessThanOrEqual(4);
    expect(location.left).toBeGreaterThan(maintainer.right);

    const cancel = within(dialog()).getByRole('button', { name: 'Cancel' });
    const update = within(dialog()).getByRole('button', { name: 'Update' });
    expect(cancel.getBoundingClientRect().top).toBeCloseTo(update.getBoundingClientRect().top, 0);
    expect(update.getBoundingClientRect().left).toBeGreaterThan(
      cancel.getBoundingClientRect().right,
    );
  });

  it('keeps the location disabled until a maintainer is picked', async () => {
    await open('1');

    expect(screen.getByTestId('repair-responsibility-location')).toBeDisabled();
  });

  it('sends every tick with the picked maintainer and location', async () => {
    structureSearchApi.updateRepairResponsibility.mockResolvedValue({
      structureCount: 3,
      siteCount: 2,
    });
    await open('1', '2', '3');
    await pickMaintainer();

    await update();

    await waitFor(() => {
      expect(structureSearchApi.updateRepairResponsibility).toHaveBeenCalledWith(
        ['1', '2', '3'],
        '00001012',
        '01',
      );
    });
    await waitFor(() => {
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({
          kind: 'success',
          title: '3 structures updated',
          subtitle: '2 sites now maintained by 00001012-01.',
        }),
      );
    });
    await waitFor(() => {
      expect(screen.getByRole('checkbox', { name: 'Select structure B1' })).not.toBeChecked();
    });
  });

  it('reports a refusal and keeps the ticks', async () => {
    structureSearchApi.updateRepairResponsibility.mockRejectedValue(
      Object.assign(new Error('Bad Request'), {
        status: 400,
        body: { detail: 'Designated maintainer 00001012-01 does not exist.' },
      }),
    );
    await open('1');
    await pickMaintainer();

    await update();

    await waitFor(() => {
      expect(display).toHaveBeenCalledWith(
        expect.objectContaining({
          kind: 'error',
          title: 'Repair responsibility was not updated',
          subtitle: 'Designated maintainer 00001012-01 does not exist.',
        }),
      );
    });
    expect(screen.getByRole('checkbox', { name: 'Select structure B1' })).toBeChecked();
  });
});

describe('StructureSearchPage — coming back to the search', () => {
  const searchFor = async (name: string) => {
    fireEvent.change(screen.getByTestId('structure-search-structureName'), {
      target: { value: name },
    });
    await searchAndWait();
  };

  it('finds the last search as it was left, results included', async () => {
    // Open a structure from the results, then return by the breadcrumb or the side nav.
    const first = renderPage();
    await searchFor('B100');
    first.unmount();
    structureSearchApi.searchStructures.mockClear();

    renderPage();

    expect(screen.getByTestId('structure-search-structureName')).toHaveValue('B100');
    await waitFor(() => {
      expect(structureSearchApi.searchStructures).toHaveBeenCalledWith(
        expect.objectContaining({ structureName: 'B100' }),
        0,
        20,
        null,
      );
    });
  });

  it('forgets it on Reset', async () => {
    const first = renderPage();
    await searchFor('B100');
    fireEvent.click(screen.getByTestId('structure-search-reset'));
    first.unmount();

    renderPage();

    expect(screen.getByTestId('structure-search-structureName')).toHaveValue('');
    expect(screen.queryByTestId('structure-search-results')).not.toBeInTheDocument();
  });

  it('runs the site Site Detail sent rather than the kept search', async () => {
    const first = renderPage();
    await searchFor('B100');
    first.unmount();

    renderPage(false, false, '/inventory/structure-search?siteId=BOWRON-001');

    expect(screen.getByTestId('structure-search-structureName')).toHaveValue('');
    expect(screen.getByTestId('structure-search-siteId')).toHaveValue('BOWRON-001');
  });

  it('opens blank when what was saved cannot be read', () => {
    sessionStorage.setItem('cbr.structureSearch', '{not json');

    renderPage();

    expect(screen.getByTestId('structure-search-structureName')).toHaveValue('');
    expect(screen.queryByTestId('structure-search-results')).not.toBeInTheDocument();
  });
});
