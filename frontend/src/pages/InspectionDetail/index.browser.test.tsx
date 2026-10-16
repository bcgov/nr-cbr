import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import InspectionDetailPage from './index';

import type { InspectionDetailResponse } from './inspectionDetail';
import type { InspectionFormResponse } from '@/pages/NewInspection/inspectionForm';

const authorization = vi.hoisted(() => ({
  canWriteInspection: true,
  canEdit: true,
  canDelete: true,
  isPeng: true,
}));
vi.mock('@/hooks/useAuthorization', () => ({ useAuthorization: () => authorization }));
vi.mock('@/context/notification/useNotification', () => ({
  useNotification: () => ({ display: vi.fn() }),
}));
vi.mock('@/context/pageTitle/usePageTitle', () => ({
  usePageTitle: () => ({ setPageTitle: vi.fn(), pageTitle: '' }),
}));

const inspectionApi = vi.hoisted(() => ({
  getInspection: vi.fn(),
  getInspectionForm: vi.fn(),
  deleteInspectionRepair: vi.fn(),
  deleteInspectionMonitor: vi.fn(),
}));
const structureApi = vi.hoisted(() => ({
  getStructure: vi.fn(),
  getDocuments: vi.fn(),
  getStructureRepairs: vi.fn(),
  getStructureMonitors: vi.fn(),
}));
vi.mock('@/services/APIs', () => ({
  default: { inspectionSearch: inspectionApi, structureSearch: structureApi },
}));

const inspection = (
  overrides: Partial<InspectionDetailResponse> = {},
): InspectionDetailResponse => ({
  id: '41',
  structureId: '7',
  structureName: 'B100',
  siteAtTimeOfInspection: '62-145',
  type: { code: 'ROUT', description: 'Routine' },
  status: { code: 'RVD', description: 'Reviewed' },
  statusHistory: [
    {
      status: { code: 'RVD', description: 'Reviewed' },
      changedBy: 'IDIR\\PAT',
      changedAt: '2024-06-02T09:00:00',
    },
    {
      status: { code: 'SUB', description: 'Submitted' },
      changedBy: 'IDIR\\JO',
      changedAt: '2024-05-20T09:00:00',
    },
  ],
  inspectionDate: '2024-05-01',
  inspectorName: 'Jo Smith',
  inspectorCompanyName: 'Bridges Ltd',
  cost: 1200,
  professionalInspectionRequired: true,
  inspectionComment: 'All sound.',
  review: {
    reviewedBy: 'Pat Engineer',
    reviewedDate: '2024-06-02',
    loadRating: 55,
    loadRatingReason: { code: 'SD', description: 'Structural deterioration' },
    loadRatingReasonComment: null,
    pengComment: 'Agreed.',
  },
  hazards: {
    presentWaterDepth: 1.5,
    presentWaterWidth: null,
    highWaterClearance: 3,
    highWater: false,
    debris: true,
    scour: false,
    ice: false,
    aggradation: false,
    comment: 'Some debris at the pier.',
  },
  items: [
    {
      subsectionCode: 'APP',
      questionCode: 'APPQ',
      spanId: null,
      pierId: null,
      stringerNumber: null,
      answer: 'P',
      comment: 'Potholes',
    },
    {
      subsectionCode: 'STR',
      questionCode: 'PEEL',
      spanId: '702',
      pierId: null,
      stringerNumber: 3,
      answer: 'Yes',
      comment: null,
    },
    {
      subsectionCode: 'OLD',
      questionCode: 'GONE',
      spanId: null,
      pierId: null,
      stringerNumber: null,
      answer: 'G',
      comment: null,
    },
  ],
  entered: { userId: 'IDIR\\JO', date: '2024-05-01' },
  updated: null,
  offline: false,
  ...overrides,
});

