import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import NewInspectionPage from './index';

import type { InspectionFormResponse } from './inspectionForm';

import { WORK_SAVED } from '@/context/unsavedChanges/workSaved';

const authorization = vi.hoisted(() => ({
  canWriteInspection: true,
  canEdit: true,
  canDelete: false,
  isPeng: false,
}));
vi.mock('@/hooks/useAuthorization', () => ({ useAuthorization: () => authorization }));
vi.mock('@/context/pageTitle/usePageTitle', () => ({
  usePageTitle: () => ({ setPageTitle: vi.fn(), pageTitle: '' }),
}));
vi.mock('@/context/unsavedChanges/useUnsavedChanges', () => ({
  useUnsavedChanges: () => ({ isDirty: false, setDirty: vi.fn() }),
}));
const navigate = vi.hoisted(() => vi.fn());
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return { ...actual, useNavigate: () => navigate };
});

const inspectionApi = vi.hoisted(() => ({ getInspectionForm: vi.fn(), createInspection: vi.fn() }));
const structureApi = vi.hoisted(() => ({ getStructure: vi.fn(), suggestStructureNames: vi.fn() }));
const configurationApi = vi.hoisted(() => ({
  getInspectionTypeCodes: vi.fn(),
  getInspectionReportStatusCodes: vi.fn(),
  getSpecialEquipmentCodes: vi.fn(),
}));
vi.mock('@/services/APIs', () => ({
  default: {
    inspectionSearch: inspectionApi,
    structureSearch: structureApi,
    configuration: configurationApi,
  },
}));

const structure = (outstanding: unknown[] = []) => ({
  id: '7',
  structureName: 'B100',
  typeClass: { code: 'PB', description: 'Durable Bridge' },
  site: {
    siteId: '62-145',
    siteTypeCode: 'CRS',
    crossingName: 'Riske Cr',
    districtName: 'Cariboo-Chilcotin',
    managementAreaName: null,
    forestServiceRoad: 'Riske FSR',
    projectName: null,
    forestFileId: 'R001',
    roadSectionId: '01',
    kilometres: '1.2',
    maintainerLabel: null,
    siteStatus: { code: 'ACT', description: 'Active' },
  },
  outstanding,
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
        { code: 'PLR', description: 'Posted Load Rating', perStringer: false, answers: [] },
      ],
    },
    {
      code: 'DECK',
      description: 'Deck',
      scope: 'SPAN',
      questions: [
        {
          code: 'DECKQ',
          description: 'Deck condition',
          perStringer: false,
          answers: [{ code: 'G', description: 'Good' }],
        },
      ],
    },
  ],
  spans: [
    { id: '701', number: 1 },
    { id: '702', number: 2 },
  ],
  piers: [],
};

beforeEach(() => {
  authorization.canEdit = true;
  navigate.mockReset();
  inspectionApi.getInspectionForm.mockReset();
  inspectionApi.getInspectionForm.mockResolvedValue(form);
  inspectionApi.createInspection.mockReset();
  inspectionApi.createInspection.mockResolvedValue({ id: '901' });
  structureApi.getStructure.mockReset();
  structureApi.getStructure.mockResolvedValue(structure());
  structureApi.suggestStructureNames.mockReset();
  structureApi.suggestStructureNames.mockResolvedValue([
    { id: '7', structureName: 'B100', archived: false },
  ]);
  configurationApi.getInspectionTypeCodes.mockResolvedValue([
    { code: 'ROUT', description: 'Routine' },
    { code: 'UNP', description: 'Unplanned' },
  ]);
  configurationApi.getInspectionReportStatusCodes.mockResolvedValue([
    { code: 'PRO', description: 'In Progress' },
    { code: 'REJ', description: 'Rejected' },
    { code: 'RVD', description: 'Reviewed' },
    { code: 'SUB', description: 'Submitted' },
  ]);
  configurationApi.getSpecialEquipmentCodes.mockResolvedValue([]);
});

