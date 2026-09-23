import { Add, Edit, ListChecked, Save } from '@carbon/icons-react';
import { Button, Column, Grid, InlineNotification, SkeletonText } from '@carbon/react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';

import PageTitle from '@/components/core/PageTitle';
import UnderConstructionTag from '@/components/core/Tags/UnderConstructionTag';
import RoadSearchModal from '@/components/RoadSearchModal';
import SiteForm, { FORM_ID, type SiteCodeTables } from '@/components/SiteForm';

import { toFormValues } from './siteResponse';

import type { FC } from 'react';

import { syncCoordinates } from '@/components/SiteForm/coordinateSync';
import { EMPTY_SITE, SITE_TYPE, type SiteFormValues } from '@/components/SiteForm/types';
import {
  crossFieldErrors,
  crossFieldWarnings,
  fieldErrors,
  fieldWarnings,
  savedSiteConflicts,
  type SiteErrors,
} from '@/components/SiteForm/validation';
import { useAuthorization } from '@/hooks/useAuthorization';
import {
  useBusinessAreas,
  useForestDistricts,
  useRecreationDistricts,
  useManagementAreas,
  useSiteReferenceDataState,
  useSiteStatusCodes,
  useSiteTypeCodes,
  useSpecialAccessCodes,
  useStructureInspectionStatusCodes,
} from '@/hooks/useConfiguration';
import { useSettledFields } from '@/hooks/useSettledFields';
import { useSite } from '@/hooks/useSiteSearch';
import { useUnsavedChangesPrompt } from '@/hooks/useUnsavedChangesPrompt';
import { apiErrorMessage } from '@/utils/apiError';
import { errorsForSettledFields } from '@/utils/validation';

import './siteDetail.scss';

/**
 * Fields no role may change on this screen, in legacy or here.
 *
 * <p>Each is disabled in <em>every</em> branch of `site.jsp` — including the Level 2 one — because
 * each is set somewhere other than this form: the site number is the key, the maintainer comes
 * from the client lookup, and Capital Road comes from the road record. Two of them still carry
 * `onchange` handlers in the JSP, so they read as fields that were editable once and were locked
 * later.
 *
 * <p><b>User Kilometres is the one worth questioning.</b> It is the posted distance on the sign,
 * which differs from the measured one and is exactly the sort of correction a district would want
 * to make. Reproduced as legacy has it, but flagged rather than assumed correct.
 */
const NEVER_EDITABLE = new Set<keyof SiteFormValues>([
  'siteId',
  'clientNumber',
  'clientLocationCode',
  'maintainerLabel',
  'userKm',
  'capitalRoad',
]);

/**
 * Site detail — what the Site # in the search results opens.
 *
 * <p>Legacy serves this and Add Site from one JSP: `showSite.do` with a site id renders the same
 * form with the number locked and the structure buttons shown. The two screens share a form here
 * for the same reason, and differ in what may be changed rather than in what is on them.
 *
 * <p><b>Read first, edit on request.</b> Legacy opens straight into an editable form for anyone
 * holding Level 1, which means the commonest thing a user does here — look something up — is done
 * on a page that is one stray keystroke from changing the record. This opens read-only and offers
 * Edit, and even then only the fields the user's role actually allows become editable; the rest
 * stay as they read.
 */