const form: InspectionFormResponse = {
  structureId: '7',
  subsections: [
    {
      code: 'APP',
      description: 'Approaches',
      scope: 'STRUCTURE',
      questions: [
        {
          code: 'APPQ',
          description: 'Approach condition',
          perStringer: false,
          answers: [
            { code: 'G', description: 'Good' },
            { code: 'P', description: 'Poor' },
          ],
        },
        { code: 'GR', description: 'Guardrail', perStringer: false, answers: [] },
      ],
    },
    {
      code: 'STR',
      description: 'Stringers',
      scope: 'SPAN',
      questions: [{ code: 'PEEL', description: 'Peeled', perStringer: true, answers: [] }],
    },
  ],
  spans: [
    { id: '701', number: 1 },
    { id: '702', number: 2 },
  ],
  piers: [],
};

const page = (content: object[]) => ({
  page: { content, totalElements: content.length, totalPages: 1, pageNumber: 0, pageSize: 10 },
  beforeInstallCount: 0,
});

beforeEach(() => {
  inspectionApi.getInspection.mockReset();
  inspectionApi.getInspection.mockResolvedValue(inspection());
  inspectionApi.getInspectionForm.mockResolvedValue(form);
  structureApi.getStructure.mockResolvedValue({
    id: '7',
    structureName: 'B100',
    typeClass: { code: 'PB', description: 'Durable Bridge' },
    details: { yearBuilt: 2000 },
    site: null,
    outstanding: [],
  });
  structureApi.getDocuments.mockResolvedValue({ documents: [] });
  structureApi.getStructureRepairs.mockReset();
  structureApi.getStructureRepairs.mockResolvedValue(page([]));
  structureApi.getStructureMonitors.mockReset();
  structureApi.getStructureMonitors.mockResolvedValue(page([]));
});

const renderPage = () =>
  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <MemoryRouter initialEntries={['/inspection/41']}>
        <Routes>
          <Route path="/inspection/:inspectionId" element={<InspectionDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );

const openTab = (key: string) => userEvent.click(screen.getByTestId(`inspection-tab-${key}`));

