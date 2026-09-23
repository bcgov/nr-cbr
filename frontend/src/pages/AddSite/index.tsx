import { Save } from '@carbon/icons-react';
import { Button, Column, Grid, InlineNotification } from '@carbon/react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import PageTitle from '@/components/core/PageTitle';
import RoadSearchModal from '@/components/RoadSearchModal';
import SiteForm, { FORM_ID, type SiteCodeTables } from '@/components/SiteForm';

import type { FC } from 'react';

import { syncCoordinates } from '@/components/SiteForm/coordinateSync';
import { toCreateRequest } from '@/components/SiteForm/request';
import { EMPTY_SITE, SITE_TYPE, type SiteFormValues } from '@/components/SiteForm/types';
import {
  crossFieldErrors,
  crossFieldWarnings,
  fieldErrors,
  fieldWarnings,
  type SiteErrors,
} from '@/components/SiteForm/validation';
import {
  useBusinessAreas,
  useForestDistricts,
  useRecreationDistricts,
  useRecreationProjectName,
  useManagementAreas,
  useSiteReferenceDataState,
  useSiteStatusCodes,
  useSiteTypeCodes,
  useSpecialAccessCodes,
  useStructureInspectionStatusCodes,
} from '@/hooks/useConfiguration';
import { useCreateSite } from '@/hooks/useCreateSite';
import { useRoadSection } from '@/hooks/useRoadSection';
import { useSettledFields } from '@/hooks/useSettledFields';
import { useSiteNumberTaken } from '@/hooks/useSiteNumberTaken';
import { useUnsavedChangesPrompt } from '@/hooks/useUnsavedChangesPrompt';
import { apiErrorMessage } from '@/utils/apiError';
import { errorsForSettledFields } from '@/utils/validation';

import './addSite.scss';

/**
 * Add Site — the blank site form, second in the legacy Inventory menu.
 *
 * <p>Ported from `site.jsp` and `SiteForm.validate`. Legacy reaches this through `showSite.do`
 * with no site id and saves through `addSite.do`; the two gates on the menu entry are
 * `/showSite` + `/addSite`, which resolves to LEVEL 2 (see routePaths).
 *
 * <p><b>Validation runs on submit, not on every keystroke.</b> Half of these fields are required
 * and all of them start empty, so validating as the user types would paint the form red before
 * they had touched anything. Once they have tried to save, the messages follow their corrections
 * live — which is the point at which the feedback is about what they did rather than what they
 * have not done yet.
 */
/**
 * The three fields legacy locks on every branch of `site.jsp`, left off a form for a site that does
 * not exist yet.
 *
 * <p>Designated Maintainer is `disabled` for Level 1 and above and `readonly` below it
 * (`site.jsp:734-740`, `778-784`), and `showClientSearch()` — the lookup that would fill it — is
 * defined at line 529 and called from nowhere. A disabled input is not submitted, so <b>legacy's
 * Add Site always stores a site with no maintainer</b>. User Kilometres is the distance posted on
 * the sign, `disabled` at line 863. BCTS BA Responsible is `disabled` at line 901 even in the
 * Level 2 branch.
 *
 * <p>Hidden rather than shown read-only: on a create they could only ever be blank, and an empty
 * cell with a label invites a user to look for the control that fills it. Site Detail shows all
 * three, where the site is stored and they have values.
 */
const NOT_SET_ON_CREATE: ReadonlySet<keyof SiteFormValues> = new Set([
  'clientNumber',
  'userKm',
  'businessAreaOrgUnitNo',
]);

