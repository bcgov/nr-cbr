import { Edit, Location } from '@carbon/icons-react';
import { Button, Tag, TextInput } from '@carbon/react';
import { useState } from 'react';

import ExternalLink from '@/components/core/ExternalLink';
import ReadOnlyField from '@/components/core/ReadOnlyField';
import { requiredLabel } from '@/utils/requiredLabel';

import Card from './Card';

import type {
  CodeValue,
  StructureDetailResponse,
  StructureInformationRequest,
  StructureSite,
} from './structureResponse';
import type { ChangeEvent, FC } from 'react';

import { useNotification } from '@/context/notification/useNotification';
import { useAuthorization } from '@/hooks/useAuthorization';
import { holdFocus, useFieldErrors } from '@/hooks/useFieldErrors';
import { useSitePreview, useUpdateStructureInformation } from '@/hooks/useStructureSearch';
import { apiErrorMessage, apiFieldErrors } from '@/utils/apiError';
import { siteStatusLabel, siteStatusTagType } from '@/utils/siteStatus';
import { byteLength } from '@/utils/textLimits';

type Props = { structure: StructureDetailResponse };

/** Legacy's site type for a recreation site, which shows a district and project of its own. */
const RECREATION = 'REC';
/** `CROSSING_STRUCTURE_NAME VARCHAR2(14 BYTE)`. */
const NAME_MAX = 14;
/** `CROSSING_SITE_ID VARCHAR2(14)`. */
const SITE_ID_MAX = 14;

/** A code as legacy's lists show it — "OK - Inspected" — or whichever half is stored. */
const codeAndDescription = (value: CodeValue | null | undefined): string =>
  [value?.code, value?.description].filter(Boolean).join(' - ');

/** A site's status as a pill coloured by its code, or nothing rather than an empty pill. */
const SiteStatusPill: FC<{ site: StructureSite | null; testId: string }> = ({ site, testId }) =>
  site?.siteStatus.code || site?.siteStatus.description ? (
    <Tag type={siteStatusTagType(site.siteStatus.code)} size="sm" data-testid={testId}>
      {siteStatusLabel(site.siteStatus.code, site.siteStatus.description)}
    </Tag>
  ) : null;

/** The site's own fields, as the tab shows them — for the structure's site, or one to move to. */
const SiteFields: FC<{ site: StructureSite | null }> = ({ site }) => {
  const recreation = site?.siteTypeCode === RECREATION;
  const projectFile = [site?.forestFileId, site?.roadSectionId].filter(Boolean).join('-');
  return (
    <>
      <ReadOnlyField
        label={recreation ? 'Recreation District' : 'Forest District'}
        value={site?.districtName}
      />
      {!recreation && <ReadOnlyField label="Management Area" value={site?.managementAreaName} />}
      <ReadOnlyField
        label={recreation ? 'Project Name' : 'Forest Service Road'}
        value={recreation ? site?.projectName : site?.forestServiceRoad}
      />
      <ReadOnlyField label="Project File ID#-Br." value={projectFile} />

      <ReadOnlyField label="Crossing Name" value={site?.crossingName} />
      <ReadOnlyField label="Kilometres" value={site?.kilometres} />
      <ReadOnlyField label="Designated Maintainer" value={site?.maintainerLabel} />
    </>
  );
};

/** What the tab shows when it is not being edited. */
const InformationView: FC<Props> = ({ structure }) => {
  const site = structure.site;
  return (
    <div className="structure-detail__fields" data-testid="structure-header">
      <ReadOnlyField label="Structure #" value={structure.structureName} />
      <ReadOnlyField
        label="Site #"
        value={
          // Its own tab: the structure stays where the user left it.
          site?.siteId ? (
            <ExternalLink to={`/inventory/site/${site.siteId}`}>{site.siteId}</ExternalLink>
          ) : null
        }
      />
      <ReadOnlyField
        label="Site Status"
        value={<SiteStatusPill site={site} testId="structure-site-status" />}
      />
      <ReadOnlyField label="Inspection Status" value={codeAndDescription(site?.inspectionStatus)} />
      <SiteFields site={site} />
    </div>
  );
};

type Values = { structureName: string; siteId: string };
type Errors = Partial<Record<keyof Values, string>>;

/**
 * The tab's two changeable fields as a form, in place of the view — legacy's header boxes.
 *
 * <p>As legacy, by role: Level 2 and up change Structure # and Site #; Level 1 Site # alone, the
 * name shown read-only. Type/Class is on the Details tab, and changed there with the bridge.
 *
 * <p>Every field of the view stays in its place; only the two are boxes. A new site number is
 * looked up as it is typed and the site fields show the site it names — its status, district, road,
 * crossing and kilometres — as legacy's header refreshed; a site the structure cannot move to (none
 * such, incomplete, Proposed) says why beside Site # at once, and Save refuses it.
 */