describe('InspectionDetailPage', () => {
  it('names the inspection by its date and structure, with its status', async () => {
    renderPage();

    expect(
      await screen.findByRole('heading', { name: 'Inspection May 1, 2024 — B100' }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('inspection-status')).toMatchTextContent('Reviewed');
  });

  it('shows the structure, the inspection and its comments on the Inspection tab', async () => {
    renderPage();

    const tab = await screen.findByTestId('inspection-inspection');
    await waitFor(() => expect(tab).toMatchTextContent(/Durable Bridge/));
    expect(within(tab).getByRole('link', { name: /B100/ })).toHaveAttribute(
      'href',
      '/inventory/structure/7',
    );
    expect(tab).toMatchTextContent(/62-145/);
    expect(tab).toMatchTextContent(/Routine/);
    expect(tab).toMatchTextContent(/Jo Smith/);
    expect(tab).toMatchTextContent(/Bridges Ltd/);
    expect(tab).toMatchTextContent(/\$1,200/);
    expect(screen.getByTestId('inspection-comment')).toMatchTextContent('All sound.');
  });

  it("shows what it lacks as the structure page does: a notice, each tab's count and list", async () => {
    // Poor without its comment; the present water width blank. Guardrail, unanswered, is not
    // required — legacy never requires an answer.
    inspectionApi.getInspection.mockResolvedValue(
      inspection({
        items: [
          {
            subsectionCode: 'APP',
            questionCode: 'APPQ',
            spanId: null,
            pierId: null,
            stringerNumber: null,
            answer: 'P',
            comment: null,
          },
        ],
      }),
    );
    renderPage();

    // The Condition tab's count once the questions have loaded.
    await waitFor(() =>
      expect(screen.getByTestId('inspection-outstanding-summary')).toMatchTextContent(
        /2 required items outstanding across 2 tabs/,
      ),
    );
    expect(screen.getByRole('img', { name: 'Condition: 1 item outstanding' })).toBeInTheDocument();
    expect(
      screen.getByRole('img', { name: 'Hydraulic Hazards: 1 item outstanding' }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('inspection-condition-outstanding')).toMatchTextContent(
      /Approaches\s*Approach condition — comment/,
    );
    expect(screen.getByTestId('inspection-hazards-outstanding')).toMatchTextContent(
      /Estimated Present Water Level Width/,
    );
    expect(screen.queryByTestId('inspection-inspection-outstanding')).not.toBeInTheDocument();
  });

  it('says when it has no date yet', async () => {
    inspectionApi.getInspection.mockResolvedValue(
      inspection({ inspectionDate: null, structureName: null }),
    );
    renderPage();

    expect(
      await screen.findByRole('heading', { name: 'Inspection No date yet' }),
    ).toBeInTheDocument();
  });

  it('shows every question asked, each span and stringer, and keeps answers no longer asked', async () => {
    renderPage();
    await screen.findByTestId('inspection-inspection');
    await openTab('condition');

    const approaches = await screen.findByTestId('condition-APP');
    expect(within(approaches).getByTestId('answer-APPQ')).toMatchTextContent(
      /Approach condition\s*Poor\s*Potholes/,
    );
    // Not answered: shown, with a dash.
    expect(within(approaches).getByTestId('answer-GR')).toMatchTextContent(/Guardrail\s*—/);

    expect(screen.getByTestId('condition-STR-span-701')).toMatchTextContent(
      /No stringers recorded\./,
    );
    const span2 = screen.getByTestId('condition-STR-span-702');
    expect(within(span2).getByRole('heading', { name: 'Stringer 3' })).toBeInTheDocument();
    expect(span2).toMatchTextContent(/Peeled\s*Yes/);

    expect(screen.getByTestId('condition-other')).toMatchTextContent(/OLD · GONE/);
  });

  it('shows the hazards', async () => {
    renderPage();
    await screen.findByTestId('inspection-inspection');
    await openTab('hazards');

    const hazards = screen.getByTestId('inspection-section-hazards');
    expect(hazards).toMatchTextContent(/1\.5 m/);
    expect(hazards).toMatchTextContent(/Debris\?\s*Yes/);
    expect(screen.getByTestId('hazard-comment')).toMatchTextContent('Some debris at the pier.');
  });

  it('shows the review, its status history and load rating, and the PEng comments', async () => {
    renderPage();
    await screen.findByTestId('inspection-inspection');
    await openTab('review');

    const review = screen.getByTestId('inspection-review');
    expect(review).toMatchTextContent(/Pat Engineer/);
    expect(review).toMatchTextContent(/Structural deterioration/);
    expect(screen.getByTestId('status-history-0')).toMatchTextContent(/Reviewed\s*IDIR\\PAT/);
    expect(screen.getByTestId('status-history-1')).toMatchTextContent(/Submitted/);
    expect(screen.getByTestId('inspection-peng-comment')).toMatchTextContent('Agreed.');
  });

  it("lists the structure's outstanding repairs, read only, and this inspection's on request", async () => {
    renderPage();
    await screen.findByTestId('inspection-inspection');
    await openTab('repairs');

    await waitFor(() =>
      expect(structureApi.getStructureRepairs).toHaveBeenCalledWith(
        '7',
        'OUTSTANDING',
        0,
        10,
        false,
        '41',
      ),
    );
    expect(screen.queryByTestId('structure-repair-add')).not.toBeInTheDocument();

    await userEvent.click(
      within(screen.getByTestId('structure-repairs-tab')).getByText('Current Inspection Items'),
    );
    await waitFor(() =>
      expect(structureApi.getStructureRepairs).toHaveBeenLastCalledWith(
        '7',
        'CURRENT',
        0,
        10,
        false,
        '41',
      ),
    );
    expect(
      await screen.findByText('No outstanding repairs for this inspection.'),
    ).toBeInTheDocument();
  });

  it('lists the monitoring items and documents read only', async () => {
    renderPage();
    await screen.findByTestId('inspection-inspection');
    await openTab('monitoring');
    await waitFor(() => expect(structureApi.getStructureMonitors).toHaveBeenCalled());
    await userEvent.click(
      within(screen.getByTestId('structure-monitoring-tab')).getByText('Current Inspection Items'),
    );
    expect(await screen.findByText('No monitoring items for this inspection.')).toBeInTheDocument();

    await openTab('documents');
    await waitFor(() => expect(structureApi.getDocuments).toHaveBeenCalledWith('7'));
    expect(screen.queryByText('Add documents')).not.toBeInTheDocument();
  });

  describe('changing its repairs and monitoring items', () => {
    const repairRow = {
      id: '3',
      number: 3,
      status: { code: 'SUG', description: 'Suggested' },
      type: { code: 'DECK', description: 'Deck planks' },
      suggested: null,
      required: null,
      completed: null,
      inspectionId: '41',
      inspectionDate: '2024-05-01',
      priority: { code: 'H', description: 'High' },
      completedDate: null,
      estimate: null,
      actualCost: null,
      quantity: null,
      unit: null,
      description: null,
    };
    const inProgress = () => inspection({ status: { code: 'PRO', description: 'In Progress' } });

    it('lets Level 0 add and edit, as legacy, and Level 2 delete — through the inspection', async () => {
      authorization.canEdit = false;
      authorization.canDelete = true;
      inspectionApi.getInspection.mockResolvedValue(inProgress());
      inspectionApi.deleteInspectionRepair.mockResolvedValue(undefined);
      structureApi.getStructureRepairs.mockResolvedValue(page([repairRow]));
      renderPage();
      await screen.findByTestId('inspection-inspection');
      await openTab('repairs');

      expect(await screen.findByTestId('structure-repair-add')).toBeInTheDocument();
      expect(await screen.findByTestId('structure-repair-edit-3')).toBeInTheDocument();
      await userEvent.click(screen.getByTestId('structure-repair-delete-3'));
      await userEvent.click(await screen.findByRole('button', { name: 'Delete' }));

      await waitFor(() =>
        expect(inspectionApi.deleteInspectionRepair).toHaveBeenCalledWith('41', '3'),
      );
      expect(
        within(screen.getByTestId('structure-monitoring-tab')).getByTestId('structure-monitor-add'),
      ).toBeInTheDocument();
      authorization.canEdit = true;
    });

    it('offers no Delete below Level 2', async () => {
      authorization.canDelete = false;
      inspectionApi.getInspection.mockResolvedValue(inProgress());
      structureApi.getStructureRepairs.mockResolvedValue(page([repairRow]));
      renderPage();
      await screen.findByTestId('inspection-inspection');
      await openTab('repairs');

      expect(await screen.findByTestId('structure-repair-edit-3')).toBeInTheDocument();
      expect(screen.queryByTestId('structure-repair-delete-3')).not.toBeInTheDocument();
      authorization.canDelete = true;
    });

    it('changes nothing once the inspection is reviewed, and says why', async () => {
      structureApi.getStructureRepairs.mockResolvedValue(page([repairRow]));
      renderPage();
      await screen.findByTestId('inspection-inspection');
      await openTab('repairs');

      expect(await screen.findByTestId('structure-repairs-locked')).toMatchTextContent(
        /has been reviewed/,
      );
      expect(screen.queryByTestId('structure-repair-add')).not.toBeInTheDocument();
      expect(screen.queryByTestId('structure-repair-edit-3')).not.toBeInTheDocument();
      expect(screen.getByTestId('structure-monitors-locked')).toBeInTheDocument();
    });
  });

  it('says when the inspection does not exist', async () => {
    inspectionApi.getInspection.mockRejectedValue({ status: 404 });
    renderPage();

    expect(await screen.findByTestId('inspection-detail-error')).toMatchTextContent(
      /Inspection not found/,
    );
  });
});