const SiteDetailPage: FC = () => {
  const { siteId } = useParams<{ siteId: string }>();
  const { canEdit, canDelete } = useAuthorization();

  const [site, setSite] = useState<SiteFormValues>({ ...EMPTY_SITE, siteId: siteId ?? '' });
  const [mode, setMode] = useState<'view' | 'edit'>('view');
  const loaded = useSite(siteId);

  /**
   * Copies the server's answer into the form once it lands, and again whenever it is refetched.
   *
   * <p>Held in state rather than read straight from the query because the form is editable: a
   * background refetch must not overwrite what the user is part-way through typing. Guarded on
   * view mode for exactly that reason — entering Edit takes a copy of what is on screen, and the
   * copy is theirs until they leave.
   */
  useEffect(() => {
    if (loaded.data && mode === 'view') {
      setSite(toFormValues(loaded.data));
    }
  }, [loaded.data, mode]);
  const [submitted, setSubmitted] = useState(false);
  const { settled, markSettled } = useSettledFields();

  const siteStatusCodes = useSiteStatusCodes();
  const siteTypeCodes = useSiteTypeCodes();
  const structureInspectionStatusCodes = useStructureInspectionStatusCodes();
  const specialAccessCodes = useSpecialAccessCodes();
  const forestDistricts = useForestDistricts();
  const businessAreas = useBusinessAreas();
  const managementAreas = useManagementAreas(site.orgUnitNo);
  // Only ever asked for once a project file is given — a recreation site's districts come from
  // the file, not from the whole province.
  const recreationDistricts = useRecreationDistricts(site.forestFileId);
  const referenceData = useSiteReferenceDataState();

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

  /**
   * Who may change what, from the role matrix `site.jsp` spells out branch by branch.
   *
   * <p>Level 2 edits the record; Level 1 edits only Site Details; everyone else reads. Inspection
   * Status is gated separately on `/modifyStructureInspectionStatus`, which is a Level 2
   * privilege of its own rather than part of the general edit surface.
   */
  const isEditable = useCallback(
    (field: keyof SiteFormValues) => {
      if (mode !== 'edit' || NEVER_EDITABLE.has(field)) {
        return false;
      }
      if (field === 'pointOfAccessDescription') {
        return canEdit;
      }
      return canDelete;
    },
    [mode, canEdit, canDelete],
  );

  /** Whether this user may change anything at all — what decides if Edit is offered. */
  const canEditSomething = canEdit || canDelete;

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

  /** The same three layers Add Site uses — see its own note on when each speaks. */
  /**
   * The two rules that only a stored site can break — see `savedSiteConflicts`. Add Site does not
   * call this at all, because a site being created owns no structures.
   *
   * <p>Zero until the site has loaded, which is the right answer while nothing is on screen:
   * treating "not yet known" as "has some" would flash a message about structures that may not
   * exist.
   */
  const stored = savedSiteConflicts(site, {
    activeStructureCount: loaded.data?.activeStructureCount ?? 0,
  });

  const settledErrors = fieldErrors(site, 'settled');
  const errors: SiteErrors = {
    ...fieldErrors(site, 'typing'),
    ...errorsForSettledFields(settledErrors, settled, (key) => String(site[key] ?? '')),
    ...(submitted ? settledErrors : {}),
    // The two-field rules, live rather than held to Save — see `crossFieldErrors`.
    ...(mode === 'edit' ? { ...crossFieldErrors(site), ...stored.errors } : {}),
  };

  // The amber tier. Never gated on Save — see the note on the same pair in Add Site — and shown
  // only while editing, because a read-only page is a record of what was decided rather than an
  // invitation to reconsider it.
  const warnings: SiteErrors =
    mode === 'edit'
      ? { ...fieldWarnings(site), ...crossFieldWarnings(site), ...stored.warnings }
      : {};

  // Only while editing: a read-only page holds nothing to lose, and prompting on the way out of
  // one the user merely looked at would be nonsense.
  useUnsavedChangesPrompt(mode === 'edit');

  const save = useCallback(() => {
    setSubmitted(true);
  }, []);

  const cancelEdit = useCallback(() => {
    setMode('view');
    setSubmitted(false);
  }, []);

  return (
    <Grid fullWidth className="default-grid">
      <PageTitle
        title={`Site ${siteId ?? ''}`.trim()}
        subtitle="Location, tenure and access for this crossing site."
        breadCrumbs={[
          { name: 'Inventory', path: '/inventory' },
          { name: 'Site Search', path: '/inventory/site-search' },
        ]}
      >
        <div className="site-detail__header-actions">
          {/* Shown only once the site is on screen, as legacy's `structuresButton` div is
              `display:none` until `actionType` is UPDATE. Both are disabled because neither screen
              exists yet — the structure inventory is the next thing to be ported. Disabled rather
              than hidden on purpose: withholding them would say this site has no structures, which
              is a different and possibly untrue statement. */}
          {mode === 'view' && loaded.data && (
            <>
              {canEdit && (
                <Button
                  kind="ghost"
                  size="md"
                  renderIcon={Add}
                  disabled
                  data-testid="site-detail-add-structure"
                  title="The structure screens have not been built yet"
                >
                  Add Structure
                </Button>
              )}
              <Button
                kind="ghost"
                size="md"
                renderIcon={ListChecked}
                disabled
                data-testid="site-detail-structures"
                title="The structure screens have not been built yet"
              >
                Display Structures
              </Button>
            </>
          )}
          {mode === 'view'
            ? canEditSomething && (
                <Button
                  kind="primary"
                  size="md"
                  renderIcon={Edit}
                  data-testid="site-detail-edit"
                  onClick={() => setMode('edit')}
                >
                  Edit
                </Button>
              )
            : [
                <Button
                  key="cancel"
                  kind="secondary"
                  size="md"
                  data-testid="site-detail-cancel"
                  onClick={cancelEdit}
                >
                  Cancel
                </Button>,
                <Button
                  key="save"
                  kind="primary"
                  size="md"
                  type="submit"
                  form={FORM_ID}
                  renderIcon={Save}
                  data-testid="site-detail-save"
                >
                  Save
                </Button>,
              ]}
        </div>
      </PageTitle>

      {referenceData.isError && (
        <Column sm={4} md={8} lg={16}>
          <InlineNotification
            kind="warning"
            lowContrast
            hideCloseButton
            title="Some lists could not be loaded"
            subtitle="Codes may show as their stored value rather than their description."
            data-testid="site-detail-reference-error"
          />
        </Column>
      )}

      {loaded.isError && (
        <Column sm={4} md={8} lg={16}>
          <InlineNotification
            kind="error"
            lowContrast
            hideCloseButton
            title="This site could not be loaded"
            subtitle={apiErrorMessage(loaded.error, 'Try again, or go back to Site Search.')}
            data-testid="site-detail-error"
          />
        </Column>
      )}

      {/* Saving is still unbuilt, so the page says so while it is — but only to someone who could
          otherwise have pressed Save. A reader has nothing to be warned about. */}
      {canEditSomething && (
        <Column sm={4} md={8} lg={16}>
          <div className="site-detail__notice">
            <UnderConstructionTag type="page" />
            <InlineNotification
              kind="info"
              lowContrast
              hideCloseButton
              title="Changes cannot be saved yet"
              subtitle="The site reads from the server, but the endpoint behind Save has not been built."
              data-testid="site-detail-placeholder"
            />
          </div>
        </Column>
      )}

      <Column sm={4} md={8} lg={16}>
        {loaded.isPending ? (
          // A skeleton, not an empty form. A form full of em dashes says the site has no values,
          // which is a different answer from "not read yet".
          <SkeletonText paragraph lineCount={10} data-testid="site-detail-loading" />
        ) : (
          <SiteForm
            values={site}
            errors={errors}
            warnings={warnings}
            codeTables={codeTables}
            codeTablesLoading={referenceData.isLoading}
            managementAreasLoading={managementAreas.isFetching}
            isEditable={isEditable}
            onChange={update}
            onSettle={markSettled}
            onSave={save}
            onFindRoad={() => setFindingRoad(true)}
          />
        )}
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

export default SiteDetailPage;
