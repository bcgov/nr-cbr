import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import AddSitePage from './index';

import type { CodeOption, OrgUnitOption } from '@/types/configuration';

const api = vi.hoisted(() => ({
  getSiteStatusCodes: vi.fn(),
  getSiteTypeCodes: vi.fn(),
  getStructureInspectionStatusCodes: vi.fn(),
  getSpecialAccessCodes: vi.fn(),
  getForestDistricts: vi.fn(),
  getManagementAreas: vi.fn(),
  getRecreationDistricts: vi.fn(),
  getBusinessAreas: vi.fn(),
  getRecreationProjectName: vi.fn(),
}));
const clientApi = vi.hoisted(() => ({ searchClients: vi.fn() }));
const roadApi = vi.hoisted(() => ({ getRoadSection: vi.fn(), searchRoads: vi.fn() }));
const siteApi = vi.hoisted(() => ({ getSite: vi.fn(), createSite: vi.fn() }));

vi.mock('@/services/APIs', () => ({
  default: { configuration: api, client: clientApi, road: roadApi, siteSearch: siteApi },
}));

vi.mock('@/context/pageTitle/usePageTitle', () => ({
  usePageTitle: () => ({ setPageTitle: vi.fn(), pageTitle: '' }),
}));

const setDirty = vi.hoisted(() => vi.fn());
vi.mock('@/context/unsavedChanges/useUnsavedChanges', () => ({
  useUnsavedChanges: () => ({ isDirty: false, setDirty }),
}));

const navigate = vi.hoisted(() => vi.fn());
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return { ...actual, useNavigate: () => navigate };
});

const statuses: CodeOption[] = [
  { code: 'ACT', description: 'Active' },
  { code: 'DAC', description: 'Deactivated (closed)' },
];
const types: CodeOption[] = [
  { code: 'CRS', description: 'Crossing' },
  { code: 'REC', description: 'Recreation' },
  { code: 'STRG', description: 'Storage' },
];
const inspectionStatuses: CodeOption[] = [
  { code: 'INS', description: 'Inspect' },
  { code: 'DNI', description: 'Do Not Inspect' },
];
const districts: OrgUnitOption[] = [
  { orgUnitNo: '18', orgUnitCode: 'DPG', orgUnitName: 'Prince George' },
];

/**
 * Renders and waits for the dropdowns to fill.
 *
 * <p>The selects are disabled while the reference data loads, and a disabled control ignores a
 * change event — so a test that interacted immediately would set nothing and then assert against a
 * form that had never heard from it.
 */
const renderPage = async () => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const result = render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AddSitePage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  await waitFor(() => {
    expect(screen.getByTestId('site-form-crossingSiteTypeCode')).not.toBeDisabled();
  });
  return result;
};

const field = (name: string) => screen.getByTestId(`site-form-${name}`);
const type = (name: string, value: string) => fireEvent.change(field(name), { target: { value } });
const save = () => fireEvent.click(screen.getByTestId('add-site-save'));

/** True when the field renders as a read-only value rather than as something to type into. */
const isReadOnlyCell = (labelText: string) =>
  Array.from(document.querySelectorAll('.read-only-field__label')).some(
    (node) => node.textContent?.trim() === labelText,
  );

/**
 * Fills everything the form insists on, so a case can break exactly one thing.
 *
 * <p>Asynchronous because the Forest District is not typed: it arrives from the road, after the
 * lookup the Project File ID# and Br. trigger.
 */
const fillRequired = async () => {
  type('siteId', 'BOWRON-001');
  type('crossingSiteStatusCode', 'ACT');
  type('crossingSiteTypeCode', 'CRS');
  type('structureInspectionStatusCode', 'INS');
  type('forestFileId', 'R00123');
  type('roadSectionId', '01');
  type('crossingName', 'Deadman Creek');
  type('pointOfCommencementDistance', '12.50');
  // Both coordinates in full — legacy refuses the save without all six boxes.
  type('longitudeDegrees', '122');
  type('longitudeMinutes', '30');
  type('longitudeSeconds', '15.5');
  type('latitudeDegrees', '53');
  type('latitudeMinutes', '55');
  type('latitudeSeconds', '0');
  // The road decides the district, so wait for it rather than typing one.
  await waitFor(() => {
    expect(screen.getByText('DPG - Prince George')).toBeInTheDocument();
  });
};

