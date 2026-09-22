import { Save } from '@carbon/icons-react';
import { Button, Column, Grid, InlineNotification } from '@carbon/react';
import { useCallback, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import PageTitle from '@/components/core/PageTitle';
import UnderConstructionTag from '@/components/core/Tags/UnderConstructionTag';
import SiteForm, { FORM_ID, type SiteCodeTables } from '@/components/SiteForm';

import type { FC } from 'react';

import { EMPTY_SITE, type SiteFormValues } from '@/components/SiteForm/types';
import { crossFieldErrors, fieldErrors, type SiteErrors } from '@/components/SiteForm/validation';
import {
  useBusinessAreas,
  useForestDistricts,
  useManagementAreas,
  useSiteReferenceDataState,
  useSiteStatusCodes,
  useSiteTypeCodes,
  useSpecialAccessCodes,
  useStructureInspectionStatusCodes,
} from '@/hooks/useConfiguration';
import { useSettledFields } from '@/hooks/useSettledFields';
import { useUnsavedChangesPrompt } from '@/hooks/useUnsavedChangesPrompt';
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
const AddSitePage: FC = () => {
  const navigate = useNavigate();

  const [site, setSite] = useState<SiteFormValues>(EMPTY_SITE);
  /** Whether Save has been pressed — see the note above on when messages appear. */
  const [submitted, setSubmitted] = useState(false);
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
  const referenceData = useSiteReferenceDataState();

  const codeTables = useMemo<SiteCodeTables>(
    () => ({
      siteStatusCodes: siteStatusCodes.data ?? [],
      siteTypeCodes: siteTypeCodes.data ?? [],
      structureInspectionStatusCodes: structureInspectionStatusCodes.data ?? [],
      specialAccessCodes: specialAccessCodes.data ?? [],
      forestDistricts: forestDistricts.data ?? [],
      managementAreas: managementAreas.data ?? [],
      businessAreas: businessAreas.data ?? [],
    }),
    [
      siteStatusCodes.data,
      siteTypeCodes.data,
      structureInspectionStatusCodes.data,
      specialAccessCodes.data,
      forestDistricts.data,
      managementAreas.data,
      businessAreas.data,
    ],
  );

  const update = useCallback(
    <K extends keyof SiteFormValues>(field: K, value: SiteFormValues[K]) => {
      setSite((current) => ({
        ...current,
        [field]: value,
        // Changing the district changes which management areas exist, so a selection made under
        // the old one has to go — it would otherwise submit an area that is not in the list the
        // user can now see. Site Search drops it for the same reason.
        ...(field === 'orgUnitNo' ? { managementOrgUnitNo: '' } : {}),
      }));
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
  const errors: SiteErrors = {
    ...fieldErrors(site, 'typing'),
    ...errorsForSettledFields(settledErrors, settled, (key) => String(site[key] ?? '')),
    ...(submitted ? settledErrors : {}),
  };

  // Above the form, and only once Save has been pressed: each of these is a disagreement between
  // two fields, and neither is wrong until the user says they have finished choosing both.
  const conflicts = submitted ? crossFieldErrors(site) : [];

  const save = useCallback(() => {
    setSubmitted(true);
    if (Object.keys(settledErrors).length > 0 || crossFieldErrors(site).length > 0) {
      return;
    }
    // The endpoint is not built yet. Nothing is silently dropped: the notification below says so,
    // rather than a Save that appears to work.
  }, [settledErrors, site]);

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
  useUnsavedChangesPrompt(isDirty);

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
            data-testid="add-site-save"
          >
            Save
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

      {conflicts.length > 0 && (
        <Column sm={4} md={8} lg={16}>
          {/* Above the form rather than beside a box, because each of these is a disagreement
              between two fields and either one could be the one to change. Marking one would be
              choosing for the user. */}
          <InlineNotification
            kind="error"
            lowContrast
            hideCloseButton
            title="This site cannot be saved"
            subtitle={conflicts.join(' ')}
            data-testid="add-site-conflicts"
          />
        </Column>
      )}

      {/* Everything on this page that is true only until the create endpoint lands, in one block.
          The tag used to sit in the heading, where it competed with Save and Cancel for the row and
          read as part of the screen's identity rather than as a note about its state — and where
          removing it later would mean editing the page header. Here the whole block deletes in one
          edit, and the notification says specifically what the tag only gestures at. */}
      <Column sm={4} md={8} lg={16}>
        <div className="add-site__notice">
          <UnderConstructionTag type="page" />
          <InlineNotification
            kind="info"
            lowContrast
            hideCloseButton
            title="Saving is not available yet"
            subtitle={
              'The form is complete and validates as the legacy screen does, but the endpoint ' +
              'behind Save has not been built — nothing entered here is stored.'
            }
            data-testid="add-site-placeholder"
          />
        </div>
      </Column>

      <Column sm={4} md={8} lg={16}>
        <SiteForm
          values={site}
          errors={errors}
          codeTables={codeTables}
          codeTablesLoading={referenceData.isLoading}
          managementAreasLoading={managementAreas.isFetching}
          onChange={update}
          onSettle={markSettled}
          onSave={save}
        />
      </Column>
    </Grid>
  );
};

export default AddSitePage;
