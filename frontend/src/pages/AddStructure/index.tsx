import {
  Calendar,
  Forum,
  Inspection as InspectionIcon,
  Location,
  Pipelines,
  Renew,
  Report,
  Road,
  RulerAlt,
  Save,
  Scales,
  TableOfContents,
} from '@carbon/icons-react';
import {
  Button,
  Column,
  Grid,
  InlineNotification,
  Select,
  SelectItem,
  Tab,
  TabList,
  TabPanel,
  TabPanels,
  Tabs,
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
import OutstandingPanel from '@/pages/StructureDetail/OutstandingPanel';
import OutstandingSummary from '@/pages/StructureDetail/OutstandingSummary';
import ReplacementFields from '@/pages/StructureDetail/ReplacementFields';
import ScheduleFields from '@/pages/StructureDetail/ScheduleFields';
import SiteFields from '@/pages/StructureDetail/SiteFields';
import StructureFields from '@/pages/StructureDetail/StructureFields';
import TabCount from '@/pages/StructureDetail/TabCount';
import { requiredLabel } from '@/utils/requiredLabel';

import { countByTab, groupsOf, outstandingOf, type AddStructureTab } from './addStructureTabs';
import SpansAndPiersSection from './SpansAndPiersSection';

import type { BridgePier, BridgeSpan } from '@/pages/StructureDetail/spansAndPiersResponse';
import type { StructureCreateRequest } from '@/pages/StructureDetail/structureResponse';
import type { CarbonIconType } from '@carbon/icons-react';
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

/** Each tab's label and icon, as the structure page's. */
const TAB_LABELS: Record<AddStructureTab, { label: string; icon: CarbonIconType }> = {
  'information': { label: 'Information', icon: TableOfContents },
  'details': { label: 'Details', icon: Report },
  'spans-and-piers': { label: 'Spans & Piers', icon: RulerAlt },
  'inspections': { label: 'Inspections', icon: InspectionIcon },
};

type Top = { structureName: string; siteId: string; typeClassCode: string; comment: string };

/**
 * Add Structure — legacy's blank structure page, reached from Site Detail's Add Structure button,
 * laid out as Add Inspection: Cancel and Save in the header, then the structure page's tabs —
 * Information, Details, Spans & Piers (a bridge's), Inspections — each card with the fields and
 * rules of its card on the structure page.
 *
 * <p>What is outstanding shows from the start, nr-frep's pattern: a notice above the tabs, a count
 * on each tab and a list at its top — red once a Save is refused. Each message itself sits beside
 * its field once the field is left, and all of them on Save.
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
  const [selectedTab, setSelectedTab] = useState(0);
  /** A Save has been refused for what is outstanding: the banner, counts and lists turn red. */
  const [refused, setRefused] = useState(false);

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
  /** Beside each field: once it is left, then live; all of them once Save is pressed. */
  const shown = form.shown;

  /**
   * Everything still outstanding, from the first render — nr-frep's pattern, as Add Inspection's:
   * the banner, the tab counts and each tab's list are the map of what is left, shown before any
   * field is touched. The server's refusals join them.
   */
  const outstanding = outstandingOf(errors, created.fieldErrors);
  const outstandingTotal = Object.keys(outstanding).length;
  const counts = countByTab(outstanding);
  const tabsWithOutstanding = Object.values(counts).filter((count) => count > 0).length;
  const tone = refused ? 'error' : 'neutral';
  // The tabs are drawn from one list of keys: Carbon numbers tabs by their place, and Spans & Piers
  // is there for a bridge only.
  const tabs: AddStructureTab[] = [
    'information',
    'details',
    ...(isBridge ? (['spans-and-piers'] as const) : []),
    'inspections',
  ];

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
    if (!form.submit()) {
      setRefused(true);
      return;
    }
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
      onError: () => setRefused(true),
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
    <Grid fullWidth className="default-grid structure-detail add-structure">
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

      {/* nr-frep's summary above the tabs, as Add Inspection's: how much is outstanding and across
          how many tabs — informational until a Save is refused, then red. Each message itself sits
          beside its field and in its tab's list. */}
      {outstandingTotal > 0 && (
        <Column sm={4} md={8} lg={16}>
          <OutstandingSummary
            total={outstandingTotal}
            tabs={tabsWithOutstanding}
            refused={refused}
            subtitle="The structure can be saved once these are complete."
            refusedTitle="Structure not saved"
            refusedSubtitle="All outstanding items must be complete before it can be saved. The tab counts show what is still outstanding."
            testId="add-structure-outstanding-summary"
          />
        </Column>
      )}

      <Column sm={4} md={8} lg={16}>
        {/* A wrapper the grey pane is styled from, as the structure page's. */}
        <div className="structure-detail__tabs" data-testid="add-structure-form">
          <Tabs
            selectedIndex={Math.min(selectedTab, tabs.length - 1)}
            onChange={({ selectedIndex }) => setSelectedTab(selectedIndex)}
          >
            <TabList aria-label="Structure sections" contained>
              {tabs.map((key) => {
                const { label, icon } = TAB_LABELS[key];
                return (
                  <Tab key={key} renderIcon={icon} data-testid={`add-structure-tab-${key}`}>
                    <span className="structure-detail__tab-label">
                      {label}
                      <TabCount count={counts[key]} section={label} tone={tone} />
                    </span>
                  </Tab>
                );
              })}
            </TabList>
            <TabPanels>
              {tabs.map((key) => (
                <TabPanel key={key}>
                  <div
                    className="structure-detail__tab-panel"
                    data-testid={`add-structure-panel-${key}`}
                  >
                    <OutstandingPanel
                      groups={groupsOf(outstanding, key)}
                      tone={tone}
                      testId={`add-structure-${key}-outstanding`}
                    />
                    {key === 'information' && (
                      <Card
                        title="Structure and site"
                        icon={Location}
                        testId="add-structure-structure"
                      >
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
                            <SiteFields site={preview?.site ?? null} />
                          </div>
                        </div>
                      </Card>
                    )}
                    {key === 'details' && (
                      <>
                        <Card title="Structure" icon={Report} testId="add-structure-details">
                          <div className="structure-detail__edit">
                            <div className="structure-detail__fields">
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
                                  <SelectItem
                                    key={option.code}
                                    value={option.code}
                                    text={option.description}
                                  />
                                ))}
                              </Select>
                            </div>
                            <StructureFields
                              values={details}
                              warnings={structureWarningsOf(details)}
                              sourceRequired
                              {...detailsSection}
                            />
                          </div>
                        </Card>
                        {isBridge && (
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
                        )}
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
                        <Card
                          title="Load Rating Details"
                          icon={Scales}
                          testId="add-structure-load-rating"
                        >
                          <div className="structure-detail__edit">
                            <LoadRatingFields
                              values={loadRating}
                              design={design}
                              hasRatings={false}
                              {...loadRatingSection}
                            />
                          </div>
                        </Card>
                        <Card
                          title="Replacement Details"
                          icon={Renew}
                          testId="add-structure-replacement"
                        >
                          {isPeng ? (
                            <div className="structure-detail__edit">
                              <ReplacementFields
                                values={replacement}
                                warnings={replacementWarningsOf(
                                  replacement,
                                  Number(details.yearBuilt) || null,
                                )}
                                restrictionRequired={false}
                                {...replacementSection}
                              />
                            </div>
                          ) : (
                            <p
                              className="structure-detail__empty"
                              data-testid="add-structure-replacement-peng"
                            >
                              Replacement Details are entered by a P.Eng, on the structure page once
                              it is saved.
                            </p>
                          )}
                        </Card>
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
                      </>
                    )}
                    {key === 'spans-and-piers' && (
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
                    )}
                    {key === 'inspections' && (
                      <Card
                        title="Inspection Schedule"
                        icon={Calendar}
                        testId="add-structure-schedule"
                      >
                        <div className="structure-detail__edit">
                          <ScheduleFields values={schedule} routineRequired {...scheduleSection} />
                        </div>
                      </Card>
                    )}
                  </div>
                </TabPanel>
              ))}
            </TabPanels>
          </Tabs>
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