beforeEach(() => {
  navigate.mockReset();
  setDirty.mockReset();
  api.getSiteStatusCodes.mockResolvedValue(statuses);
  api.getSiteTypeCodes.mockResolvedValue(types);
  api.getStructureInspectionStatusCodes.mockResolvedValue(inspectionStatuses);
  api.getSpecialAccessCodes.mockResolvedValue([]);
  api.getForestDistricts.mockResolvedValue(districts);
  api.getManagementAreas.mockResolvedValue([]);
  api.getRecreationDistricts.mockResolvedValue([]);
  api.getBusinessAreas.mockResolvedValue([]);
  // Reset, not just re-stubbed: the configuration mocks are shared across the file and a test
  // that asserts this one was *not* called would otherwise see the previous test's calls.
  api.getRecreationProjectName.mockReset();
  api.getRecreationProjectName.mockResolvedValue({ forestFileId: '', projectName: null });
  clientApi.searchClients.mockReset();
  clientApi.searchClients.mockResolvedValue([]);
  roadApi.searchRoads.mockReset();
  roadApi.searchRoads.mockResolvedValue([]);
  siteApi.getSite.mockReset();
  // No site with this number yet, which is what the uniqueness check asks. A 404 is the "free"
  // answer, so the hook reads a rejection rather than a value.
  siteApi.getSite.mockRejectedValue(new Error('not found'));
  siteApi.createSite.mockReset();
  siteApi.createSite.mockResolvedValue({ siteId: 'BOWRON-001' });
  roadApi.getRoadSection.mockReset();
  // A road that resolves, because the Forest District now comes from one — a form with no road has
  // no district, which is legacy's rule and the subject of its own tests below.
  roadApi.getRoadSection.mockResolvedValue({
    forestFileId: 'R00123',
    roadSectionId: '01',
    forestServiceRoad: 'Bowron FSR',
    orgUnitNo: 18,
  });
});

