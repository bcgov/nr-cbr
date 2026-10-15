import { Calendar, Forum, Pipelines, Renew, Road, Save, Scales } from '@carbon/icons-react';
import {
  Button,
  Column,
  Grid,
  InlineNotification,
  Select,
  SelectItem,
  TextArea,
  TextInput,
} from '@carbon/react';
import { useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import FieldWithCounter from '@/components/core/FieldWithCounter';
import PageTitle from '@/components/core/PageTitle';
import BridgeFields from '@/pages/StructureDetail/BridgeFields';
import Card from '@/pages/StructureDetail/Card';
import CulvertFields from '@/pages/StructureDetail/CulvertFields';
import LoadRatingFields from '@/pages/StructureDetail/LoadRatingFields';
import ReplacementFields from '@/pages/StructureDetail/ReplacementFields';
import ScheduleFields from '@/pages/StructureDetail/ScheduleFields';
import SiteFields from '@/pages/StructureDetail/SiteFields';
import StructureFields from '@/pages/StructureDetail/StructureFields';
import { requiredLabel } from '@/utils/requiredLabel';

import SpansAndPiersSection from './SpansAndPiersSection';

import type { BridgePier, BridgeSpan } from '@/pages/StructureDetail/spansAndPiersResponse';
import type { StructureCreateRequest } from '@/pages/StructureDetail/structureResponse';
import type { ChangeEvent, FC } from 'react';

import { WORK_SAVED } from '@/context/unsavedChanges/workSaved';
import { useAuthorization } from '@/hooks/useAuthorization';
import { useLoadRatingCodes, useStructureTypeClassCodes } from '@/hooks/useConfiguration';
import { useFieldErrors } from '@/hooks/useFieldErrors';
import { useCreateStructure, useSitePreview } from '@/hooks/useStructureSearch';
import { useUnsavedChangesPrompt } from '@/hooks/useUnsavedChangesPrompt';
import {
  BRIDGE_TYPES,
  EMPTY_BRIDGE_VALUES,
  NEW_BRIDGE_REQUIRED,
  bridgeErrorsOf,
  bridgeWarningsOf,
  toBridgeRequest,
  type BridgeValues,
} from '@/pages/StructureDetail/bridgeRules';
import {
  EMPTY_CULVERT_VALUES,
  NEW_CULVERT_REQUIRED,
  culvertErrorsOf,
  culvertWarningsOf,
  toCulvertRequest,
  type CulvertValues,
} from '@/pages/StructureDetail/culvertRules';
import { prefixed, sectionOf } from '@/pages/StructureDetail/formFields';
import {
  NEW_LOAD_RATING_VALUES,
  designRatingOf,
  loadRatingErrorsOf,
  toLoadRatingRequest,
  type LoadRatingValues,
} from '@/pages/StructureDetail/loadRatingRules';
import {
  EMPTY_REPLACEMENT_VALUES,
  replacementErrorsOf,
  replacementWarningsOf,
  toReplacementRequest,
  type ReplacementValues,
} from '@/pages/StructureDetail/replacementRules';
import {
  newScheduleValues,
  scheduleErrorsOf,
  toScheduleRequest,
  type ScheduleValues,
} from '@/pages/StructureDetail/scheduleRules';
import {
  EMPTY_STRUCTURE_VALUES,
  structureErrorsOf,
  structureWarningsOf,
  toStructureRequest,
  type StructureValues,
} from '@/pages/StructureDetail/structureRules';
import { apiErrorMessage } from '@/utils/apiError';
import { byteLength, overLimitError } from '@/utils/textLimits';

import '@/pages/StructureDetail/structureDetail.scss';
import './addStructure.scss';

/** The four Type/Classes a structure can be added as, in legacy's order. */
const TYPE_CLASSES = ['PB', 'TB', 'CUL', 'WLC'];
const WOOD_LOG = 'WLC';
/** `CROSSING_STRUCTURE_NAME VARCHAR2(14)`. */
const NAME_MAX = 14;
/** `STRUCTURE_COMMENT VARCHAR2(2000)`. */
const COMMENT_MAX = 2000;

type Top = { structureName: string; siteId: string; typeClassCode: string; comment: string };

/**
 * Add Structure — legacy's blank structure page, reached from Site Detail's Add Structure button,
 * as one page in the Add Site pattern: Cancel and Save in the header, the structure page's cards
 * below, each with the fields and rules of its card on the structure page.
 *
 * <p>What a structure needs to be complete is required here, so none is born incomplete: Structure
 * #, Site #, Type/Class, Source, the bridge's or culvert's measurements and codes, at least one span
 * (and more spans than piers), and the next routine inspection. Estimated Load Restriction stays
 * optional, as on legacy's page: it is a P.Eng's, and a structure added without one lists it as
 * outstanding until a P.Eng sets it.
 *
 * <p>The starting values are legacy's: Load Posting Signs Unknown, Load Rating Review Required, the
 * next routine inspection a year from today, every three years. On Save the server adds legacy's
 * 0-tonne "NS - New Structure" rating, and the page opens the new structure.
 */
const AddStructurePage: FC = () => {
  const { siteId: fromSite = '' } = useParams<{ siteId: string }>();
  const navigate = useNavigate();
  const { isPeng } = useAuthorization();
  const typeClasses = useStructureTypeClassCodes();
  const loadRatingCodes = useLoadRatingCodes();
  const created = useCreateStructure();

  const [top, setTop] = useState<Top>({
    structureName: '',
    siteId: fromSite,
    typeClassCode: '',
    comment: '',
  });
  const [details, setDetails] = useState<StructureValues>(EMPTY_STRUCTURE_VALUES);
  const [bridge, setBridge] = useState<BridgeValues>(EMPTY_BRIDGE_VALUES);
  const [culvert, setCulvert] = useState<CulvertValues>(EMPTY_CULVERT_VALUES);
  const [spans, setSpans] = useState<BridgeSpan[]>([]);
  const [piers, setPiers] = useState<BridgePier[]>([]);
  const [loadRating, setLoadRating] = useState<LoadRatingValues>(NEW_LOAD_RATING_VALUES);
  const [replacement, setReplacement] = useState<ReplacementValues>(EMPTY_REPLACEMENT_VALUES);
  const [schedule, setSchedule] = useState<ScheduleValues>(newScheduleValues);
  const [initialSchedule] = useState(schedule);
  /** Set once stored, so the unsaved-changes guard lets the page go to the new structure. */
  const [saved, setSaved] = useState(false);

  const isBridge = BRIDGE_TYPES.includes(top.typeClassCode);
  const isCulvert = top.typeClassCode === 'CUL' || top.typeClassCode === WOOD_LOG;
  const woodLog = top.typeClassCode === WOOD_LOG;
  const bridgeValues = { ...bridge, typeClassCode: top.typeClassCode };
  const design = designRatingOf(loadRating, loadRatingCodes.data?.designVehicles ?? [], '', null);
  // The site the Site # names, with why a structure cannot stand on it, if it cannot.
  const { preview, loading: siteLoading } = useSitePreview(top.siteId, null);

  /** Every rule, keyed as the server keys its refusals: `structureName`, `bridge.deckWidth`, … */
  const errors: Record<string, string | undefined> = {
    structureName: nameError(top.structureName),
    siteId: top.siteId.trim() ? (preview?.problem ?? undefined) : 'Site # is required.',
    typeClassCode: top.typeClassCode ? undefined : 'Type/Class is required.',
    ...prefixed(structureErrorsOf(details, true), 'details'),
    ...(isBridge ? prefixed(bridgeErrorsOf(bridgeValues, NEW_BRIDGE_REQUIRED), 'bridge') : {}),
    ...(isBridge ? listErrors(spans.length, piers.length) : {}),
    ...(isCulvert
      ? prefixed(culvertErrorsOf(culvert, NEW_CULVERT_REQUIRED, woodLog), 'culvert')
      : {}),
    ...prefixed(loadRatingErrorsOf(loadRating, design), 'loadRating'),
    ...(isPeng ? prefixed(replacementErrorsOf(replacement, false), 'replacement') : {}),
    ...prefixed(scheduleErrorsOf(schedule, true, true), 'schedule'),
    comment: overLimitError(top.comment, COMMENT_MAX) || undefined,
  };
  const form = useFieldErrors<string>(errors, created.fieldErrors);
  const shown = form.shown;

  /** A section's `change` and `leave`, keyed `section.field` in the page's form. */
  const section = <V,>(name: string, set: (update: (current: V) => V) => void) => ({
    shown: sectionOf<V>(shown, name),
    change: <K extends keyof V & string>(field: K, value: V[K]) => {
      set((current) => ({ ...current, [field]: value }));
      form.change(`${name}.${field}`);
    },
    leave: (field: keyof V & string) => form.leave(`${name}.${field}`),
  });

  const changeTop = (field: keyof Top, value: string) => {
    setTop((current) => ({ ...current, [field]: value }));
    form.change(field);
  };

  const isDirty = useMemo(
    () =>
      top.structureName !== '' ||
      top.siteId !== fromSite ||
      top.typeClassCode !== '' ||
      top.comment !== '' ||
      details !== EMPTY_STRUCTURE_VALUES ||
      bridge !== EMPTY_BRIDGE_VALUES ||
      culvert !== EMPTY_CULVERT_VALUES ||
      spans.length > 0 ||
      piers.length > 0 ||
      loadRating !== NEW_LOAD_RATING_VALUES ||
      replacement !== EMPTY_REPLACEMENT_VALUES ||
      schedule !== initialSchedule,
    [
      top,
      fromSite,
      details,
      bridge,
      culvert,
      spans,
      piers,
      loadRating,
      replacement,
      schedule,
      initialSchedule,
    ],
  );
  useUnsavedChangesPrompt(isDirty && !saved);

  const save = () => {
    if (!form.submit()) return;
    const request: StructureCreateRequest = {
      structureName: top.structureName.trim(),
      siteId: top.siteId.trim(),
      typeClassCode: top.typeClassCode,
      details: toStructureRequest(details),
      bridge: isBridge ? toBridgeRequest(bridgeValues) : null,
      culvert: isCulvert ? toCulvertRequest(culvert) : null,
      spans: isBridge
        ? spans.map((span) => ({ number: span.number ?? 0, lengthMetres: span.lengthMetres ?? 0 }))
        : [],
      piers: isBridge
        ? piers.map((pier) => ({ number: pier.number ?? 0, typeCode: pier.type.code ?? '' }))
        : [],
      loadRating: toLoadRatingRequest(loadRating, design),
      replacement: isPeng ? toReplacementRequest(replacement) : null,
      schedule: toScheduleRequest(schedule),
      comment: top.comment,
    };
    created.mutate(request, {
      // Marked as saved on the way out, so the guard does not ask about work already stored.
      onSuccess: (stored) => {
        setSaved(true);
        void navigate(`/inventory/structure/${stored.id}`, { state: WORK_SAVED });
      },
    });
  };

  const typeOptions = TYPE_CLASSES.map((code) => ({
    code,
    description: typeClasses.data?.find((option) => option.code === code)?.description ?? code,
  }));
  const detailsSection = section<StructureValues>('details', setDetails);
  const bridgeSection = section<BridgeValues>('bridge', setBridge);
  const culvertSection = section<CulvertValues>('culvert', setCulvert);
  const loadRatingSection = section<LoadRatingValues>('loadRating', setLoadRating);
  const replacementSection = section<ReplacementValues>('replacement', setReplacement);
  const scheduleSection = section<ScheduleValues>('schedule', setSchedule);

  return (
    <Grid fullWidth className="default-grid structure-detail">
      <PageTitle
        title="Add Structure"
        subtitle={`A new structure for site ${fromSite}.`}
        breadCrumbs={[
          { name: 'Inventory', path: '/inventory' },
          { name: `Site ${fromSite}`, path: `/inventory/site/${encodeURIComponent(fromSite)}` },
        ]}
      >
        {/* Beside the heading, as Add Site's: the form is long, and these end the task. */}
        <div className="add-structure__header-actions">
          <Button
            kind="secondary"
            size="md"
            data-testid="add-structure-cancel"
            onClick={() => void navigate(`/inventory/site/${encodeURIComponent(fromSite)}`)}
          >
            Cancel
          </Button>
          <Button
            kind="primary"
            size="md"
            renderIcon={Save}
            disabled={created.isPending}
            data-testid="add-structure-save"
            onClick={save}
          >
            {created.isPending ? 'Saving…' : 'Save'}
          </Button>
        </div>
      </PageTitle>

      {/* A refusal no field is at fault for — the server down, the session expired. */}
      {created.isError && Object.keys(created.fieldErrors).length === 0 && (
        <Column sm={4} md={8} lg={16}>
          <InlineNotification
            kind="error"
            lowContrast
            hideCloseButton
            title="This structure could not be saved"
            subtitle={apiErrorMessage(
              created.error,
              'Try again, or contact support if this continues.',
            )}
            data-testid="add-structure-save-error"
          />
        </Column>
      )}

      <Column sm={4} md={8} lg={16}>
        <div
          className="structure-detail__tab-panel add-structure__sections"
          data-testid="add-structure-form"
        >
          {/* The first section, untitled: the page's own heading says what it is. */}
          <section className="structure-detail__card" data-testid="add-structure-structure">
            <div className="structure-detail__edit">
              <div className="structure-detail__fields">
                <TextInput
                  id="add-structure-name"
                  data-testid="add-structure-name"
                  labelText={requiredLabel('Structure #', true)}
                  maxLength={NAME_MAX}
                  value={top.structureName}
                  invalid={Boolean(shown.structureName)}
                  invalidText={shown.structureName}
                  onChange={(event: ChangeEvent<HTMLInputElement>) =>
                    changeTop('structureName', event.target.value)
                  }
                  onBlur={() => form.leave('structureName')}
                />
                <TextInput
                  id="add-structure-site"
                  data-testid="add-structure-site"
                  labelText={requiredLabel('Site #', true)}
                  maxLength={NAME_MAX}
                  helperText={siteLoading ? 'Looking up the site…' : undefined}
                  value={top.siteId}
                  invalid={Boolean(shown.siteId)}
                  invalidText={shown.siteId}
                  onChange={(event: ChangeEvent<HTMLInputElement>) =>
                    changeTop('siteId', event.target.value)
                  }
                  onBlur={() => form.leave('siteId')}
                />
                <Select
                  id="add-structure-type"
                  data-testid="add-structure-type"
                  labelText={requiredLabel('Type/Class', true)}
                  value={top.typeClassCode}
                  disabled={typeClasses.isLoading}
                  invalid={Boolean(shown.typeClassCode)}
                  invalidText={shown.typeClassCode}
                  onChange={(event) => changeTop('typeClassCode', event.target.value)}
                  onBlur={() => form.leave('typeClassCode')}
                >
                  <SelectItem value="" text="Choose a type" />
                  {typeOptions.map((option) => (
                    <SelectItem key={option.code} value={option.code} text={option.description} />
                  ))}
                </Select>
                <SiteFields site={preview?.site ?? null} />
              </div>
              <StructureFields
                values={details}
                warnings={structureWarningsOf(details)}
                sourceRequired
                {...detailsSection}
              />
            </div>
          </section>

          <hr className="add-structure__divider" />

          {isBridge && (
            <>
              <Card title="Bridge" icon={Road} testId="add-structure-bridge">
                <div className="structure-detail__edit">
                  <BridgeFields
                    values={bridgeValues}
                    warnings={bridgeWarningsOf(bridgeValues)}
                    stored={NEW_BRIDGE_REQUIRED}
                    {...bridgeSection}
                  />
                </div>
              </Card>
              <hr className="add-structure__divider" />
              <SpansAndPiersSection
                spans={spans}
                piers={piers}
                onSpans={(next) => {
                  setSpans(next);
                  form.change('spans');
                }}
                onPiers={(next) => {
                  setPiers(next);
                  form.change('spans');
                  form.change('piers');
                }}
                spansError={shown.spans}
                piersError={shown.piers}
              />
            </>
          )}

          {isBridge && <hr className="add-structure__divider" />}

          {isCulvert && (
            <Card title="Culvert" icon={Pipelines} testId="add-structure-culvert">
              <div className="structure-detail__edit">
                <CulvertFields
                  values={culvert}
                  warnings={culvertWarningsOf(culvert)}
                  stored={NEW_CULVERT_REQUIRED}
                  woodLog={woodLog}
                  {...culvertSection}
                />
              </div>
            </Card>
          )}
          {isCulvert && <hr className="add-structure__divider" />}

          <Card title="Load Rating Details" icon={Scales} testId="add-structure-load-rating">
            <div className="structure-detail__edit">
              <LoadRatingFields
                values={loadRating}
                design={design}
                hasRatings={false}
                {...loadRatingSection}
              />
            </div>
          </Card>

          <hr className="add-structure__divider" />

          <Card title="Replacement Details" icon={Renew} testId="add-structure-replacement">
            {isPeng ? (
              <div className="structure-detail__edit">
                <ReplacementFields
                  values={replacement}
                  warnings={replacementWarningsOf(replacement, Number(details.yearBuilt) || null)}
                  restrictionRequired={false}
                  {...replacementSection}
                />
              </div>
            ) : (
              <p className="structure-detail__empty" data-testid="add-structure-replacement-peng">
                Replacement Details are entered by a P.Eng, on the structure page once it is saved.
              </p>
            )}
          </Card>

          <hr className="add-structure__divider" />

          <Card title="Inspection Schedule" icon={Calendar} testId="add-structure-schedule">
            <div className="structure-detail__edit">
              <ScheduleFields values={schedule} routineRequired {...scheduleSection} />
            </div>
          </Card>

          <hr className="add-structure__divider" />

          <Card title="Comment" icon={Forum} testId="add-structure-comment">
            <FieldWithCounter used={byteLength(top.comment)} limit={COMMENT_MAX}>
              <TextArea
                id="add-structure-comment-text"
                data-testid="add-structure-comment-text"
                labelText="Comment"
                rows={3}
                value={top.comment}
                invalid={Boolean(shown.comment)}
                invalidText={shown.comment}
                onChange={(event) => changeTop('comment', event.target.value)}
                onBlur={() => form.leave('comment')}
              />
            </FieldWithCounter>
          </Card>
        </div>
      </Column>
    </Grid>
  );
};

/** Structure #: required, and no longer than its column. */
const nameError = (typed: string): string | undefined => {
  const name = typed.trim();
  if (!name) return 'Structure # is required.';
  return byteLength(name) > NAME_MAX
    ? `Structure # can be at most ${NAME_MAX} characters.`
    : undefined;
};

/** A bridge's spans and piers as lists: at least one span, and more spans than piers. */
const listErrors = (spanCount: number, pierCount: number): Record<string, string | undefined> => {
  if (spanCount === 0) return { spans: 'At least one span is required.' };
  return spanCount <= pierCount
    ? { spans: 'Number of spans must be greater than number of piers.' }
    : {};
};

export default AddStructurePage;
