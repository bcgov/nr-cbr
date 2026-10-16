import {
  Inspection as InspectionIcon,
  Location,
  Report,
  Save,
  WaveDirection,
} from '@carbon/icons-react';
import {
  Button,
  Column,
  Grid,
  InlineNotification,
  Tab,
  TabList,
  TabPanel,
  TabPanels,
  Tabs,
} from '@carbon/react';
import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';

import PageTitle from '@/components/core/PageTitle';
import Card from '@/pages/StructureDetail/Card';
import OutstandingPanel from '@/pages/StructureDetail/OutstandingPanel';
import TabCount from '@/pages/StructureDetail/TabCount';

import ConditionSection from './ConditionSection';
import { countByTab, errorGroups, outstandingOf, type TabKey } from './errorSummary';
import InformationSection from './InformationSection';
import { HazardFields, InspectionFields } from './InspectionFields';
import {
  EMPTY_HAZARD_VALUES,
  EMPTY_INSPECTION_VALUES,
  answerErrorsOf,
  hazardErrorsOf,
  hazardWarningsOf,
  inspectionErrorsOf,
  itemKey,
  toCreateRequest,
  type Answer,
  type Answers,
  type HazardValues,
  type InspectionValues,
} from './inspectionRules';

import type { CarbonIconType } from '@carbon/icons-react';
import type { FC } from 'react';

import { WORK_SAVED } from '@/context/unsavedChanges/workSaved';
import { useInspectionReportStatusCodes, useInspectionTypeCodes } from '@/hooks/useConfiguration';
import { useFieldErrors } from '@/hooks/useFieldErrors';
import { useCreateInspection, useInspectionForm } from '@/hooks/useInspectionSearch';
import { useStructure } from '@/hooks/useStructureSearch';
import { useUnsavedChangesPrompt } from '@/hooks/useUnsavedChangesPrompt';
import { apiErrorMessage } from '@/utils/apiError';

import '@/pages/StructureDetail/structureDetail.scss';
import './newInspection.scss';

const TABS: { key: TabKey; label: string; icon: CarbonIconType }[] = [
  { key: 'inspection', label: 'Inspection', icon: InspectionIcon },
  { key: 'condition', label: 'Condition', icon: Report },
  { key: 'hazards', label: 'Hydraulic Hazards', icon: WaveDirection },
];

/** Structure #'s message: none chosen, or one that is incomplete and so cannot be inspected. */
const structureErrorOf = (
  structureId: string | null,
  incomplete: boolean,
  structureName: string | null | undefined,
): string | undefined => {
  if (!structureId) return 'Structure # is required.';
  if (!incomplete) return undefined;
  return `Structure ${structureName ?? ''} is incomplete. Complete it on its structure page before it can be inspected.`;
};

/** Whether the Inspection tab differs from how it opened — its type as the link set it. */
const hasChanged = (inspection: InspectionValues, fromType: string | null): boolean =>
  inspection.typeCode !== (fromType ?? '') ||
  Object.entries(inspection).some(
    ([field, value]) =>
      field !== 'typeCode' && value !== EMPTY_INSPECTION_VALUES[field as keyof InspectionValues],
  );

/** Whether any question has an answer or a comment. */
const hasAnswers = (answers: Answers): boolean =>
  Object.values(answers).some((entry) => entry.answer.trim() || entry.comment.trim());

/**
 * Add Inspection — legacy's blank inspection form, in the structure page's tabs: Inspection (the
 * structure and its site first, then the inspection's own fields), Condition (the questions) and
 * Hydraulic Hazards. Saved In Progress or Submitted; Documents & Photos, Repairs, Monitoring and the
 * review come on the inspection's own page, which Save opens.
 *
 * <p>Opened from a structure's Add Routine / Add Unplanned Inspection button
 * (`?structureId=&type=`), the structure and type are set and shown; from the side menu, the
 * structure is found and the type chosen on the Inspection tab.
 *
 * <p>Each error shows beside its field once the field is left, and all of them on Save; a tab with
 * errors shows how many, so none is missed behind another tab.
 */