describe('AddSitePage — the form', () => {
  it('offers every field the legacy form does', async () => {
    // Field-for-field with site.jsp. A missing one is data someone can no longer record, and there
    // is no error to notice — the form just quietly captures less.
    await renderPage();

    for (const name of [
      'siteId',
      'crossingSiteStatusCode',
      'crossingSiteTypeCode',
      'structureInspectionStatusCode',
      'forestFileId',
      'roadSectionId',
      'managementOrgUnitNo',
      'pointOfCommencementDistance',
      'crossingName',
      'specialAccessRqmtCode',
      'capitalRoad',
      'pointOfAccessDescription',
      'longitudeDegrees',
      'longitudeMinutes',
      'longitudeSeconds',
      'latitudeDegrees',
      'latitudeMinutes',
      'latitudeSeconds',
      'utmZone',
      'utmEasting',
      'utmNorthing',
    ]) {
      expect(field(name)).toBeInTheDocument();
    }
    // Two fields read rather than typed: the road's name, and the district the road implies.
    expect(isReadOnlyCell('Forest Service Road')).toBe(true);
    expect(isReadOnlyCell('Forest District')).toBe(true);
  });

  it('leaves out the three fields legacy will not let anyone set here', async () => {
    // Designated Maintainer is disabled for Level 1 and above and readonly below it, and the
    // lookup that would fill it — `showClientSearch()` at site.jsp:529 — is called from nowhere.
    // User Kilometres is disabled at 863, BCTS BA Responsible at 901 even for Level 2. On a site
    // that does not exist yet they could only ever be blank.
    await renderPage();

    expect(screen.queryByTestId('site-form-maintainer')).not.toBeInTheDocument();
    expect(screen.queryByTestId('site-form-userKm')).not.toBeInTheDocument();
    expect(screen.queryByTestId('site-form-businessAreaOrgUnitNo')).not.toBeInTheDocument();
    // Nor as read-only cells: an empty labelled cell invites a hunt for the control that fills it.
    expect(isReadOnlyCell('User Kilometres')).toBe(false);
    expect(isReadOnlyCell('BCTS BA Responsible')).toBe(false);
  });

  it('keeps the legacy labels, which is the vocabulary the business uses', async () => {
    await renderPage();

    // Regexes, not exact strings: a required field's label carries a trailing asterisk, and
    // pinning the exact text here would make every future required marker a test failure.
    expect(screen.getByLabelText(/^Site #/)).toBeInTheDocument();
    expect(screen.getByLabelText(/^Project File ID#/)).toBeInTheDocument();
    expect(screen.getByLabelText(/^Br\./)).toBeInTheDocument();
    expect(screen.getByLabelText('Site Details')).toBeInTheDocument();
  });

  it('leaves out the two map sheet fields legacy deleted under CBR-455', async () => {
    // They are still in `site.jsp`, inside an HTML comment that wraps the whole table row
    // (lines 917-954) — which is why a grep for their maxlength finds them and the screen does not.
    // The columns still hold pre-CBR-455 data, so the entity keeps them; nothing reads them.
    await renderPage();

    expect(screen.queryByLabelText('Trim Map Sheet #')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('1:50,000 Map Sheet #')).not.toBeInTheDocument();
  });

  it('enforces the legacy maxlengths, which mirror the column widths', async () => {
    await renderPage();

    expect(field('siteId')).toHaveAttribute('maxlength', '14');
    expect(field('forestFileId')).toHaveAttribute('maxlength', '10');
    expect(field('crossingName')).toHaveAttribute('maxlength', '255');
    expect(field('utmZone')).toHaveAttribute('maxlength', '2');
  });

  it('asks for the coordinates before Site Details', async () => {
    // Order matters on a form this long: the coordinates are structured fields like everything
    // above them, and burying them under a free-text box reads as an afterthought. There is no
    // "Coordinates" heading over them — it named a group whose members already say what they are.
    await renderPage();

    expect(screen.queryByText('Coordinates')).not.toBeInTheDocument();

    const order = ['site-form-longitudeDegrees', 'site-form-latitudeDegrees', 'site-form-utmZone'];
    const positions = order.map((id) =>
      Array.from(document.querySelectorAll('[data-testid]')).indexOf(screen.getByTestId(id)),
    );
    expect(positions).toEqual([...positions].sort((a, b) => a - b));

    const details = screen.getByTestId('site-form-pointOfAccessDescription');
    expect(
      screen.getByTestId('site-form-utmNorthing').compareDocumentPosition(details) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  it('still names each coordinate box for a screen reader with the labels hidden', async () => {
    // hideLabel keeps the <label> and hides it visually. The temptation when removing a visible
    // label is to drop it altogether, which leaves nine identical unnamed boxes in three rows —
    // unusable without sight of the layout.
    await renderPage();

    expect(
      screen.getByLabelText('Degrees', { selector: '#site-form-longitudeDegrees' }),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText('Seconds', { selector: '#site-form-latitudeSeconds' }),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText('Northing', { selector: '#site-form-utmNorthing' }),
    ).toBeInTheDocument();
  });

  it('keeps longitude and latitude on one row, with UTM on its own', async () => {
    // They are one point, so a reader checks them against each other. UTM says the same thing a
    // different way rather than completing them, so it does not belong in the pair.
    await renderPage();

    const pair = document.querySelector('.site-form__coordinate-pair');
    expect(pair).toContainElement(field('longitudeDegrees'));
    expect(pair).toContainElement(field('latitudeDegrees'));
    expect(pair).not.toContainElement(field('utmZone'));
  });

  it('leaves Capital Road disabled, as every branch of the legacy form does', async () => {
    // It is set from the road record, not on this screen.
    await renderPage();

    expect(field('capitalRoad')).toBeDisabled();
  });
});

describe('AddSitePage — validation', () => {
  it('says nothing until the user tries to save', async () => {
    // Half these fields are required and all start empty, so validating on keystroke would paint
    // the form red before the user had touched anything.
    await renderPage();

    expect(field('siteId')).not.toHaveAttribute('aria-invalid', 'true');
  });

  it('marks what is missing once they do', async () => {
    await renderPage();

    save();

    expect(await screen.findByText('Site # is required.')).toBeInTheDocument();
    expect(field('siteId')).toHaveAttribute('aria-invalid', 'true');
  });

  it('follows the corrections live once it has spoken', async () => {
    await renderPage();
    save();
    expect(await screen.findByText('Site # is required.')).toBeInTheDocument();

    type('siteId', 'BOWRON-001');

    expect(screen.queryByText('Site # is required.')).not.toBeInTheDocument();
  });

  it('excuses a storage site its crossing name and kilometre mark', async () => {
    await renderPage();
    await fillRequired();
    type('crossingSiteTypeCode', 'STRG');
    type('structureInspectionStatusCode', 'DNI');
    type('crossingName', '');
    type('pointOfCommencementDistance', '');

    save();

    expect(screen.queryByText('Crossing Name is required.')).not.toBeInTheDocument();
    expect(screen.queryByText('Kilometres is required.')).not.toBeInTheDocument();
  });

  it('marks the Inspection Status box when it disagrees with the status, without waiting for Save', async () => {
    // Inline and live. Legacy stamps fieldName="structureInspectionStatusCode" on this rule, and a
    // clash between two values the user deliberately chose is not a field left unfinished — the
    // same line nr-frep draws for its duplicate-label check.
    await renderPage();
    await fillRequired();
    type('structureInspectionStatusCode', 'DNI');

    expect(
      await screen.findByText(
        /An Active or Barricaded\/Closed Crossing site must be set to Inspect/,
      ),
    ).toBeInTheDocument();
  });

  it('clears it again as soon as the combination agrees', async () => {
    await renderPage();
    await fillRequired();
    type('structureInspectionStatusCode', 'DNI');
    expect(await screen.findByText(/must be set to Inspect/)).toBeInTheDocument();

    type('structureInspectionStatusCode', 'INS');

    expect(screen.queryByText(/must be set to Inspect/)).not.toBeInTheDocument();
  });
});

describe('AddSitePage — Site Details', () => {
  const details = () => field('pointOfAccessDescription');

  it('counts what has been used against the column limit, live', async () => {
    await renderPage();
    expect(screen.getByText('0 / 255')).toBeInTheDocument();

    type('pointOfAccessDescription', 'Helicopter required to reach the cove.');

    expect(screen.getByText('38 / 255')).toBeInTheDocument();
  });

  it('counts bytes, which is what the column counts', async () => {
    // POINT_OF_ACCESS_DESC is VARCHAR2(255 BYTE). A character counter would read 1 / 255 here and
    // then let Oracle reject the save.
    await renderPage();

    type('pointOfAccessDescription', '🌲');

    expect(screen.getByText('4 / 255')).toBeInTheDocument();
  });

  it('does not truncate what is pasted past the limit', async () => {
    // A maxLength would drop the tail silently, which is how the end of a paragraph goes missing.
    await renderPage();
    const tooLong = 'a'.repeat(300);

    type('pointOfAccessDescription', tooLong);

    expect(details()).toHaveValue(tooLong);
    expect(details()).not.toHaveAttribute('maxlength');
  });

  it('refuses the save instead, and says by how much', async () => {
    await renderPage();
    await fillRequired();
    type('pointOfAccessDescription', 'a'.repeat(256));

    save();

    expect(
      await screen.findByText('Too long — the limit is 255 and this entry uses 256.'),
    ).toBeInTheDocument();
  });

  it('accepts a value at exactly the limit', async () => {
    await renderPage();
    await fillRequired();
    type('pointOfAccessDescription', 'a'.repeat(255));

    save();

    expect(screen.queryByText(/Too long/)).not.toBeInTheDocument();
  });
});

describe('AddSitePage — inline validation', () => {
  const blur = (name: string) => fireEvent.blur(field(name));

  it('marks the required fields, and only those the rules enforce', async () => {
    // An asterisk on a field nothing checks teaches the user to ignore asterisks.
    await renderPage();

    expect(screen.getByLabelText(/^Site #\s*\*/)).toBeInTheDocument();
    expect(screen.getByLabelText(/^Crossing Name\s*\*/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/^User Kilometres\s*\*/)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/^Trim Map Sheet #\s*\*/)).not.toBeInTheDocument();
  });

  it('drops the marker from the fields a storage site is excused', async () => {
    await renderPage();

    type('crossingSiteTypeCode', 'STRG');

    expect(screen.queryByLabelText(/^Crossing Name\s*\*/)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/^Kilometres\s*\*/)).not.toBeInTheDocument();
  });

  it('drops the district marker for a recreation site', async () => {
    await renderPage();

    type('crossingSiteTypeCode', 'REC');

    expect(screen.queryByLabelText(/^Recreation District\s*\*/)).not.toBeInTheDocument();
  });

  it('flags a value no further typing can rescue, without waiting', async () => {
    await renderPage();

    type('pointOfCommencementDistance', 'abc');

    expect(await screen.findByText('Must be a number, e.g. 12.5')).toBeInTheDocument();
  });

  it('leaves a number the user is part-way through writing alone', async () => {
    // "12." is a kilometre mark half typed. Marking it red on the way to "12.5" is the behaviour
    // this whole mode split exists to avoid.
    await renderPage();

    type('pointOfCommencementDistance', '12.');

    expect(screen.queryByText('Must be a number, e.g. 12.5')).not.toBeInTheDocument();
  });

  it('says nothing when the user tabs past a field they left empty', async () => {
    // A blank box is a gap, not a mistake. Reporting it on blur turns the whole form red for
    // anyone who reads it through before filling it in.
    await renderPage();

    blur('siteId');

    expect(screen.queryByText('Site # is required.')).not.toBeInTheDocument();
    expect(field('siteId')).not.toHaveAttribute('aria-invalid', 'true');
  });

  it('speaks once they leave a field they did fill in', async () => {
    await renderPage();

    type('pointOfCommencementDistance', '12.');
    blur('pointOfCommencementDistance');

    expect(await screen.findByText('Must be a number, e.g. 12.5')).toBeInTheDocument();
  });

  it('will not save a site with no position on the map', async () => {
    // The columns are nullable, so nothing at the database would stop this — but legacy refuses it
    // ("// Making longitude mandatory"), and a crossing nobody can find is not a record of a
    // crossing.
    await renderPage();
    await fillRequired();
    type('longitudeSeconds', '');

    save();

    expect(
      await screen.findByText('Longitude is required — degrees, minutes and seconds.'),
    ).toBeInTheDocument();
  });

  it('marks both coordinates as required on the form', async () => {
    await renderPage();

    expect(screen.getByText(/^Longitude/).textContent).toContain('*');
    expect(screen.getByText(/^Latitude/).textContent).toContain('*');
    // UTM is not — legacy checks it for being numeric and nothing more.
    expect(screen.getByText(/^UTM/).textContent).not.toContain('*');
  });

  it('still reports everything missing at Save', async () => {
    await renderPage();

    save();

    expect(await screen.findByText('Site # is required.')).toBeInTheDocument();
  });
});

describe('AddSitePage — the site type', () => {
  it("takes a crossing's Forest District from the road, not from the user", async () => {
    // Legacy's rule: SiteAction overwrites orgUnitNo from the road on every redisplay, and
    // setValidateResponse disables the field. A crossing is on a road, and the road knows its
    // district.
    await renderPage();
    type('crossingSiteTypeCode', 'CRS');
    type('forestFileId', 'R00123');
    type('roadSectionId', '01');

    expect(await screen.findByText('DPG - Prince George')).toBeInTheDocument();
    expect(screen.queryByTestId('site-form-orgUnitNo')).not.toBeInTheDocument();
  });

  it('leaves a crossing with no road no district to record', async () => {
    // Disabled and blank until a road is given, as legacy leaves it — the `else` branch of
    // setValidateResponse re-enables the field for a storage site and for nothing else.
    roadApi.getRoadSection.mockRejectedValue(new Error('No road section was found.'));
    await renderPage();
    type('crossingSiteTypeCode', 'CRS');

    expect(isReadOnlyCell('Forest District')).toBe(true);
    expect(screen.queryByTestId('site-form-orgUnitNo')).not.toBeInTheDocument();
  });

  it('lets a storage site choose its own district while it has no road', async () => {
    // A storage site may never have a road, so legacy gives the choice back rather than leaving
    // the field permanently empty.
    roadApi.getRoadSection.mockRejectedValue(new Error('No road section was found.'));
    await renderPage();

    type('crossingSiteTypeCode', 'STRG');

    expect(screen.getByTestId('site-form-orgUnitNo')).toBeInTheDocument();
  });

  it("narrows a recreation site's districts to its project file", async () => {
    // The file constrains the choice here rather than making it — CBR_GENERAL
    // .FIND_RECREATION_DISTRICTS cross-references the file to its recreation districts.
    await renderPage();

    type('crossingSiteTypeCode', 'REC');
    type('forestFileId', 'R00123');

    expect(screen.getByLabelText(/^Recreation District/)).toBeInTheDocument();
    await waitFor(() => {
      expect(api.getRecreationDistricts).toHaveBeenCalledWith('R00123');
    });
  });

  it('calls it a Recreation District for a recreation site, on the same column', async () => {
    // Legacy re-renders the whole page through redisplay() to achieve this; the column it writes
    // to is ORG_UNIT_NO either way, so only the label changes.
    await renderPage();
    type('crossingSiteTypeCode', 'REC');

    expect(screen.getByLabelText(/^Recreation District/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/^Forest District/)).not.toBeInTheDocument();
  });

  it('offers a Project Name instead of a Forest Service Road for a recreation site', async () => {
    await renderPage();
    expect(isReadOnlyCell('Forest Service Road')).toBe(true);

    type('crossingSiteTypeCode', 'REC');

    expect(isReadOnlyCell('Project Name')).toBe(true);
    expect(isReadOnlyCell('Forest Service Road')).toBe(false);
  });

  it('opens the road lookup from the magnifying glass beside Project File ID#', async () => {
    // Legacy opens a second browser window here — blocked by default in every current browser,
    // unreachable on a phone, and it leaves the form behind an unrelated window.
    await renderPage();

    fireEvent.click(screen.getByTestId('site-form-find-road'));

    expect(await screen.findByTestId('road-search-form')).toBeInTheDocument();
  });

  it('takes both halves of the pair from the road that was picked', async () => {
    roadApi.searchRoads.mockResolvedValue([
      {
        forestServiceRoad: 'Bowron FSR',
        forestFileId: 'R00123',
        roadSectionId: '01',
        tenureType: 'B40',
        clientName: null,
        clientNumber: null,
      },
    ]);
    await renderPage();
    fireEvent.click(screen.getByTestId('site-form-find-road'));
    fireEvent.click(await screen.findByTestId('road-search-submit'));

    fireEvent.click(await screen.findByTestId('road-search-row-R00123-01'));

    expect(field('forestFileId')).toHaveValue('R00123');
    expect(field('roadSectionId')).toHaveValue('01');
  });

  it('fills the road name once both halves of the pair name a section', async () => {
    // Legacy asks the same question on every change of either box — through `redisplay()`, which
    // re-submits the whole form and reloads the page while the user is still filling it in.
    roadApi.getRoadSection.mockResolvedValue({
      forestFileId: 'R00123',
      roadSectionId: '01',
      forestServiceRoad: 'Bowron FSR',
    });
    await renderPage();

    type('forestFileId', 'R00123');
    type('roadSectionId', '01');

    expect(await screen.findByText('Bowron FSR')).toBeInTheDocument();
    await waitFor(() => {
      expect(roadApi.getRoadSection).toHaveBeenCalledWith('R00123', '01');
    });
  });

  it('asks nothing until both halves are there', async () => {
    // A road file alone names many sections, and they are different roads.
    await renderPage();

    type('forestFileId', 'R00123');

    await waitFor(() => {
      expect(roadApi.getRoadSection).not.toHaveBeenCalled();
    });
  });

  it('says nothing when the pair names no section, rather than reporting it', async () => {
    // A 404 here is the ordinary answer to a pair half typed, and the only answer in an
    // environment where the road view is stubbed.
    await renderPage();

    type('forestFileId', 'R00123');
    type('roadSectionId', '99');

    await waitFor(() => {
      expect(roadApi.getRoadSection).toHaveBeenCalled();
    });
    expect(screen.queryByText(/No road section/)).not.toBeInTheDocument();
  });

  it('does not require a district for a recreation site', async () => {
    await renderPage();
    await fillRequired();
    type('crossingSiteTypeCode', 'REC');
    type('orgUnitNo', '');

    save();

    expect(screen.queryByText('Forest District is required.')).not.toBeInTheDocument();
  });
});

describe('AddSitePage — the rest of the screen', () => {
  it('posts the form and opens the site that was stored', async () => {
    // The stored site, not the one that was sent: the server upper-cases the number and fills in
    // the road name and maintainer, so the detail page should read the record.
    siteApi.createSite.mockResolvedValue({ siteId: 'BOWRON-001' });
    await renderPage();
    await fillRequired();

    save();

    await waitFor(() => expect(siteApi.createSite).toHaveBeenCalledTimes(1));
    expect(navigate).toHaveBeenCalledWith('/inventory/site/BOWRON-001');
  });

  it('sends the coordinates as decimal degrees, longitude negated', async () => {
    // The form holds six boxes; the columns hold two decimals. Legacy converts at the same edge —
    // `SiteForm.getSiteDTO` flips the sign before anything reaches INSERT_SITE.
    await renderPage();
    await fillRequired();

    save();

    await waitFor(() => expect(siteApi.createSite).toHaveBeenCalledTimes(1));
    const sent = siteApi.createSite.mock.calls[0][0];
    expect(sent.longitude).toBeCloseTo(-122.504306, 5);
    expect(sent.latitude).toBeCloseTo(53.916667, 5);
  });

  it('puts a rule the server refused beside the box it blames', async () => {
    // The server applies the same rules deliberately — the form is a convenience, not a gate — so
    // a 400 means the two disagreed, and it belongs where every other message on this form lives.
    siteApi.createSite.mockRejectedValue({
      body: { detail: 'Site cannot be saved', fieldErrors: { crossingName: 'Server says no.' } },
    });
    await renderPage();
    await fillRequired();

    save();

    expect(await screen.findByText('Server says no.')).toBeInTheDocument();
    expect(screen.queryByTestId('add-site-save-error')).not.toBeInTheDocument();
  });

  it('falls back to a notification when the failure blames no field', async () => {
    // A 500, an expired token, a network drop. Without this a refused save is a button that does
    // nothing.
    siteApi.createSite.mockRejectedValue({ body: { detail: 'Service unavailable.' } });
    await renderPage();
    await fillRequired();

    save();

    expect(await screen.findByTestId('add-site-save-error')).toHaveTextContent(
      'Service unavailable.',
    );
  });

  it('does not post a form the rules already refuse', async () => {
    await renderPage();
    await fillRequired();
    type('crossingName', '');

    save();

    await waitFor(() => expect(screen.getByText('Crossing Name is required.')).toBeInTheDocument());
    expect(siteApi.createSite).not.toHaveBeenCalled();
  });

  it('puts Save and Cancel beside the heading rather than below the last field', async () => {
    // The form scrolls past a laptop screen, so at the bottom the two controls that end the task
    // are the two the user has to go looking for.
    await renderPage();

    const heading = screen.getByRole('heading', { name: 'Add Site' });
    expect(heading.parentElement).toContainElement(screen.getByTestId('add-site-save'));
    expect(heading.parentElement).toContainElement(screen.getByTestId('add-site-cancel'));
  });

  it('keeps Save submitting the form it now sits outside', async () => {
    // The `form` attribute is the whole of the connection, and losing it leaves a button that looks
    // right and does nothing — with no error anywhere to notice. It is also what keeps Enter in a
    // text box saving, which an onClick handler would have quietly taken away.
    await renderPage();
    const button = screen.getByTestId('add-site-save');

    expect(button.closest('form')).toBeNull();
    expect(button).toHaveAttribute('type', 'submit');
    expect(button).toHaveAttribute('form', 'site-form');
  });

  const cancel = () => fireEvent.click(screen.getByTestId('add-site-cancel'));

  it('returns to Site Search on Cancel', async () => {
    // Cancel simply leaves. Whether that is allowed is UnsavedChangesGuard's question, asked of
    // every exit rather than this one — see its own tests.
    await renderPage();

    cancel();

    expect(navigate).toHaveBeenCalledWith('/inventory/site-search');
  });

  it('declares nothing to lose on an untouched form', async () => {
    await renderPage();

    expect(setDirty).toHaveBeenLastCalledWith(false);
  });

  it('declares unsaved work as soon as anything is entered', async () => {
    await renderPage();

    type('siteId', 'BOWRON-001');

    expect(setDirty).toHaveBeenLastCalledWith(true);
  });

  it('stops declaring unsaved work once an edit is undone', async () => {
    // Compared against the empty form rather than tracked with a flag: typing a character and
    // deleting it again leaves nothing to lose, so nothing should ask about it.
    await renderPage();
    type('siteId', 'B');

    type('siteId', '');

    expect(setDirty).toHaveBeenLastCalledWith(false);
  });

  it('drops a management area when the district changes out from under it', async () => {
    // It would otherwise submit an area that is not in the list the user can now see. The district
    // now moves when the road does, so the road is what changes it.
    await renderPage();
    type('crossingSiteTypeCode', 'STRG');
    type('orgUnitNo', '18');
    type('managementOrgUnitNo', '');

    type('orgUnitNo', '');

    expect(field('managementOrgUnitNo')).toHaveValue('');
  });
});

describe('AddSitePage — Project Name', () => {
  it('shows the recreation project where a crossing shows its road', async () => {
    // `site.jsp:803-824` is one <c:if>: "Forest Service Road" for every type but REC, "Project
    // Name" for REC, never both. The two come from different tables, so the page picks the
    // question as well as the label.
    api.getRecreationProjectName.mockResolvedValue({
      forestFileId: 'R00123',
      projectName: 'Bowron Lake',
    });
    await renderPage();

    type('crossingSiteTypeCode', 'REC');
    type('forestFileId', 'R00123');

    expect(await screen.findByText('Bowron Lake')).toBeInTheDocument();
    expect(isReadOnlyCell('Project Name')).toBe(true);
    expect(isReadOnlyCell('Forest Service Road')).toBe(false);
  });

  it('asks nothing of the recreation table for a crossing site', async () => {
    // A crossing's Project File ID# names a road file. Looking it up in RECREATION_PROJECT would
    // be asking the wrong table about the right number.
    await renderPage();

    type('crossingSiteTypeCode', 'CRS');
    type('forestFileId', 'R00123');
    // Both halves, because the road lookup asks nothing until it has a section as well — waiting
    // on it is what makes the assertion below more than a race.
    type('roadSectionId', '01');

    await waitFor(() => expect(roadApi.getRoadSection).toHaveBeenCalled());
    expect(api.getRecreationProjectName).not.toHaveBeenCalled();
  });

  it('leaves the cell empty when the file names no project', async () => {
    // A recreation file id is typed by hand; a half-typed one is the ordinary state of the field.
    api.getRecreationProjectName.mockResolvedValue({ forestFileId: 'NOPE', projectName: null });
    await renderPage();

    type('crossingSiteTypeCode', 'REC');
    type('forestFileId', 'NOPE');

    await waitFor(() => expect(api.getRecreationProjectName).toHaveBeenCalled());
    expect(isReadOnlyCell('Project Name')).toBe(true);
  });
});