const InformationForm: FC<{ structure: StructureDetailResponse; onDone: () => void }> = ({
  structure,
  onDone,
}) => {
  // Level 2 and up — legacy's /level2Access on the Structure # box.
  const { canDelete: canRename } = useAuthorization();
  const update = useUpdateStructureInformation(structure.id);
  const { display } = useNotification();
  const currentSiteId = structure.site?.siteId ?? null;

  const [values, setValues] = useState<Values>({
    structureName: structure.structureName ?? '',
    siteId: currentSiteId ?? '',
  });
  const { preview, loading } = useSitePreview(values.siteId, currentSiteId);

  const validate = (): Errors => {
    const found: Errors = {};
    const name = values.structureName.trim();
    if (canRename) {
      if (!name) found.structureName = 'Structure # is required.';
      else if (byteLength(name) > NAME_MAX) {
        found.structureName = `Structure # can be at most ${NAME_MAX} characters.`;
      }
    }
    // As legacy: required once the structure has a site.
    if (!values.siteId.trim() && currentSiteId) found.siteId = 'Site # is required.';
    return found;
  };

  // Checked as the values change; each shown once its field is left, or at Save.
  const form = useFieldErrors<keyof Values>(validate(), update.fieldErrors);
  const shown: Errors = {
    // A site that cannot be moved to says so as soon as it is looked up.
    ...(preview?.problem ? { siteId: preview.problem } : {}),
    ...form.shown,
  };

  const change = (field: keyof Values, value: string) => {
    setValues((current) => ({ ...current, [field]: value }));
    form.change(field);
  };

  const save = () => {
    if (!form.submit() || preview?.problem) return;
    const request: StructureInformationRequest = {
      structureName: canRename ? values.structureName.trim() : (structure.structureName ?? ''),
      siteId: values.siteId.trim(),
    };
    update.mutate(request, {
      onSuccess: () => {
        display({ kind: 'success', title: 'Structure information saved', timeout: 4000 });
        onDone();
      },
      onError: (error) => {
        // Field refusals show beside their fields; anything else is a toast.
        if (Object.keys(apiFieldErrors(error)).length > 0) return;
        display({
          kind: 'error',
          title: 'The structure information was not saved',
          subtitle: apiErrorMessage(error, 'Try again, or contact support if this continues.'),
          timeout: 0,
        });
      },
    });
  };

  const moving = values.siteId.trim() !== '' && values.siteId.trim() !== (currentSiteId ?? '');
  // As legacy's header: once a new number is looked up, the site's fields are that site's — blank
  // when there is no such site; the structure's own while it is being looked up.
  const site = moving && preview ? preview.site : structure.site;

  return (
    <div className="structure-detail__edit" data-testid="information-form">
      {/* Every field in its place, as in the view; only Structure # and Site # are boxes. */}
      <div className="structure-detail__fields" data-testid="structure-header">
        {canRename ? (
          <TextInput
            id="information-structure-name"
            data-testid="information-structure-name"
            // As legacy's box: CROSSING_STRUCTURE_NAME is 14 bytes; the byte count is checked on Save.
            maxLength={NAME_MAX}
            labelText={requiredLabel('Structure #', true)}
            value={values.structureName}
            invalid={Boolean(shown.structureName)}
            invalidText={shown.structureName}
            onChange={(event: ChangeEvent<HTMLInputElement>) =>
              change('structureName', event.target.value)
            }
            onBlur={() => form.leave('structureName')}
          />
        ) : (
          // Level 1: the name as it is, as legacy's read-only box.
          <ReadOnlyField label="Structure #" value={structure.structureName} />
        )}
        <TextInput
          id="information-site-id"
          data-testid="information-site-id"
          // As legacy's box: CROSSING_SITE_ID is 14 characters.
          maxLength={SITE_ID_MAX}
          labelText={requiredLabel('Site #', Boolean(currentSiteId))}
          helperText={
            !moving
              ? undefined
              : loading
                ? 'Looking up the site…'
                : preview?.site
                  ? `Showing site ${preview.site.siteId}.`
                  : undefined
          }
          value={values.siteId}
          invalid={Boolean(shown.siteId)}
          invalidText={shown.siteId}
          onChange={(event: ChangeEvent<HTMLInputElement>) => change('siteId', event.target.value)}
          onBlur={() => form.leave('siteId')}
        />
        <ReadOnlyField
          label="Site Status"
          value={<SiteStatusPill site={site} testId="structure-site-status" />}
        />
        <ReadOnlyField
          label="Inspection Status"
          value={codeAndDescription(site?.inspectionStatus)}
        />
        <SiteFields site={site} />
      </div>

      {/* The form's actions: small, at the right, under the fields. */}
      <div className="structure-detail__edit-actions">
        <Button
          onMouseDown={holdFocus}
          kind="tertiary"
          size="sm"
          onClick={onDone}
          disabled={update.isPending}
        >
          Cancel
        </Button>
        <Button
          onMouseDown={holdFocus}
          kind="primary"
          size="sm"
          onClick={save}
          disabled={update.isPending}
          data-testid="information-save"
        >
          {update.isPending ? 'Saving…' : 'Save changes'}
        </Button>
      </div>
    </div>
  );
};

/**
 * The Information tab, first of the page's tabs: what the structure is and the site it stands on —
 * legacy's header above its tabs (`structure.jsp:4069-4314`), moved into a tab of its own, the one
 * the page opens on. Structure # is here as well as in the page title, and Site Status is a pill
 * coloured by its code, as Site Search shows it.
 *
 * <p>"Edit structure and site", for Level 1 and up (legacy's page Save), replaces the values in
 * place with Structure # and Site #, as the Inspection Schedule card edits.
 *
 * <p>Legacy's two maintainer boxes — client number and location code — and its maintainer name are
 * one field here, as on Site Detail. Inspection Status shows its code and description; legacy showed
 * the code alone.
 */
const InformationTab: FC<Props> = ({ structure }) => {
  const { canEdit } = useAuthorization();
  const [editing, setEditing] = useState(false);
  return (
    <Card
      title="Structure and site"
      icon={Location}
      testId="structure-information-tab"
      action={
        canEdit &&
        !editing && (
          <Button
            kind="tertiary"
            size="sm"
            renderIcon={Edit}
            data-testid="information-edit"
            onClick={() => setEditing(true)}
          >
            Edit structure and site
          </Button>
        )
      }
    >
      {editing ? (
        <InformationForm structure={structure} onDone={() => setEditing(false)} />
      ) : (
        <InformationView structure={structure} />
      )}
    </Card>
  );
};

export default InformationTab;