const NewInspectionPage: FC = () => {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const fromStructure = params.get('structureId');
  const fromType = params.get('type');
  const types = useInspectionTypeCodes();
  const statuses = useInspectionReportStatusCodes();
  const created = useCreateInspection();

  const [structureId, setStructureId] = useState<string | null>(fromStructure);
  const [inspection, setInspection] = useState<InspectionValues>({
    ...EMPTY_INSPECTION_VALUES,
    typeCode: fromType ?? '',
  });
  const [hazards, setHazards] = useState<HazardValues>(EMPTY_HAZARD_VALUES);
  const [answers, setAnswers] = useState<Answers>({});
  const [selectedTab, setSelectedTab] = useState(0);
  const [saved, setSaved] = useState(false);
  /** A Save has been refused for what is outstanding: the banner, counts and lists turn red. */
  const [refused, setRefused] = useState(false);

  const structure = useStructure(structureId ?? undefined);
  const form = useInspectionForm(structureId);
  const subsections = form.data?.subsections ?? [];

  const incomplete = (structure.data?.outstanding.length ?? 0) > 0;
  const errors: Record<string, string | undefined> = {
    structureId: structureErrorOf(structureId, incomplete, structure.data?.structureName),
    ...inspectionErrorsOf(inspection),
    ...hazardErrorsOf(hazards),
    ...answerErrorsOf(answers, subsections),
  };
  const fields = useFieldErrors<string>(errors, created.fieldErrors);
  /** Beside each field: once it is left, then live; all of them once Save is pressed. */
  const shown = fields.shown;

  /**
   * Everything still outstanding, from the first render — nr-frep's pattern: the banner, the tab
   * counts and each tab's list are the map of what is left, shown before any field is touched. The
   * server's refusals join them.
   */
  const outstanding = outstandingOf(errors, created.fieldErrors);
  const outstandingTotal = Object.keys(outstanding).length;
  const counts = countByTab(outstanding);
  const tabsWithOutstanding = Object.values(counts).filter((count) => count > 0).length;
  const tone = refused ? 'error' : 'neutral';

  const section = <V,>(set: (update: (current: V) => V) => void) => ({
    change: <K extends keyof V & string>(field: K, value: V[K]) => {
      set((current) => ({ ...current, [field]: value }));
      fields.change(field);
    },
    leave: (field: keyof V & string) => fields.leave(field),
  });

  const answer = (entry: Answer) => {
    const key = itemKey(entry);
    setAnswers((current) => ({ ...current, [key]: entry }));
    fields.change(key);
  };

  const isDirty =
    structureId !== fromStructure ||
    hasChanged(inspection, fromType) ||
    hazards !== EMPTY_HAZARD_VALUES ||
    hasAnswers(answers);
  useUnsavedChangesPrompt(isDirty && !saved);

  const save = () => {
    if (!fields.submit() || !structureId || incomplete) {
      setRefused(true);
      return;
    }
    created.mutate(toCreateRequest(structureId, inspection, hazards, answers), {
      onError: () => setRefused(true),
      onSuccess: (stored) => {
        setSaved(true);
        void navigate(`/inspection/${stored.id}`, { state: WORK_SAVED });
      },
    });
  };

  const cancel = () =>
    void navigate(
      fromStructure ? `/inventory/structure/${fromStructure}` : '/inspection/inspection-search',
    );

  return (
    <Grid fullWidth className="default-grid structure-detail new-inspection">
      <PageTitle
        title="Add Inspection"
        breadCrumbs={
          fromStructure
            ? [
                { name: 'Inventory', path: '/inventory' },
                {
                  name: `Structure ${structure.data?.structureName ?? ''}`.trim(),
                  path: `/inventory/structure/${fromStructure}`,
                },
              ]
            : [{ name: 'Inspection', path: '/inspection' }]
        }
      >
        <div className="new-inspection__header-actions">
          <Button kind="secondary" size="md" data-testid="inspection-cancel" onClick={cancel}>
            Cancel
          </Button>
          <Button
            kind="primary"
            size="md"
            renderIcon={Save}
            disabled={created.isPending}
            data-testid="inspection-save"
            onClick={save}
          >
            {created.isPending ? 'Saving…' : 'Save'}
          </Button>
        </div>
      </PageTitle>

      {/* nr-frep's summary above the tabs: how much is outstanding and across how many tabs —
          informational until a Save is refused, then red. Each message itself sits beside its
          field and in its tab's list. */}
      {outstandingTotal > 0 && (
        <Column sm={4} md={8} lg={16}>
          {refused ? (
            <InlineNotification
              className="structure-detail__outstanding-summary"
              kind="error"
              lowContrast
              hideCloseButton
              title="Inspection not saved"
              subtitle="All outstanding items must be complete before it can be saved. The tab counts show what is still outstanding."
              data-testid="inspection-outstanding-summary"
            />
          ) : (
            <InlineNotification
              className="structure-detail__outstanding-summary"
              kind="info"
              lowContrast
              hideCloseButton
              title={`${outstandingTotal} ${
                outstandingTotal === 1 ? 'required item' : 'required items'
              } outstanding across ${
                tabsWithOutstanding === 1 ? '1 tab' : `${tabsWithOutstanding} tabs`
              }`}
              subtitle="The inspection can be saved once these are complete."
              data-testid="inspection-outstanding-summary"
            />
          )}
        </Column>
      )}

      {/* A refusal no field is at fault for — the server down, the session expired. */}
      {created.isError && Object.keys(created.fieldErrors).length === 0 && (
        <Column sm={4} md={8} lg={16}>
          <InlineNotification
            kind="error"
            lowContrast
            hideCloseButton
            title="This inspection could not be saved"
            subtitle={apiErrorMessage(
              created.error,
              'Try again, or contact support if this continues.',
            )}
            data-testid="inspection-save-error"
          />
        </Column>
      )}

      <Column sm={4} md={8} lg={16}>
        <div className="structure-detail__tabs">
          <Tabs
            selectedIndex={selectedTab}
            onChange={({ selectedIndex }) => setSelectedTab(selectedIndex)}
          >
            <TabList aria-label="Inspection sections" contained>
              {TABS.map(({ key, label, icon }) => (
                <Tab key={key} renderIcon={icon} data-testid={`inspection-tab-${key}`}>
                  <span className="structure-detail__tab-label">
                    {label}
                    <TabCount count={counts[key]} section={label} tone={tone} />
                  </span>
                </Tab>
              ))}
            </TabList>
            <TabPanels>
              <TabPanel>
                <div className="structure-detail__tab-panel" data-testid="inspection-inspection">
                  <OutstandingPanel
                    groups={errorGroups(outstanding, 'inspection', form.data)}
                    tone={tone}
                    testId="inspection-inspection-errors"
                  />
                  <Card
                    title="Structure and site"
                    icon={Location}
                    testId="inspection-section-structure"
                  >
                    <InformationSection
                      structureId={structureId}
                      fixed={Boolean(fromStructure)}
                      onPick={(id) => {
                        setStructureId(id);
                        setAnswers({});
                        fields.change('structureId');
                      }}
                      error={shown.structureId}
                    />
                  </Card>
                  <InspectionFields
                    values={inspection}
                    shown={shown}
                    types={types.data ?? []}
                    statuses={statuses.data ?? []}
                    typeFixed={Boolean(fromType)}
                    {...section<InspectionValues>(setInspection)}
                  />
                </div>
              </TabPanel>
              <TabPanel>
                <div className="structure-detail__tab-panel" data-testid="inspection-condition">
                  <OutstandingPanel
                    groups={errorGroups(outstanding, 'condition', form.data)}
                    tone={tone}
                    testId="inspection-condition-errors"
                  />
                  <ConditionSection
                    form={form.data}
                    loading={form.isLoading}
                    answers={answers}
                    onAnswer={answer}
                    shown={shown}
                    onLeave={fields.leave}
                  />
                </div>
              </TabPanel>
              <TabPanel>
                <div className="structure-detail__tab-panel" data-testid="inspection-hazards">
                  <OutstandingPanel
                    groups={errorGroups(outstanding, 'hazards', form.data)}
                    tone={tone}
                    testId="inspection-hazards-errors"
                  />
                  <HazardFields
                    values={hazards}
                    shown={shown}
                    warnings={hazardWarningsOf(hazards)}
                    {...section<HazardValues>(setHazards)}
                  />
                </div>
              </TabPanel>
            </TabPanels>
          </Tabs>
        </div>
      </Column>
    </Grid>
  );
};

export default NewInspectionPage;