const renderPage = (search = '') => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/inspection/new${search}`]}>
        <Routes>
          <Route path="/inspection/new" element={<NewInspectionPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
};

const openTab = (key: string) => userEvent.click(screen.getByTestId(`inspection-tab-${key}`));

describe('NewInspectionPage', () => {
  it("from a structure's button: shows the structure and the type it was started as", async () => {
    renderPage('?structureId=7&type=ROUT');

    const information = screen.getByTestId('inspection-section-structure');
    await waitFor(() => expect(information).toMatchTextContent(/B100/));
    expect(information).toMatchTextContent(/Durable Bridge/);
    expect(information).toMatchTextContent(/Riske Cr/);
    expect(screen.queryByTestId('inspection-structure')).not.toBeInTheDocument();

    // The same tab, below the structure: the inspection's own fields.
    const inspection = screen.getByTestId('inspection-inspection');
    expect(inspection).toMatchTextContent(/Routine/);
    expect(inspection).toMatchTextContent(/In Progress/);
    expect(screen.queryByTestId('inspection-typeCode')).not.toBeInTheDocument();
  });

  it('from the menu: the structure is found by Structure #, the type chosen', async () => {
    authorization.canEdit = false;
    renderPage();

    const picker = within(screen.getByTestId('inspection-section-structure')).getByRole('combobox');
    await userEvent.type(picker, 'B1');
    await userEvent.click(await screen.findByRole('option', { name: 'B100' }));
    await waitFor(() => expect(structureApi.getStructure).toHaveBeenCalledWith('7'));

    // Routine is Level 1's: a Level 0 user is offered Unplanned only.
    const options = within(screen.getByTestId('inspection-typeCode')).getAllByRole('option');
    expect(options.map((option) => option.textContent)).toEqual(['Choose a type', 'Unplanned']);
  });

  it('asks the structure its questions, each span in turn, and keeps Poor to a comment', async () => {
    renderPage('?structureId=7&type=UNP');
    await openTab('condition');

    const deck = await screen.findByTestId('condition-DECK');
    // Every span stacked in the card, each under its own heading.
    expect(within(deck).getByRole('heading', { name: 'Span 1' })).toBeInTheDocument();
    expect(within(deck).getByRole('heading', { name: 'Span 2' })).toBeInTheDocument();
    fireEvent.change(screen.getByTestId('items-APP-APPQ-answer'), { target: { value: 'P' } });
    // Poor needs a comment: the box says so.
    const comment = screen.getByTestId('items-APP-APPQ-comment');
    expect(comment).toHaveAttribute('placeholder', 'Comment (required)');
    await userEvent.click(screen.getByTestId('inspection-save'));

    // Under the comment box, not the answer.
    expect(comment).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByTestId('items-APP-APPQ-answer')).not.toHaveAttribute('aria-invalid');
    expect(screen.getByTestId('condition-APP')).toMatchTextContent(
      '(Approaches) Approach condition requires a comment.',
    );
    // Refused: the banner, the tab's count and its list turn red.
    expect(
      screen.getByRole('img', { name: 'Condition: 1 item outstanding, blocking save' }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('inspection-outstanding-summary')).toMatchTextContent(
      /Inspection not saved/,
    );
    // Said at the top of the tab too, under the card it is in.
    expect(screen.getByTestId('inspection-condition-errors')).toMatchTextContent(
      /Approaches\s*Approach condition requires a comment\./,
    );
    expect(inspectionApi.createInspection).not.toHaveBeenCalled();
  });

  it('saves the inspection with its answers and opens it', async () => {
    renderPage('?structureId=7&type=UNP');
    await openTab('inspection');
    await userEvent.type(screen.getByTestId('inspection-inspectorName'), 'Jo Smith');
    await openTab('condition');
    await screen.findByTestId('condition-DECK');
    fireEvent.change(screen.getByTestId('items-APP-APPQ-answer'), { target: { value: 'G' } });
    fireEvent.change(screen.getByTestId('items-DECK-DECKQ-span-702-answer'), {
      target: { value: 'G' },
    });
    await openTab('hazards');
    await userEvent.click(screen.getByText('Scour?'));

    await userEvent.click(screen.getByTestId('inspection-save'));

    await waitFor(() =>
      expect(inspectionApi.createInspection).toHaveBeenCalledWith(
        expect.objectContaining({
          structureId: 7,
          typeCode: 'UNP',
          statusCode: 'PRO',
          inspectorName: 'Jo Smith',
          scour: true,
          items: [
            expect.objectContaining({ subsectionCode: 'APP', questionCode: 'APPQ', answer: 'G' }),
            expect.objectContaining({
              subsectionCode: 'DECK',
              questionCode: 'DECKQ',
              spanId: 702,
              answer: 'G',
            }),
          ],
        }),
      ),
    );
    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith('/inspection/901', { state: WORK_SAVED }),
    );
  });

  it('is added In Progress or Submitted, and Submitted needs the date, inspector and company', async () => {
    renderPage('?structureId=7&type=UNP');
    await openTab('inspection');

    const status = screen.getByTestId('inspection-statusCode');
    await waitFor(() => expect(status).toHaveValue('PRO'));
    // Reviewed and Rejected are a P.Eng's, on the saved inspection.
    expect(
      within(status)
        .getAllByRole('option')
        .map((option) => option.textContent),
    ).toEqual(['In Progress', 'Submitted']);

    fireEvent.change(status, { target: { value: 'SUB' } });
    await userEvent.click(screen.getByTestId('inspection-save'));

    const inspection = screen.getByTestId('inspection-inspection');
    expect(inspection).toMatchTextContent(/Date of Inspection is required\./);
    expect(inspection).toMatchTextContent(/Inspector Name is required\./);
    expect(inspection).toMatchTextContent(/Inspection Company Name is required\./);
    expect(screen.getByTestId('inspection-inspection-errors')).toMatchTextContent(
      /Outstanding in this tab.*Inspection\s*Date of Inspection is required\./,
    );
    expect(inspectionApi.createInspection).not.toHaveBeenCalled();
  });

  it('shows what is outstanding from the start, before any field is touched', async () => {
    renderPage();

    // From the menu: the structure and the type are still to choose — counted, not yet beside
    // their fields.
    expect(await screen.findByTestId('inspection-outstanding-summary')).toMatchTextContent(
      '2 required items outstanding across 1 tab',
    );
    expect(
      screen.getByRole('img', { name: 'Inspection: 2 items outstanding' }),
    ).toBeInTheDocument();
    const list = screen.getByTestId('inspection-inspection-errors');
    expect(list).toMatchTextContent(/Structure and site\s*Structure # is required\./);
    expect(list).toMatchTextContent(/Inspection\s*Inspection Type is required\./);
    expect(screen.getByTestId('inspection-section-inspection')).not.toMatchTextContent(
      /is required\./,
    );
  });

  it('says beside Structure # when the structure is incomplete, and does not save', async () => {
    structureApi.getStructure.mockResolvedValue(
      structure([{ section: 'DETAILS', card: 'Structure', label: 'Source' }]),
    );
    renderPage('?structureId=7&type=UNP');

    expect(await screen.findByTestId('inspection-structure-error')).toMatchTextContent(
      'Structure B100 is incomplete. Complete it on its structure page before it can be inspected.',
    );
    await userEvent.click(screen.getByTestId('inspection-save'));
    expect(inspectionApi.createInspection).not.toHaveBeenCalled();
  });

  it('stacks each pier, and adds stringers to a span one at a time', async () => {
    inspectionApi.getInspectionForm.mockResolvedValue({
      ...form,
      subsections: [
        {
          code: 'STR',
          description: 'Stringers',
          scope: 'SPAN',
          questions: [
            {
              code: 'SQ',
              description: 'Stringer condition',
              perStringer: false,
              answers: [{ code: 'G', description: 'Good' }],
            },
            { code: 'PEEL', description: 'Peeled', perStringer: true, answers: [] },
          ],
        },
        {
          code: 'PIER',
          description: 'Piers',
          scope: 'PIER',
          questions: [
            { code: 'PQ', description: 'Pier condition', perStringer: false, answers: [] },
          ],
        },
      ],
      spans: [{ id: '701', number: 1 }],
      piers: [
        { id: '801', number: 1 },
        { id: '802', number: 2 },
      ],
    });
    renderPage('?structureId=7&type=UNP');
    await openTab('condition');

    const piers = await screen.findByTestId('condition-PIER');
    expect(within(piers).getByRole('heading', { name: 'Pier 2' })).toBeInTheDocument();

    const span = screen.getByTestId('condition-STR-span-701');
    expect(span).toMatchTextContent(/No stringers yet\./);
    await userEvent.click(within(span).getByTestId('condition-STR-span-701-stringer-add'));
    await userEvent.click(within(span).getByTestId('condition-STR-span-701-stringer-add'));
    expect(within(span).getByRole('heading', { name: 'Stringer 2' })).toBeInTheDocument();

    // A typed answer, for a question that lists none.
    await userEvent.type(screen.getByTestId('items-STR-PEEL-span-701-stringer-2-answer'), 'Yes');
    expect(screen.getByTestId('items-STR-PEEL-span-701-stringer-2-answer')).toHaveValue('Yes');
  });

  it('says when the bridge has no spans or piers recorded', async () => {
    inspectionApi.getInspectionForm.mockResolvedValue({
      ...form,
      subsections: [
        ...form.subsections,
        {
          code: 'PIER',
          description: 'Piers',
          scope: 'PIER',
          questions: [
            { code: 'PQ', description: 'Pier condition', perStringer: false, answers: [] },
          ],
        },
      ],
      spans: [],
      piers: [],
    });
    renderPage('?structureId=7&type=UNP');
    await openTab('condition');

    expect(await screen.findByTestId('condition-DECK')).toMatchTextContent(
      /This bridge has no spans recorded\./,
    );
    expect(screen.getByTestId('condition-PIER')).toMatchTextContent(
      /This bridge has no piers recorded\./,
    );
  });

  it('says when the structure is asked no questions, and before one is chosen', async () => {
    inspectionApi.getInspectionForm.mockResolvedValue({ ...form, subsections: [] });
    renderPage();
    await openTab('condition');
    expect(screen.getByTestId('condition-no-structure')).toBeInTheDocument();
  });

  it('warns past the hazard ranges and refuses a measurement that is not metres', async () => {
    renderPage('?structureId=7&type=UNP');
    await openTab('hazards');

    await userEvent.type(screen.getByTestId('hazard-presentWaterDepth'), '25');
    expect(screen.getByTestId('inspection-hazards')).toMatchTextContent(
      /Estimated Present Water Level Depth is not in the range 0 through 20\./,
    );
    await userEvent.type(screen.getByTestId('hazard-presentWaterWidth'), 'wide');
    await userEvent.tab();
    expect(screen.getByTestId('inspection-hazards')).toMatchTextContent(
      /Estimated Present Water Level Width must be metres/,
    );
    await userEvent.type(screen.getByTestId('hazard-comment'), 'Some debris');
    expect(screen.getByTestId('hazard-comment')).toHaveValue('Some debris');
  });

  it('lets Level 2 set Professional Inspection Required', async () => {
    authorization.canDelete = true;
    renderPage('?structureId=7&type=UNP');

    await userEvent.click(screen.getByText('Yes'));
    await userEvent.type(screen.getByTestId('inspection-cost'), '1200');
    await userEvent.type(screen.getByTestId('inspection-comment'), 'Looks fine');
    expect(screen.getByRole('radio', { name: 'Yes' })).toBeChecked();
    authorization.canDelete = false;
  });

  it("shows the server's refusal beside its field and in the tab's list", async () => {
    inspectionApi.createInspection.mockRejectedValue({
      status: 400,
      body: { fieldErrors: { inspectionDate: 'Inspection with this date already exists.' } },
    });
    renderPage('?structureId=7&type=UNP');
    await screen.findByText('B100');

    await userEvent.click(screen.getByTestId('inspection-save'));

    await waitFor(() =>
      expect(screen.getByTestId('inspection-inspection-errors')).toMatchTextContent(
        /Inspection with this date already exists\./,
      ),
    );
  });
});
