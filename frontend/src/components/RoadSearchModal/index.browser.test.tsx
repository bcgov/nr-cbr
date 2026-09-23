import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import RoadSearchModal from './index';

const roadApi = vi.hoisted(() => ({ searchRoads: vi.fn() }));
const clientApi = vi.hoisted(() => ({ searchClients: vi.fn() }));
vi.mock('@/services/APIs', () => ({ default: { road: roadApi, client: clientApi } }));

const bowron = {
  forestServiceRoad: 'Bowron FSR',
  forestFileId: 'R00123',
  roadSectionId: '01',
  tenureType: 'B40',
  clientName: 'CANFOR CORPORATION',
  clientNumber: '00001012',
};

/** A page and a half of roads, so paging has something to page. */
const manyRoads = (count: number) =>
  Array.from({ length: count }, (_, index) => ({
    ...bowron,
    forestFileId: `R${String(index).padStart(5, '0')}`,
    forestServiceRoad: `Road ${index}`,
  }));

const onSelect = vi.fn();
const onClose = vi.fn();

const renderModal = () => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <RoadSearchModal onSelect={onSelect} onClose={onClose} />
    </QueryClientProvider>,
  );
};

const criterion = (name: string, value: string) =>
  fireEvent.change(screen.getByTestId(`road-search-${name}`), { target: { value } });
const search = () => fireEvent.click(screen.getByTestId('road-search-submit'));

beforeEach(() => {
  onSelect.mockReset();
  onClose.mockReset();
  roadApi.searchRoads.mockReset();
  roadApi.searchRoads.mockResolvedValue([bowron]);
  clientApi.searchClients.mockReset();
  clientApi.searchClients.mockResolvedValue([]);
});