const AddSitePage: FC = () => {
  const navigate = useNavigate();

  const [site, setSite] = useState<SiteFormValues>(EMPTY_SITE);
  /** Whether Save has been pressed — see the note above on when messages appear. */
  const [submitted, setSubmitted] = useState(false);
  /**
   * Whether the site has been stored.
   *
   * <p>Only used to stand the unsaved-changes guard down: the form is still full of values when the
   * save succeeds, so without this the navigation to the new site's page would be intercepted and
   * the user asked whether to discard work that is already safely in the database.
   */
  const [saved, setSaved] = useState(false);
  const { settled, markSettled } = useSettledFields();

  const siteStatusCodes = useSiteStatusCodes();
  const siteTypeCodes = useSiteTypeCodes();
  const structureInspectionStatusCodes = useStructureInspectionStatusCodes();
  const specialAccessCodes = useSpecialAccessCodes();
  const forestDistricts = useForestDistricts();
  const businessAreas = useBusinessAreas();
  // Refetches under its own key whenever the district changes, and does not run until one is
  // picked — the same coupling Site Search has.
  const managementAreas = useManagementAreas(site.orgUnitNo);
  // Only ever asked for once a project file is given — a recreation site's districts come from
  // the file, not from the whole province.
  const recreationDistricts = useRecreationDistricts(site.forestFileId);
  // The other half of the same question the road lookup asks. A recreation site's Project File ID#
  // names a recreation project rather than a road file, so only one of the two is ever asked.
  const isRecreationSite = site.crossingSiteTypeCode === SITE_TYPE.RECREATION;
  const recreationProject = useRecreationProjectName(site.forestFileId, isRecreationSite);
  const referenceData = useSiteReferenceDataState();
  // Debounced inside the hook, and asked nothing until both halves are present — a road file alone
  // names many sections, and they are different roads.
  const road = useRoadSection(site.forestFileId, site.roadSectionId);
  // Asked as the number is typed, so a clash is known before the other thirty fields are filled
  // in. Legacy checks the same thing in the same two places — live, and again at the save.
  const siteNumberTaken = useSiteNumberTaken(site.siteId);
  const created = useCreateSite();

  /**
   * The road sets the Forest District, as `SiteAction` does on every redisplay.
   *
   * <p>Never for a recreation site, whose district is the user's choice from a list the file
   * narrows — writing the road's org unit there would overwrite what they picked.
   */
  useEffect(() => {
    const fromRoad = road.data?.orgUnitNo;
    if (fromRoad === undefined || fromRoad === null) return;
    if (site.crossingSiteTypeCode === SITE_TYPE.RECREATION) return;
    setSite((current) =>
      current.orgUnitNo === String(fromRoad)
        ? current
        : { ...current, orgUnitNo: String(fromRoad), managementOrgUnitNo: '' },
    );
  }, [road.data, site.crossingSiteTypeCode]);

  const codeTables = useMemo<SiteCodeTables>(
    () => ({
      siteStatusCodes: siteStatusCodes.data ?? [],
      siteTypeCodes: siteTypeCodes.data ?? [],
      structureInspectionStatusCodes: structureInspectionStatusCodes.data ?? [],
      specialAccessCodes: specialAccessCodes.data ?? [],
      forestDistricts: forestDistricts.data ?? [],
      recreationDistricts: recreationDistricts.data ?? [],
      managementAreas: managementAreas.data ?? [],
      businessAreas: businessAreas.data ?? [],
    }),
    [
      siteStatusCodes.data,
      siteTypeCodes.data,
      structureInspectionStatusCodes.data,
      specialAccessCodes.data,
      forestDistricts.data,
      recreationDistricts.data,
      managementAreas.data,
      businessAreas.data,
    ],
  );

  /** Whether the road lookup is open. */
  const [findingRoad, setFindingRoad] = useState(false);

  /**
   * A road was picked. Both halves of the pair go in together — they identify one section between
   * them, and setting either alone would leave the form describing a road that does not exist.
   */
  const chooseRoad = useCallback((road: { forestFileId: string; roadSectionId: string }) => {
    setSite((current) => ({
      ...current,
      forestFileId: road.forestFileId,
      roadSectionId: road.roadSectionId,
    }));
    setFindingRoad(false);
  }, []);

  const update = useCallback(
    <K extends keyof SiteFormValues>(field: K, value: SiteFormValues[K]) => {
      setSite((current) =>
        syncCoordinates(
          {
            ...current,
            [field]: value,
            // Two ways a Management Area selection stops being valid. Changing the district
            // changes which areas exist, so one picked under the old district would submit an
            // area the user can no longer see. Switching to a recreation site takes the field off
            // the form entirely, as it does on legacy's — and a hidden box must not submit a value
            // at all. (Legacy's session-scoped form bean keeps it, which is a framework artefact
            // rather than a rule: the row is simply not rendered.)
            ...(field === 'orgUnitNo' ||
            (field === 'crossingSiteTypeCode' && value === SITE_TYPE.RECREATION)
              ? { managementOrgUnitNo: '' }
              : {}),
          },
          // Which box was touched decides which notation is recomputed — legacy's `longLatUpdate`
          // and `utmUpdate` flags, set the same way from the changed field's name.
          field,
        ),
      );
    },
    [],
  );

  /**
   * What the form is saying right now, in three layers.
   *
   * <p>`'typing'` runs on every keystroke and carries only the rules no further typing can satisfy
   * — a letter in a kilometre box, a third decimal place, 91 degrees of latitude. Those are wrong
   * the moment they are typed and waiting to say so helps nobody.
   *
   * <p>`errorsForSettledFields` adds the full check for boxes the user has left *and* filled in.
   * An empty box is a gap rather than a mistake, so tabbing through the form never turns it red.
   *
   * <p>Pressing Save shows everything, including what is still missing.
   */
  const settledErrors = fieldErrors(site, 'settled');
  const conflicts = crossFieldErrors(site);
  const errors: SiteErrors = {
    ...fieldErrors(site, 'typing'),
    ...errorsForSettledFields(settledErrors, settled, (key) => String(site[key] ?? '')),
    ...(submitted ? settledErrors : {}),
    // The two-field rules, live rather than held to Save — see `crossFieldErrors`. After the
    // required set, so "this combination is wrong" wins over "this field is required" on the same
    // box; the rule cannot fire unless all three fields are filled in, so the two rarely meet.
    ...conflicts,
    // Last, so it wins the Site # box: "already used" is a more specific and more actionable
    // complaint than "required", and the two can never both be true anyway.
    ...(siteNumberTaken ? { siteId: 'A site with this number already exists.' } : {}),
    // What the server refused, over everything the form decided. It applies the same rules, so
    // these arrive only where the two disagreed — and when they do, the server is the one that
    // decides whether the site stores.
    ...created.fieldErrors,
  };

  /**
   * The amber tier, which never waits for Save.
   *
   * <p>Legacy shows these the moment they become true — its live `validate` call returns warnings
   * and errors in one list — and they read differently from an error for it: nothing here is
   * wrong, so there is no correction to hold back until the user says they are done. A warning
   * that appears only after Save has been refused would be advice arriving too late to take.
   *
   * <p>Every rule here depends on fields being filled in, so an untouched form is silent.
   */
  const warnings: SiteErrors = { ...fieldWarnings(site), ...crossFieldWarnings(site) };

  /**
   * Saves, once the form agrees the site is storable.
   *
   * <p>The local check first, so an obviously incomplete form is answered without a round trip —
   * but it is not the gate. The server applies the same rules and is the only thing that can
   * actually refuse; anything it sends back lands beside the boxes through `created.fieldErrors`.
   */
  const save = useCallback(() => {
    setSubmitted(true);
    if (
      siteNumberTaken ||
      Object.keys(settledErrors).length > 0 ||
      Object.keys(crossFieldErrors(site)).length > 0
    ) {
      return;
    }
    created.mutate(toCreateRequest(site), {
      // To the site that was stored, not the one that was sent: the server upper-cases the number
      // and fills in the road name and maintainer, so the detail page should read the record.
      onSuccess: (stored) => {
        setSaved(true);
        navigate(`/inventory/site/${stored.siteId}`);
      },
    });
  }, [created, navigate, settledErrors, site, siteNumberTaken]);

  /**
   * Anything typed, picked or ticked.
   *
   * <p>Compared against the empty form rather than tracked with a flag, so undoing an edit back to
   * where it started counts as untouched — a user who types a character and deletes it again has
   * nothing to lose, and should not be asked.
   */
  const isDirty = useMemo(
    () =>
      (Object.keys(EMPTY_SITE) as (keyof SiteFormValues)[]).some(
        (field) => site[field] !== EMPTY_SITE[field],
      ),
    [site],
  );

  /**
   * Registers the form's state with `UnsavedChangesGuard`, which owns the prompt for the whole
   * application.
   *
   * <p><b>Legacy asks nothing on Cancel</b> — it is a bare
   * `window.open('showWelcome.do', '_self')`. The prompt is not invented for CBR, though: the same
   * screen already carries `"Unsaved changes will be lost, do you wish to continue?"` and puts it
   * in front of the two *other* ways off the page, Add Structure and Display Structures. Cancel
   * was simply never wired to it.
   *
   * <p>Declaring the state rather than prompting here is what closes the rest of that gap. A
   * confirmation attached to Cancel catches one exit; the side nav, a breadcrumb and the browser's
   * back button would each still discard the form in silence.
   */
  useUnsavedChangesPrompt(isDirty && !saved);

  /** Cancel simply leaves. If there is anything to lose, the guard intercepts and asks. */
  const cancel = useCallback(() => navigate('/inventory/site-search'), [navigate]);

  return (
    <Grid fullWidth className="default-grid">
      <PageTitle
        title="Add Site"
        subtitle="Record a new crossing site."
        breadCrumbs={[{ name: 'Inventory', path: '/inventory' }]}
      >
        {/* Beside the heading rather than under the last field. The form is long enough to scroll
            past on any laptop, and at the bottom the two controls that end the task are the two a
            user has to go looking for. Up here they are on screen the whole time, and their
            position says what the page is for before it is read.

            Save is still a submit button — `form` ties it back to the form it sits outside, so
            pressing Enter in a text box saves exactly as it did when the button was inside. */}
        <div className="add-site__header-actions">
          <Button kind="secondary" size="md" data-testid="add-site-cancel" onClick={cancel}>
            Cancel
          </Button>
          <Button
            kind="primary"
            size="md"
            type="submit"
            form={FORM_ID}
            renderIcon={Save}
            disabled={created.isPending}
            data-testid="add-site-save"
          >
            {created.isPending ? 'Saving…' : 'Save'}
          </Button>
        </div>
      </PageTitle>

      {referenceData.isError && (
        <Column sm={4} md={8} lg={16}>
          {/* Inline, not a toast: the dropdowns below are the thing that failed, and the message
              has to stay on screen beside them rather than time out. */}
          <InlineNotification
            kind="warning"
            lowContrast
            hideCloseButton
            title="Some lists could not be loaded"
            subtitle="The dropdowns may be incomplete. Reload the page to try again."
            data-testid="add-site-reference-error"
          />
        </Column>
      )}

      {/* A save the server refused for a reason no single box is at fault for — it was down, the
          token had expired, the role was wrong. Anything it blamed on a field is already beside
          that field; this is what is left over, and without it a refused save looks like a button
          that does nothing. Not a validation message, which is why it is a notification. */}
      {created.isError && Object.keys(created.fieldErrors).length === 0 && (
        <Column sm={4} md={8} lg={16}>
          <InlineNotification
            kind="error"
            lowContrast
            hideCloseButton
            title="This site could not be saved"
            subtitle={apiErrorMessage(created.error, 'The site could not be saved. Try again.')}
            data-testid="add-site-save-error"
          />
        </Column>
      )}

      <Column sm={4} md={8} lg={16}>
        <SiteForm
          values={site}
          errors={errors}
          warnings={warnings}
          codeTables={codeTables}
          codeTablesLoading={referenceData.isLoading}
          managementAreasLoading={managementAreas.isFetching}
          onChange={update}
          onSettle={markSettled}
          onSave={save}
          onFindRoad={() => setFindingRoad(true)}
          forestServiceRoad={
            isRecreationSite
              ? (recreationProject.data?.projectName ?? '')
              : (road.data?.forestServiceRoad ?? '')
          }
          forestServiceRoadLoading={
            isRecreationSite ? recreationProject.isFetching : road.isFetching
          }
          roadResolved={Boolean(road.data)}
          hiddenFields={NOT_SET_ON_CREATE}
        />
      </Column>

      {/* Mounted only while open. Carbon keeps a closed modal's content in the document, and a
          second "Project File ID#" label sitting invisibly beside the real one confuses a screen
          reader exactly as much as it confuses a test. */}
      {findingRoad && (
        <RoadSearchModal onSelect={chooseRoad} onClose={() => setFindingRoad(false)} />
      )}
    </Grid>
  );
};

export default AddSitePage;
