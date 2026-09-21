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
  getBusinessAreas: vi.fn(),
}));
const clientApi = vi.hoisted(() => ({ searchClients: vi.fn() }));

vi.mock('@/services/APIs', () => ({
  default: { configuration: api, client: clientApi },
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
    expect(screen.getByTestId('add-site-crossingSiteTypeCode')).not.toBeDisabled();
  });
  return result;
};

const field = (name: string) => screen.getByTestId(`add-site-${name}`);
const type = (name: string, value: string) => fireEvent.change(field(name), { target: { value } });
const save = () => fireEvent.click(screen.getByTestId('add-site-save'));

/** Fills everything the form insists on, so a case can break exactly one thing. */
const fillRequired = () => {
  type('siteId', 'BOWRON-001');
  type('crossingSiteStatusCode', 'ACT');
  type('crossingSiteTypeCode', 'CRS');
  type('structureInspectionStatusCode', 'INS');
  type('forestFileId', 'R00123');
  type('roadSectionId', '01');
  type('orgUnitNo', '18');
  type('crossingName', 'Deadman Creek');
  type('pointOfCommencementDistance', '12.50');
  // Both coordinates in full — legacy refuses the save without all six boxes.
  type('longitudeDegrees', '122');
  type('longitudeMinutes', '30');
  type('longitudeSeconds', '15.5');
  type('latitudeDegrees', '53');
  type('latitudeMinutes', '55');
  type('latitudeSeconds', '0');
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
  api.getBusinessAreas.mockResolvedValue([]);
  clientApi.searchClients.mockReset();
  clientApi.searchClients.mockResolvedValue([]);
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
      'orgUnitNo',
      'maintainer',
      'managementOrgUnitNo',
      'derived-road',
      'pointOfCommencementDistance',
      'userKm',
      'crossingName',
      'businessAreaOrgUnitNo',
      'trimMapSheetNumber',
      'ntsMapSheetNumber',
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
  });

  it('keeps the legacy labels, which is the vocabulary the business uses', async () => {
    await renderPage();

    // Regexes, not exact strings: a required field's label carries a trailing asterisk, and
    // pinning the exact text here would make every future required marker a test failure.
    expect(screen.getByLabelText(/^Site #/)).toBeInTheDocument();
    expect(screen.getByLabelText(/^Project File ID#/)).toBeInTheDocument();
    expect(screen.getByLabelText(/^Br\./)).toBeInTheDocument();
    expect(screen.getByLabelText('BCTS BA Responsible')).toBeInTheDocument();
    expect(screen.getByLabelText('1:50,000 Map Sheet #')).toBeInTheDocument();
    expect(screen.getByLabelText('Site Details')).toBeInTheDocument();
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

    const order = ['add-site-longitudeDegrees', 'add-site-latitudeDegrees', 'add-site-utmZone'];
    const positions = order.map((id) =>
      Array.from(document.querySelectorAll('[data-testid]')).indexOf(screen.getByTestId(id)),
    );
    expect(positions).toEqual([...positions].sort((a, b) => a - b));

    const details = screen.getByTestId('add-site-pointOfAccessDescription');
    expect(
      screen.getByTestId('add-site-utmNorthing').compareDocumentPosition(details) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  it('still names each coordinate box for a screen reader with the labels hidden', async () => {
    // hideLabel keeps the <label> and hides it visually. The temptation when removing a visible
    // label is to drop it altogether, which leaves nine identical unnamed boxes in three rows —
    // unusable without sight of the layout.
    await renderPage();

    expect(
      screen.getByLabelText('Degrees', { selector: '#add-site-longitudeDegrees' }),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText('Seconds', { selector: '#add-site-latitudeSeconds' }),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText('Northing', { selector: '#add-site-utmNorthing' }),
    ).toBeInTheDocument();
  });

  it('keeps longitude and latitude on one row, with UTM on its own', async () => {
    // They are one point, so a reader checks them against each other. UTM says the same thing a
    // different way rather than completing them, so it does not belong in the pair.
    await renderPage();

    const pair = document.querySelector('.add-site__coordinate-pair');
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
    fillRequired();
    type('crossingSiteTypeCode', 'STRG');
    type('structureInspectionStatusCode', 'DNI');
    type('crossingName', '');
    type('pointOfCommencementDistance', '');

    save();

    expect(screen.queryByText('Crossing Name is required.')).not.toBeInTheDocument();
    expect(screen.queryByText('Kilometres is required.')).not.toBeInTheDocument();
  });

  it('reports a status that disagrees with the inspection status above the form', async () => {
    // Either field could be the one to change, so marking one would be choosing for the user.
    await renderPage();
    fillRequired();
    type('structureInspectionStatusCode', 'DNI');

    save();

    const conflict = await screen.findByTestId('add-site-conflicts');
    expect(conflict).toHaveTextContent('must have an Inspection Status of Inspect');
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
    fillRequired();
    type('pointOfAccessDescription', 'a'.repeat(256));

    save();

    expect(
      await screen.findByText('Too long — the limit is 255 and this entry uses 256.'),
    ).toBeInTheDocument();
  });

  it('accepts a value at exactly the limit', async () => {
    await renderPage();
    fillRequired();
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

    type('userKm', 'abc');

    expect(await screen.findByText('Must be a number, e.g. 12.5')).toBeInTheDocument();
  });

  it('leaves a number the user is part-way through writing alone', async () => {
    // "12." is a kilometre mark half typed. Marking it red on the way to "12.5" is the behaviour
    // this whole mode split exists to avoid.
    await renderPage();

    type('userKm', '12.');

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

    type('userKm', '12.');
    blur('userKm');

    expect(await screen.findByText('Must be a number, e.g. 12.5')).toBeInTheDocument();
  });

  it('will not save a site with no position on the map', async () => {
    // The columns are nullable, so nothing at the database would stop this — but legacy refuses it
    // ("// Making longitude mandatory"), and a crossing nobody can find is not a record of a
    // crossing.
    await renderPage();
    fillRequired();
    type('longitudeSeconds', '');

    save();

    expect(
      await screen.findByText('Longitude is required — degrees, minutes and seconds.'),
    ).toBeInTheDocument();
  });

  it('marks both coordinates as required on the form', async () => {
    await renderPage();

    expect(screen.getByText(/^Longitude \(west\)/).textContent).toContain('*');
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
  it('asks for a Forest District for a crossing', async () => {
    await renderPage();
    type('crossingSiteTypeCode', 'CRS');

    expect(screen.getByLabelText(/^Forest District/)).toBeInTheDocument();
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
    expect(screen.getByLabelText('Forest Service Road')).toBeInTheDocument();

    type('crossingSiteTypeCode', 'REC');

    expect(screen.getByLabelText('Project Name')).toBeInTheDocument();
  });

  it('does not require a district for a recreation site', async () => {
    await renderPage();
    fillRequired();
    type('crossingSiteTypeCode', 'REC');
    type('orgUnitNo', '');

    save();

    expect(screen.queryByText('Forest District is required.')).not.toBeInTheDocument();
  });
});

describe('AddSitePage — the rest of the screen', () => {
  it('keeps the temporary notice out of the heading row', async () => {
    // It belongs with the notice that explains it, not beside the title where it competes with
    // Save and Cancel and reads as part of what the screen is rather than what state it is in.
    await renderPage();

    const heading = screen.getByRole('heading', { name: 'Add Site' });
    const tag = screen.getByText('Under construction');

    expect(heading.parentElement).not.toContainElement(tag);
    expect(screen.getByTestId('add-site-placeholder').parentElement).toContainElement(tag);
  });

  it('says plainly that Save does not store anything yet', async () => {
    // The alternative is a Save that appears to work, which is worse than a button that says so.
    await renderPage();

    expect(screen.getByTestId('add-site-placeholder')).toBeInTheDocument();
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
    expect(button).toHaveAttribute('form', 'add-site-form');
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
    // It would otherwise submit an area that is not in the list the user can now see.
    await renderPage();
    type('orgUnitNo', '18');
    type('crossingSiteTypeCode', 'CRS');

    type('orgUnitNo', '');

    expect(field('managementOrgUnitNo')).toHaveValue('');
  });
});