describe('RoadSearchModal', () => {
  it('offers every criterion the legacy dialog does', () => {
    renderModal();

    for (const name of ['forestServiceRoad', 'forestFileId', 'roadSectionId', 'tenureType']) {
      expect(screen.getByTestId(`road-search-${name}`)).toBeInTheDocument();
    }
    // One lookup where legacy has separate Client Name and Client # boxes, as the Designated
    // Maintainer field does — it searches on either.
    expect(screen.getByRole('combobox', { name: /^Client/ })).toBeInTheDocument();
  });

  it('offers only the clients that hold a road file', async () => {
    // A different set from the site maintainers the other combo box offers: a company may hold a
    // road file and maintain no site, and the two are reached through different tables.
    renderModal();

    fireEvent.change(screen.getByRole('combobox', { name: /^Client/ }), {
      target: { value: 'canfor' },
    });

    await waitFor(() => {
      expect(clientApi.searchClients).toHaveBeenCalledWith('canfor', 'ROAD_FILE_HOLDERS');
    });
  });

  it('narrows to one client number once a suggestion is picked', async () => {
    clientApi.searchClients.mockResolvedValue([
      {
        clientNumber: '00001012',
        clientLocnCode: null,
        clientName: 'CANFOR CORPORATION',
        clientLocnName: null,
        city: null,
      },
    ]);
    renderModal();
    fireEvent.change(screen.getByRole('combobox', { name: /^Client/ }), {
      target: { value: 'canfor' },
    });

    fireEvent.click(await screen.findByText(/CANFOR CORPORATION/));
    search();

    await waitFor(() => {
      expect(roadApi.searchRoads).toHaveBeenCalledWith(
        expect.objectContaining({ clientNumber: '00001012', clientName: '' }),
      );
    });
  });

  it('still searches on the name when the term resolves to nothing', async () => {
    // Several distinct clients are called Canfor, and no single pick covers them — this is what
    // the plain text box gave and a combo box would otherwise take away.
    renderModal();

    fireEvent.change(screen.getByRole('combobox', { name: /^Client/ }), {
      target: { value: 'canfor' },
    });
    search();

    await waitFor(() => {
      expect(roadApi.searchRoads).toHaveBeenCalledWith(
        expect.objectContaining({ clientName: 'canfor', clientNumber: '' }),
      );
    });
  });

  it('spells out the tenure type codes, which nothing in the database decodes', () => {
    renderModal();

    expect(screen.getByText(/B01 = RP, B40 = FSR/)).toBeInTheDocument();
  });

  it('searches nothing until asked', async () => {
    // Six partial matches over every road section in the province. A keystroke-driven search
    // would run the widest possible query the most often, which is why legacy has a button too.
    renderModal();

    criterion('forestServiceRoad', 'bowron');

    await waitFor(() => {
      expect(roadApi.searchRoads).not.toHaveBeenCalled();
    });
  });

  it('sends what was asked when Search is pressed', async () => {
    renderModal();
    criterion('forestServiceRoad', 'bowron');
    criterion('tenureType', 'B40');

    search();

    await waitFor(() => {
      expect(roadApi.searchRoads).toHaveBeenCalledWith(
        expect.objectContaining({ forestServiceRoad: 'bowron', tenureType: 'B40' }),
      );
    });
  });

  it('shows what came back, with the client that holds the file', async () => {
    renderModal();

    search();

    expect(await screen.findByTestId('road-search-results')).toBeInTheDocument();
    expect(screen.getByText('R00123')).toBeInTheDocument();
    expect(screen.getByText('CANFOR CORPORATION')).toBeInTheDocument();
  });

  it('picks the road from anywhere in the row', async () => {
    // The user is reading the whole row — the tenure type, the client — so having to travel back
    // to the first cell to act on it is work the dialog can spare them.
    renderModal();
    search();

    fireEvent.click(await screen.findByTestId('road-search-row-R00123-01'));

    expect(onSelect).toHaveBeenCalledWith(bowron);
  });

  it('picks it from a keyboard as well as a pointer', async () => {
    // The row is the control, and a row cannot be tabbed to or pressed on its own — so it is made
    // focusable and told what Enter and Space mean. Without this the dialog would be unusable
    // without a mouse.
    renderModal();
    search();
    const row = await screen.findByTestId('road-search-row-R00123-01');

    expect(row).toHaveAttribute('tabindex', '0');
    fireEvent.keyDown(row, { key: 'Enter' });

    expect(onSelect).toHaveBeenCalledWith(bowron);
  });

  it('leaves the road name as plain text', async () => {
    // The whole row is the control; underlining one cell of it promised something narrower, and
    // read as a link to somewhere this has never gone.
    renderModal();
    search();

    const cell = (await screen.findByText('Bowron FSR')).closest('td');

    expect(cell?.querySelector('a, button')).toBeNull();
  });

  it('hands back both halves of the pair when a road is picked', async () => {
    // They identify one section between them; setting either alone would leave the form
    // describing a road that does not exist.
    renderModal();
    search();
    fireEvent.click(await screen.findByTestId('road-search-row-R00123-01'));

    expect(onSelect).toHaveBeenCalledWith(bowron);
  });

  it('falls back to the pair when a road has no name to click on', async () => {
    roadApi.searchRoads.mockResolvedValue([{ ...bowron, forestServiceRoad: null }]);
    renderModal();

    search();

    expect(await screen.findByText('R00123-01')).toBeInTheDocument();
  });

  it('says so plainly when nothing matched', async () => {
    roadApi.searchRoads.mockResolvedValue([]);
    renderModal();

    search();

    expect(await screen.findByText('No roads found.')).toBeInTheDocument();
  });

  it('shows fifteen roads at a time', async () => {
    // A list the user has to scroll past the Search button to read is harder to scan than a short
    // one they can step through — and scanning for one road is the whole task here.
    roadApi.searchRoads.mockResolvedValue(manyRoads(40));
    renderModal();

    search();

    expect(await screen.findByTestId('road-search-results')).toBeInTheDocument();
    expect(screen.getByText('Road 0')).toBeInTheDocument();
    expect(screen.getByText('Road 14')).toBeInTheDocument();
    expect(screen.queryByText('Road 15')).not.toBeInTheDocument();
  });

  it('counts every match, not the page on screen', async () => {
    roadApi.searchRoads.mockResolvedValue(manyRoads(40));
    renderModal();

    search();

    expect(await screen.findByText('40 roads found')).toBeInTheDocument();
  });

  it('goes back to the first page when a new search is run', async () => {
    // Page three of the previous answer is an empty table in this one.
    roadApi.searchRoads.mockResolvedValue(manyRoads(40));
    renderModal();
    search();
    await screen.findByTestId('road-search-results');
    fireEvent.click(screen.getByLabelText('Next page'));
    expect(screen.getByText('Road 15')).toBeInTheDocument();

    search();

    await waitFor(() => {
      expect(screen.getByText('Road 0')).toBeInTheDocument();
    });
  });

  it('admits that a full page may be more than it shows', async () => {
    // The cap is the server's, so two hundred rows is indistinguishable from exactly two hundred
    // matches — which is what legacy's own wording admits.
    roadApi.searchRoads.mockResolvedValue(
      Array.from({ length: 200 }, (_, index) => ({
        ...bowron,
        forestFileId: `R${String(index).padStart(5, '0')}`,
      })),
    );
    renderModal();

    search();

    expect(await screen.findByText(/200 or more roads found/)).toBeInTheDocument();
  });

  it('reports a failed search rather than showing an empty table', async () => {
    roadApi.searchRoads.mockRejectedValue(new Error('The road view is unavailable.'));
    renderModal();

    search();

    expect(await screen.findByTestId('road-search-error')).toHaveTextContent(
      'The road view is unavailable.',
    );
  });

  it('closes on Cancel', () => {
    renderModal();

    fireEvent.click(screen.getByText('Cancel'));

    expect(onClose).toHaveBeenCalled();
  });
});
