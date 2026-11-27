import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import RoadSearchModal from './index';

const roadApi = vi.hoisted(() => ({ searchRoads: vi.fn(), getTenureTypes: vi.fn() }));
const configurationApi = vi.hoisted(() => ({ getForestDistricts: vi.fn() }));
vi.mock('@/services/APIs', () => ({
  default: { road: roadApi, configuration: configurationApi },
}));

const bowron = {
  forestServiceRoad: 'Bowron FSR',
  forestFileId: 'R00123',
  roadSectionId: '01',
  tenureType: 'FSR',
  districts: ['DPG'],
};

/** One page as the server answers it: these rows, out of `total` matches. */
const paged = <T,>(content: T[], total = content.length, pageNumber = 0, pageSize = 15) => ({
  content,
  totalElements: total,
  totalPages: Math.ceil(total / pageSize),
  pageNumber,
  pageSize,
});

/** Roads numbered from `from`, as one page of a larger answer would hold them. */
const roadsFrom = (from: number, count: number) =>
  Array.from({ length: count }, (_, index) => ({
    ...bowron,
    forestFileId: `R${String(from + index).padStart(5, '0')}`,
    forestServiceRoad: `Road ${from + index}`,
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
  roadApi.searchRoads.mockResolvedValue(paged([bowron]));
  roadApi.getTenureTypes.mockReset();
  roadApi.getTenureTypes.mockResolvedValue([
    { code: 'FSR', description: 'Forest Service Road' },
    { code: 'RP', description: 'Road Permit' },
  ]);
  configurationApi.getForestDistricts.mockReset();
  configurationApi.getForestDistricts.mockResolvedValue([
    { orgUnitNo: '18', orgUnitCode: 'DPG', orgUnitName: 'Prince George Natural Resource District' },
    { orgUnitNo: '19', orgUnitCode: 'DMH', orgUnitName: 'Mackenzie Natural Resource District' },
  ]);
});

describe('RoadSearchModal', () => {
  it('offers every criterion', () => {
    renderModal();

    for (const name of [
      'forestServiceRoad',
      'forestFileId',
      'roadSectionId',
      'tenureType',
      'district',
    ]) {
      expect(screen.getByTestId(`road-search-${name}`)).toBeInTheDocument();
    }
  });

  it("offers LRMOPS's tenure types, with their descriptions", async () => {
    renderModal();

    expect(
      await screen.findByRole('option', { name: 'FSR - Forest Service Road' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'RP - Road Permit' })).toBeInTheDocument();
  });

  it('searches on the district picked, by its org unit number — no client name', async () => {
    renderModal();
    await screen.findByRole('option', {
      name: 'DPG - Prince George Natural Resource District',
    });
    criterion('district', '18');

    search();

    await waitFor(() => {
      expect(roadApi.searchRoads).toHaveBeenCalledWith(
        expect.objectContaining({ district: '18' }),
        0,
        15,
      );
    });
    expect(roadApi.searchRoads.mock.calls[0][0]).not.toHaveProperty('clientName');
  });

  it("lists each road's districts — both, for one crossing a boundary", async () => {
    roadApi.searchRoads.mockResolvedValue(paged([{ ...bowron, districts: ['DMH', 'DPG'] }]));
    renderModal();

    search();

    expect(await screen.findByText('DMH, DPG')).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: /District/ })).toBeInTheDocument();
    expect(screen.queryByRole('columnheader', { name: /Client Name/ })).not.toBeInTheDocument();
  });

  it('says the road lookup is unavailable when LRMOPS cannot be asked', async () => {
    roadApi.searchRoads.mockRejectedValue(
      Object.assign(new Error('Service Unavailable'), { status: 503 }),
    );
    renderModal();

    search();

    expect(await screen.findByText('Road lookup is unavailable right now.')).toBeInTheDocument();
    expect(screen.getByTestId('road-search-error')).toBeInTheDocument();
  });

  it('searches nothing until asked', async () => {
    // Partial matches over every road section in LRMOPS. A keystroke-driven search
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
    await screen.findByRole('option', { name: 'FSR - Forest Service Road' });
    criterion('tenureType', 'FSR');

    search();

    await waitFor(() => {
      expect(roadApi.searchRoads).toHaveBeenCalledWith(
        expect.objectContaining({ forestServiceRoad: 'bowron', tenureType: 'FSR' }),
        0,
        15,
      );
    });
  });

  it('shows what came back, with the district it lies in', async () => {
    renderModal();

    search();

    expect(await screen.findByTestId('road-search-results')).toBeInTheDocument();
    expect(screen.getByText('R00123')).toBeInTheDocument();
    expect(screen.getByText('DPG')).toBeInTheDocument();
  });

  it('picks the road from anywhere in the row', async () => {
    // The user is reading the whole row — the tenure type, the district — so having to travel back
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
    roadApi.searchRoads.mockResolvedValue(paged([{ ...bowron, forestServiceRoad: null }]));
    renderModal();

    search();

    expect(await screen.findByText('R00123-01')).toBeInTheDocument();
  });

  it('says so plainly when nothing matched', async () => {
    roadApi.searchRoads.mockResolvedValue(paged([]));
    renderModal();

    search();

    expect(await screen.findByText('No roads found.')).toBeInTheDocument();
  });

  it('asks the server for fifteen roads at a time', async () => {
    // A list the user has to scroll past the Search button to read is harder to scan than a short
    // one they can step through — and scanning for one road is the whole task here.
    roadApi.searchRoads.mockResolvedValue(paged(roadsFrom(0, 15), 40));
    renderModal();

    search();

    expect(await screen.findByTestId('road-search-results')).toBeInTheDocument();
    expect(roadApi.searchRoads).toHaveBeenCalledWith(expect.anything(), 0, 15);
    expect(screen.getByText('Road 0')).toBeInTheDocument();
    expect(screen.getByText('Road 14')).toBeInTheDocument();
  });

  it('counts every match, not the page on screen', async () => {
    roadApi.searchRoads.mockResolvedValue(paged(roadsFrom(0, 15), 40));
    renderModal();

    search();

    expect(await screen.findByText('40 roads found')).toBeInTheDocument();
  });

  it('asks the server for the next page rather than slicing one it already has', async () => {
    roadApi.searchRoads.mockImplementation((_criteria, pageNumber: number) =>
      Promise.resolve(paged(roadsFrom(pageNumber * 15, 15), 40, pageNumber)),
    );
    renderModal();
    search();
    await screen.findByText('Road 0');

    fireEvent.click(screen.getByLabelText('Next page'));

    expect(await screen.findByText('Road 15')).toBeInTheDocument();
    expect(roadApi.searchRoads).toHaveBeenLastCalledWith(expect.anything(), 1, 15);
  });

  it('sorts by a header on the server, from the first page', async () => {
    roadApi.searchRoads.mockImplementation((_criteria, pageNumber: number) =>
      Promise.resolve(paged(roadsFrom(pageNumber * 15, 15), 40, pageNumber)),
    );
    renderModal();
    search();
    await screen.findByText('Road 0');
    fireEvent.click(screen.getByLabelText('Next page'));
    await screen.findByText('Road 15');

    fireEvent.click(screen.getByTestId('road-search-sort-TENURE_TYPE'));

    await vi.waitFor(() =>
      expect(roadApi.searchRoads).toHaveBeenLastCalledWith(expect.anything(), 0, 15, [
        {
          column: 'TENURE_TYPE',
          direction: 'ASC',
        },
      ]),
    );
  });

  it('goes back to the first page when a new search is run', async () => {
    // Page three of the previous answer is an empty table in this one.
    roadApi.searchRoads.mockImplementation((_criteria, pageNumber: number) =>
      Promise.resolve(paged(roadsFrom(pageNumber * 15, 15), 40, pageNumber)),
    );
    renderModal();
    search();
    await screen.findByText('Road 0');
    fireEvent.click(screen.getByLabelText('Next page'));
    await screen.findByText('Road 15');

    criterion('forestServiceRoad', 'road');
    search();

    await waitFor(() => {
      expect(roadApi.searchRoads).toHaveBeenLastCalledWith(
        expect.objectContaining({ forestServiceRoad: 'road' }),
        0,
        15,
      );
    });
    expect(await screen.findByText('Road 0')).toBeInTheDocument();
  });

  it('reaches past the two hundred roads legacy stopped at', async () => {
    // Legacy caps at 200 and can only say "200 or more". Paged on the server, the count is the
    // true one and the last page is as reachable as the first.
    roadApi.searchRoads.mockResolvedValue(paged(roadsFrom(0, 15), 205));
    renderModal();

    search();

    expect(await screen.findByText('205 roads found')).toBeInTheDocument();
  });

  it('reports a failed search rather than showing an empty table', async () => {
    roadApi.searchRoads.mockRejectedValue(new Error('The road view is unavailable.'));
    renderModal();

    search();

    expect(await screen.findByTestId('road-search-error')).toMatchTextContent(
      'The road view is unavailable.',
    );
  });

  it('closes on Cancel', () => {
    renderModal();

    fireEvent.click(screen.getByText('Cancel'));

    expect(onClose).toHaveBeenCalled();
  });
});
