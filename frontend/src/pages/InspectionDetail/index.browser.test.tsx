import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
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
  isInspectionReviewer: true,
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
  deleteInspectionDocument: vi.fn(),
  updateInspectionHazards: vi.fn(),
  updateInspection: vi.fn(),
  updateInspectionComment: vi.fn(),
  updateInspectionCondition: vi.fn(),
  reviewInspection: vi.fn(),
  updatePengComment: vi.fn(),
}));
const structureApi = vi.hoisted(() => ({
  getStructure: vi.fn(),
  getDocuments: vi.fn(),
  getStructureRepairs: vi.fn(),
  getStructureMonitors: vi.fn(),
  getInspectionSchedule: vi.fn(),
}));
const configurationApi = vi.hoisted(() => ({
  getInspectionReportStatusCodes: vi.fn(),
  getLoadRatingCodes: vi.fn(),
}));
vi.mock('@/services/APIs', () => ({
  default: {
    inspectionSearch: inspectionApi,
    structureSearch: structureApi,
    configuration: configurationApi,
  },
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
  configurationApi.getInspectionReportStatusCodes.mockResolvedValue([
    { code: 'PRO', description: 'In Progress' },
    { code: 'REJ', description: 'Rejected' },
    { code: 'RVD', description: 'Reviewed' },
    { code: 'SUB', description: 'Submitted' },
  ]);
  inspectionApi.getInspection.mockReset();
  inspectionApi.getInspection.mockResolvedValue(inspection());
  inspectionApi.getInspectionForm.mockResolvedValue(form);
  structureApi.getStructure.mockResolvedValue({
    id: '7',
    structureName: 'B100',
    typeClass: { code: 'PB', description: 'Durable Bridge' },
    details: { yearBuilt: 2000, endOfDesignLifeYear: 2020 },
    site: null,
    loadRating: {
      currentRating: 50,
      history: [],
      reviewRequired: true,
      designVehicle: { code: 'L75', description: 'L-75' },
      designLoadRating: 75,
    },
    replacement: { estimatedLoadRestrictionYear: null, estimatedReplacementYear: 2090 },
    outstanding: [],
  });
  structureApi.getInspectionSchedule.mockResolvedValue({ routineFrequencyYears: 3 });
  configurationApi.getLoadRatingCodes.mockResolvedValue({
    loadPostingSigns: [],
    designVehicles: [],
    reasons: [
      { code: 'INSP', description: 'Inspection', deltas: ['INC'] },
      { code: 'DOWN', description: 'Downrated', deltas: ['DEC'] },
      { code: 'OTHR', description: 'Other', deltas: ['UNDEF'] },
      { code: 'HIST', description: 'Historical', deltas: ['UNDEF'] },
    ],
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
    // A table of the span's stringers; View opens one's answers and comments.
    expect(within(span2).getByTestId('condition-STR-span-702-stringer-3')).toMatchTextContent(
      /3\s*Yes/,
    );
    await userEvent.click(within(span2).getByTestId('condition-STR-span-702-stringer-view-3'));
    const details = within(span2).getByTestId('condition-STR-span-702-stringer-details');
    expect(within(details).getByRole('heading', { name: 'Stringer 3' })).toBeInTheDocument();
    expect(details).toMatchTextContent(/Peeled\s*Yes/);
    await userEvent.click(within(span2).getByTestId('condition-STR-span-702-stringer-view-3'));
    expect(
      within(span2).queryByTestId('condition-STR-span-702-stringer-details'),
    ).not.toBeInTheDocument();

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

  it('shows the review on the Inspection tab, as legacy: no Review tab', async () => {
    renderPage();
    await screen.findByTestId('inspection-inspection');

    expect(screen.queryByTestId('inspection-tab-review')).not.toBeInTheDocument();
    expect(screen.getByTestId('inspection-section-inspection')).toMatchTextContent(
      /Reviewed By\s*Pat Engineer/,
    );
    expect(screen.getByTestId('inspection-section-load-rating')).toMatchTextContent(
      /55[\s\S]*Structural deterioration/,
    );
    expect(screen.getByTestId('inspection-peng-comment')).toMatchTextContent('Agreed.');

    // Its status history, in legacy's View History dialog.
    await userEvent.click(screen.getByTestId('status-history-open'));
    const history = screen.getByTestId('status-history-dialog');
    expect(within(history).getByTestId('status-history-0')).toMatchTextContent(
      /Reviewed\s*IDIR\\PAT/,
    );
    expect(within(history).getByTestId('status-history-1')).toMatchTextContent(/Submitted/);
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
    // Reviewed: no Add, as legacy.
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

  describe('changing its documents and photos, as legacy', () => {
    const doc = (id: string, inspectionId: string | null) => ({
      id,
      inspectionId,
      inspectionDate: inspectionId ? '2023-06-01' : null,
      attachmentType: { code: 'PHOTO', description: 'Photograph' },
      created: '2023-06-01',
      extension: 'JPG',
      description: 'Deck',
      filename: `${id}.jpg`,
      auditRecord: false,
    });

    it("adds to this inspection's folder from Level 1, deletes any file at Level 2, never edits", async () => {
      inspectionApi.getInspection.mockResolvedValue(
        inspection({ status: { code: 'SUB', description: 'Submitted' } }),
      );
      inspectionApi.deleteInspectionDocument.mockResolvedValue(undefined);
      structureApi.getDocuments.mockResolvedValue({ documents: [doc('1', null), doc('2', '30')] });
      renderPage();
      await screen.findByTestId('inspection-inspection');
      await openTab('documents');

      // This inspection's folder is there to hold Add, with its date — and first.
      const folder = await screen.findByTestId('structure-documents-41');
      const cards = screen.getAllByTestId(/^structure-documents-(structure|\d+)$/);
      expect(cards[0]).toBe(folder);
      expect(folder).toMatchTextContent(/Inspection: May 1, 2024/);
      expect(within(folder).getByTestId('structure-documents-add')).toBeInTheDocument();
      expect(screen.queryByTestId('structure-document-edit-1')).not.toBeInTheDocument();

      await userEvent.click(screen.getByTestId('structure-document-delete-2'));
      await userEvent.click(await screen.findByRole('button', { name: 'Delete' }));
      await waitFor(() =>
        expect(inspectionApi.deleteInspectionDocument).toHaveBeenCalledWith('41', '2'),
      );
    });

    it('adds nothing once Reviewed, and says why — but still deletes, as legacy', async () => {
      structureApi.getDocuments.mockResolvedValue({ documents: [doc('1', null)] });
      renderPage();
      await screen.findByTestId('inspection-inspection');
      await openTab('documents');

      expect(await screen.findByTestId('structure-documents-locked')).toMatchTextContent(
        /can no longer be added/,
      );
      expect(screen.queryByTestId('structure-documents-add')).not.toBeInTheDocument();
      expect(screen.getByTestId('structure-document-delete-1')).toBeInTheDocument();
    });
  });

  describe('editing the hydraulic hazards, as legacy', () => {
    const openHazards = async () => {
      renderPage();
      await screen.findByTestId('inspection-inspection');
      await openTab('hazards');
    };

    it('saves the measurements, hazards and comment, and shows them again', async () => {
      inspectionApi.getInspection.mockResolvedValue(
        inspection({ status: { code: 'PRO', description: 'In Progress' } }),
      );
      inspectionApi.updateInspectionHazards.mockResolvedValue(undefined);
      await openHazards();

      await userEvent.click(screen.getByTestId('hazards-edit'));
      const width = screen.getByTestId('hazard-presentWaterWidth');
      await userEvent.type(width, '400');
      // Legacy's range warning: shown, never stopping the save.
      expect(screen.getByTestId('inspection-section-hazards')).toMatchTextContent(
        /not in the range 0 through 350/,
      );
      await userEvent.click(screen.getByText('Ice?'));
      await userEvent.click(screen.getByTestId('hazards-save'));

      await waitFor(() =>
        expect(inspectionApi.updateInspectionHazards).toHaveBeenCalledWith(
          '41',
          expect.objectContaining({
            presentWaterDepth: 1.5,
            presentWaterWidth: 400,
            highWaterClearance: 3,
            debris: true,
            ice: true,
            comment: 'Some debris at the pier.',
          }),
        ),
      );
      await waitFor(() => expect(screen.getByTestId('hazards-edit')).toBeInTheDocument());
    });

    it('will not blank a saved measurement, and needs all three past In Progress', async () => {
      inspectionApi.getInspection.mockResolvedValue(
        inspection({ status: { code: 'SUB', description: 'Submitted' } }),
      );
      await openHazards();

      await userEvent.click(screen.getByTestId('hazards-edit'));
      await userEvent.clear(screen.getByTestId('hazard-presentWaterDepth'));
      await userEvent.click(screen.getByTestId('hazards-save'));

      const card = screen.getByTestId('inspection-section-hazards');
      expect(card).toMatchTextContent(/Estimated Present Water Level Depth is required\./);
      expect(card).toMatchTextContent(/Estimated Present Water Level Width is required\./);
      expect(inspectionApi.updateInspectionHazards).not.toHaveBeenCalled();

      await userEvent.click(screen.getByTestId('hazards-cancel'));
      expect(screen.getByTestId('hazards-edit')).toBeInTheDocument();
    });

    it('offers no Edit once Reviewed, as legacy disables its page', async () => {
      await openHazards();

      expect(screen.getByTestId('inspection-section-hazards')).toBeInTheDocument();
      expect(screen.queryByTestId('hazards-edit')).not.toBeInTheDocument();
    });
  });

  describe('editing the Inspection tab, as legacy', () => {
    const inProgress = () =>
      inspection({
        status: { code: 'PRO', description: 'In Progress' },
        inspectorCompanyName: null,
      });

    it('submits it: In Progress or Submitted, and Submitted needs the company', async () => {
      inspectionApi.getInspection.mockResolvedValue(inProgress());
      inspectionApi.updateInspection.mockResolvedValue(undefined);
      renderPage();
      await screen.findByTestId('inspection-inspection');

      await userEvent.click(screen.getByTestId('inspection-edit'));
      const status = screen.getByTestId('inspection-edit-statusCode');
      await waitFor(() =>
        expect(
          within(status)
            .getAllByRole('option')
            .map((option) => option.textContent),
        ).toEqual(['In Progress', 'Rejected', 'Reviewed', 'Submitted']),
      );
      // One card at a time.
      expect(screen.queryByTestId('comments-edit')).not.toBeInTheDocument();

      fireEvent.change(status, { target: { value: 'SUB' } });
      await userEvent.click(screen.getByTestId('inspection-save'));
      expect(screen.getByTestId('inspection-section-inspection')).toMatchTextContent(
        /Inspection Company Name is required\./,
      );
      expect(inspectionApi.updateInspection).not.toHaveBeenCalled();

      await userEvent.type(screen.getByTestId('inspection-edit-inspectorCompanyName'), 'Bridges');
      await userEvent.click(screen.getByTestId('inspection-save'));
      await waitFor(() =>
        expect(inspectionApi.updateInspection).toHaveBeenCalledWith(
          '41',
          expect.objectContaining({
            statusCode: 'SUB',
            inspectionDate: '2024-05-01',
            inspectorName: 'Jo Smith',
            inspectorCompanyName: 'Bridges',
            cost: 1200,
          }),
        ),
      );
    });

    it('will not blank what was given, even In Progress', async () => {
      inspectionApi.getInspection.mockResolvedValue(inProgress());
      renderPage();
      await screen.findByTestId('inspection-inspection');

      await userEvent.click(screen.getByTestId('inspection-edit'));
      await userEvent.clear(screen.getByTestId('inspection-edit-inspectorName'));
      await userEvent.click(screen.getByTestId('inspection-save'));

      expect(screen.getByTestId('inspection-section-inspection')).toMatchTextContent(
        /Inspector Name is required\./,
      );
      await userEvent.click(screen.getByTestId('inspection-cancel'));
      expect(screen.getByTestId('inspection-section-inspection')).toMatchTextContent(/Jo Smith/);
    });

    it('saves the comments', async () => {
      inspectionApi.getInspection.mockResolvedValue(inProgress());
      inspectionApi.updateInspectionComment.mockResolvedValue(undefined);
      renderPage();
      await screen.findByTestId('inspection-inspection');

      await userEvent.click(screen.getByTestId('comments-edit'));
      const box = screen.getByTestId('inspection-edit-comment');
      await userEvent.clear(box);
      await userEvent.type(box, 'Deck re-planked.');
      await userEvent.click(screen.getByTestId('inspection-comment-save'));

      await waitFor(() =>
        expect(inspectionApi.updateInspectionComment).toHaveBeenCalledWith(
          '41',
          'Deck re-planked.',
        ),
      );
    });

    it("shows a Rejected one's status read-only to all but a P.Eng", async () => {
      inspectionApi.getInspection.mockResolvedValue(
        inspection({ status: { code: 'REJ', description: 'Rejected' } }),
      );
      authorization.isPeng = false;
      renderPage();
      await screen.findByTestId('inspection-inspection');
      await userEvent.click(screen.getByTestId('inspection-edit'));
      expect(screen.queryByTestId('inspection-edit-statusCode')).not.toBeInTheDocument();
      authorization.isPeng = true;
    });

    it('offers no Edit once Reviewed, as legacy disables its page', async () => {
      renderPage();
      await screen.findByTestId('inspection-inspection');

      expect(screen.queryByTestId('inspection-edit')).not.toBeInTheDocument();
      expect(screen.queryByTestId('comments-edit')).not.toBeInTheDocument();
      expect(screen.queryByTestId('peng-comments-edit')).not.toBeInTheDocument();
    });

    it('offers Reviewed and the PEng Comments to a P.Eng only', async () => {
      inspectionApi.getInspection.mockResolvedValue(inProgress());
      // An inspection reviewer is a P.Eng too: below the P.Eng, neither.
      authorization.isPeng = false;
      authorization.isInspectionReviewer = false;
      renderPage();
      await screen.findByTestId('inspection-inspection');

      expect(screen.queryByTestId('peng-comments-edit')).not.toBeInTheDocument();
      await userEvent.click(screen.getByTestId('inspection-edit'));
      const status = screen.getByTestId('inspection-edit-statusCode');
      await waitFor(() =>
        expect(
          within(status)
            .getAllByRole('option')
            .map((option) => option.textContent),
        ).toEqual(['In Progress', 'Submitted']),
      );
      authorization.isPeng = true;
      authorization.isInspectionReviewer = true;
    });

    it('offers Reviewed to an inspection reviewer, the PEng Comments to any P.Eng', async () => {
      inspectionApi.getInspection.mockResolvedValue(inProgress());
      authorization.isInspectionReviewer = false;
      renderPage();
      await screen.findByTestId('inspection-inspection');

      expect(screen.getByTestId('peng-comments-edit')).toBeInTheDocument();
      await userEvent.click(screen.getByTestId('inspection-edit'));
      const status = screen.getByTestId('inspection-edit-statusCode');
      await waitFor(() =>
        expect(
          within(status)
            .getAllByRole('option')
            .map((option) => option.textContent),
        ).toEqual(['In Progress', 'Submitted']),
      );
      authorization.isInspectionReviewer = true;
    });

    it('lets an inspection reviewer reject it with the card, saying what it reschedules', async () => {
      inspectionApi.getInspection.mockResolvedValue(
        inspection({ status: { code: 'SUB', description: 'Submitted' } }),
      );
      inspectionApi.updateInspection.mockResolvedValue(undefined);
      renderPage();
      await screen.findByTestId('inspection-inspection');

      await userEvent.click(screen.getByTestId('inspection-edit'));
      const status = screen.getByTestId('inspection-edit-statusCode');
      await waitFor(() => expect(within(status).getByText('Rejected')).toBeInTheDocument());
      expect(screen.getByTestId('inspection-section-inspection')).not.toMatchTextContent(
        /reschedules/,
      );

      fireEvent.change(status, { target: { value: 'REJ' } });
      const next = new Date();
      const yearOut = `${next.getFullYear() + 1}/`;
      expect(screen.getByTestId('inspection-section-inspection')).toMatchTextContent(
        new RegExp(`next routine inspection to ${yearOut}`),
      );
      await userEvent.click(screen.getByTestId('inspection-save'));

      await waitFor(() =>
        expect(inspectionApi.updateInspection).toHaveBeenCalledWith(
          '41',
          expect.objectContaining({ statusCode: 'REJ' }),
        ),
      );
      // Not a review: no Accept dialog.
      expect(inspectionApi.reviewInspection).not.toHaveBeenCalled();
    });

    it('offers Rejected to an inspection reviewer only', async () => {
      inspectionApi.getInspection.mockResolvedValue(
        inspection({ status: { code: 'SUB', description: 'Submitted' } }),
      );
      // A P.Eng below the reviewer may move a Rejected one on, but not reject.
      authorization.isInspectionReviewer = false;
      renderPage();
      await screen.findByTestId('inspection-inspection');

      await userEvent.click(screen.getByTestId('inspection-edit'));
      const status = screen.getByTestId('inspection-edit-statusCode');
      await waitFor(() =>
        expect(
          within(status)
            .getAllByRole('option')
            .map((option) => option.textContent),
        ).toEqual(['In Progress', 'Submitted']),
      );
      authorization.isInspectionReviewer = true;
    });

    it('saves the PEng Comments', async () => {
      inspectionApi.getInspection.mockResolvedValue(inProgress());
      inspectionApi.updatePengComment.mockResolvedValue(undefined);
      renderPage();
      await screen.findByTestId('inspection-inspection');

      await userEvent.click(screen.getByTestId('peng-comments-edit'));
      const box = screen.getByTestId('inspection-edit-peng-comment');
      await userEvent.clear(box);
      await userEvent.type(box, 'Sound.');
      await userEvent.click(screen.getByTestId('peng-comment-save'));

      await waitFor(() =>
        expect(inspectionApi.updatePengComment).toHaveBeenCalledWith('41', 'Sound.'),
      );
    });

    it("reviews it in legacy's Accept dialog: the card, then the review", async () => {
      inspectionApi.getInspection.mockResolvedValue(
        inspection({ status: { code: 'SUB', description: 'Submitted' } }),
      );
      inspectionApi.updateInspection.mockResolvedValue(undefined);
      inspectionApi.reviewInspection.mockResolvedValue(undefined);
      renderPage();
      await screen.findByTestId('inspection-inspection');

      await userEvent.click(screen.getByTestId('inspection-edit'));
      const status = screen.getByTestId('inspection-edit-statusCode');
      await waitFor(() => expect(within(status).getAllByRole('option')).toHaveLength(4));
      fireEvent.change(status, { target: { value: 'RVD' } });
      await userEvent.click(screen.getByTestId('inspection-save'));

      // Saving as Reviewed opens the dialog instead of saving.
      const dialog = await screen.findByTestId('review-dialog');
      expect(inspectionApi.updateInspection).not.toHaveBeenCalled();
      await waitFor(() =>
        expect(within(dialog).getByTestId('review-loadRating')).toHaveValue('55'),
      );
      // Legacy's suggestion: the inspection date plus the structure's frequency.
      expect(within(dialog).getByTestId('review-routineFrequencyYears')).toHaveValue('3');
      expect(dialog.querySelector('#review-nextRoutineDate')).toHaveValue('2027/05/01');
      expect(within(dialog).getByTestId('review-end-of-design-life')).toMatchTextContent(
        /years overdue/,
      );
      // 55 over the structure's 50 is an increase: its reasons, and any change's — never HIST.
      expect(
        within(within(dialog).getByTestId('review-reasonCode'))
          .getAllByRole('option')
          .map((option) => option.getAttribute('value')),
      ).toEqual(['', 'INSP', 'OTHR']);

      await userEvent.click(within(dialog).getByTestId('review-accept'));
      expect(dialog).toMatchTextContent(/Choose a reason that fits the change in load rating\./);
      expect(inspectionApi.reviewInspection).not.toHaveBeenCalled();

      fireEvent.change(within(dialog).getByTestId('review-reasonCode'), {
        target: { value: 'OTHR' },
      });
      await userEvent.click(within(dialog).getByTestId('review-accept'));
      expect(dialog).toMatchTextContent(/Load Rating Reason Comment is required for Other\./);
      await userEvent.type(within(dialog).getByTestId('review-reasonComment'), 'Re-rated.');
      await userEvent.click(within(dialog).getByTestId('review-loadRatingReviewRequired'));
      await userEvent.click(within(dialog).getByTestId('review-accept'));

      await waitFor(() =>
        expect(inspectionApi.reviewInspection).toHaveBeenCalledWith('41', {
          loadRating: 55,
          reasonCode: 'OTHR',
          reasonComment: 'Re-rated.',
          nextRoutineDate: '2027-05-01',
          routineFrequencyYears: 3,
          loadRatingReviewRequired: false,
        }),
      );
      // The card first, at the status it has: Reviewed is the review's.
      expect(inspectionApi.updateInspection).toHaveBeenCalledWith(
        '41',
        expect.objectContaining({ statusCode: 'SUB' }),
      );
    });
  });

  describe('editing a Condition card, as legacy', () => {
    it("saves its group's answers, keeping Add Inspection's rules", async () => {
      inspectionApi.getInspection.mockResolvedValue(
        inspection({ status: { code: 'SUB', description: 'Submitted' } }),
      );
      inspectionApi.updateInspectionCondition.mockResolvedValue(undefined);
      renderPage();
      await screen.findByTestId('inspection-inspection');
      await openTab('condition');

      await userEvent.click(await screen.findByTestId('condition-APP-edit'));
      // One card at a time.
      expect(screen.queryByTestId('condition-STR-edit')).not.toBeInTheDocument();
      const comment = screen.getByTestId('items-APP-APPQ-comment');
      expect(comment).toHaveValue('Potholes');
      await userEvent.clear(comment);
      await userEvent.click(screen.getByTestId('condition-APP-save'));
      expect(screen.getByTestId('condition-APP')).toMatchTextContent(
        /\(Approaches\) Approach condition requires a comment\./,
      );
      expect(inspectionApi.updateInspectionCondition).not.toHaveBeenCalled();

      await userEvent.type(comment, 'Ruts');
      fireEvent.change(screen.getByTestId('items-APP-GR-answer'), { target: { value: 'Bent' } });
      await userEvent.click(screen.getByTestId('condition-APP-save'));
      await waitFor(() =>
        expect(inspectionApi.updateInspectionCondition).toHaveBeenCalledWith('41', 'APP', [
          expect.objectContaining({ questionCode: 'APPQ', answer: 'P', comment: 'Ruts' }),
          expect.objectContaining({ questionCode: 'GR', answer: 'Bent' }),
        ]),
      );
    });

    it("edits a span's stringers in its table, one form at a time", async () => {
      inspectionApi.getInspection.mockResolvedValue(
        inspection({ status: { code: 'PRO', description: 'In Progress' } }),
      );
      renderPage();
      await screen.findByTestId('inspection-inspection');
      await openTab('condition');

      await userEvent.click(await screen.findByTestId('condition-STR-edit'));
      const span = screen.getByTestId('condition-STR-span-702');
      expect(within(span).getByTestId('condition-STR-span-702-stringer-3')).toMatchTextContent(
        /3\s*Yes/,
      );
      await userEvent.click(within(span).getByTestId('condition-STR-span-702-stringer-edit-3'));
      expect(screen.getByTestId('items-STR-PEEL-span-702-stringer-3-answer')).toHaveValue('Yes');
      await userEvent.click(screen.getByTestId('condition-STR-cancel'));
      expect(screen.getByTestId('condition-STR-edit')).toBeInTheDocument();
    });

    it('offers no Edit once Reviewed, as legacy disables its page', async () => {
      renderPage();
      await screen.findByTestId('inspection-inspection');
      await openTab('condition');

      await screen.findByTestId('condition-APP');
      expect(screen.queryByTestId('condition-APP-edit')).not.toBeInTheDocument();
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
