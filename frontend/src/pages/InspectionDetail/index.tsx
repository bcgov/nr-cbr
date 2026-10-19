import {
  DocumentAttachment,
  Inspection as InspectionIcon,
  Report,
  Tools,
  UserCertification,
  View,
  WaveDirection,
} from '@carbon/icons-react';
import {
  Column,
  Grid,
  InlineNotification,
  SkeletonText,
  Tab,
  TabList,
  TabPanel,
  TabPanels,
  Tabs,
} from '@carbon/react';
import { useState } from 'react';
import { useParams } from 'react-router-dom';

import PageTitle from '@/components/core/PageTitle';
import DocumentsTab from '@/pages/StructureDetail/DocumentsTab';
import MonitoringTab from '@/pages/StructureDetail/MonitoringTab';
import OutstandingPanel from '@/pages/StructureDetail/OutstandingPanel';
import OutstandingSummary from '@/pages/StructureDetail/OutstandingSummary';
import RepairsTab from '@/pages/StructureDetail/RepairsTab';
import TabCount from '@/pages/StructureDetail/TabCount';

import ConditionView from './ConditionView';
import HazardsTab from './HazardsTab';
import InspectionTab from './InspectionTab';
import { ReviewCards, StatusTag } from './InspectionViews';
import { countOf, outstandingOf, type OutstandingTab } from './outstanding';

import type { CarbonIconType } from '@carbon/icons-react';
import type { FC } from 'react';

import { useAuthorization } from '@/hooks/useAuthorization';
import { useInspection, useInspectionForm } from '@/hooks/useInspectionSearch';
import { useStructure } from '@/hooks/useStructureSearch';
import { apiErrorMessage } from '@/utils/apiError';
import { formatShortDate } from '@/utils/date';

import '@/pages/StructureDetail/structureDetail.scss';
import './inspectionDetail.scss';

/** The page's tabs, in order — legacy's, with Repairs & Monitoring split as the structure page's. */
type TabKey =
  'inspection' | 'condition' | 'hazards' | 'documents' | 'repairs' | 'monitoring' | 'review';

const TABS: { key: TabKey; label: string; icon: CarbonIconType }[] = [
  { key: 'inspection', label: 'Inspection', icon: InspectionIcon },
  { key: 'condition', label: 'Condition', icon: Report },
  { key: 'hazards', label: 'Hydraulic Hazards', icon: WaveDirection },
  { key: 'documents', label: 'Documents & Photos', icon: DocumentAttachment },
  { key: 'repairs', label: 'Repairs', icon: Tools },
  { key: 'monitoring', label: 'Monitoring', icon: View },
  { key: 'review', label: 'Review', icon: UserCertification },
];

/** The statuses after which an inspection is no longer changed from its page — the server's too. */
const LOCKING_STATUSES = new Set(['RVD', 'ACC', 'REJ']);
/** The statuses after which no document is added from its page — legacy's, the server's too. */
const DOCUMENT_LOCKING_STATUSES = new Set(['RVD', 'ACC']);

const isOutstandingTab = (key: TabKey): key is OutstandingTab =>
  key === 'inspection' || key === 'condition' || key === 'hazards';

/**
 * One inspection — legacy's `showInspection.do` in UPDATE mode (`cbr-inspection-page.local.md`),
 * read only for now, laid out as the structure page: icon tabs over a grey pane, each section a
 * white card.
 *
 * <p>Inspection (the structure and its site, the inspection, its comments), Condition (every
 * question asked, answered or not), Hydraulic Hazards, then as legacy: Documents & Photos (every
 * document on the structure, by inspection), Repairs and Monitoring (outstanding by default, with
 * Current Inspection Items for this one's), and Review (status and history, load rating, PEng
 * comments).
 *
 * <p>What it still lacks — legacy's submit rules and every unanswered question — shows as the
 * structure page shows an incomplete structure: a notice above the tabs, a count on each tab, and
 * a list at the top of it.
 */
const InspectionDetailPage: FC = () => {
  const { canWriteInspection } = useAuthorization();
  const { inspectionId = '' } = useParams<{ inspectionId: string }>();
  const loaded = useInspection(inspectionId);
  const inspection = loaded.data;
  const structureId = inspection?.structureId ?? undefined;
  const structure = useStructure(structureId);
  const form = useInspectionForm(structureId ?? null);
  const [selectedTab, setSelectedTab] = useState(0);
  /** The tabs opened so far: a tab with data of its own loads it when first opened, and keeps it. */
  const [openedTabs, setOpenedTabs] = useState<ReadonlySet<TabKey>>(new Set(['inspection']));

  const date = inspection ? formatShortDate(inspection.inspectionDate) || 'No date yet' : '';
  const title = inspection
    ? `Inspection ${date} — ${inspection.structureName ?? ''}`.replace(/ — $/, '')
    : `Inspection ${inspectionId}`;
  const notFound = (loaded.error as { status?: number } | null)?.status === 404;
  /** Reviewed, Accepted or Rejected: its repairs and monitoring items are no longer changed here. */
  const locked = LOCKING_STATUSES.has(inspection?.status.code ?? '');
  /** Reviewed or Accepted: no document added any more — legacy disables its Add then, not on Rejected. */
  const documentsLocked = DOCUMENT_LOCKING_STATUSES.has(inspection?.status.code ?? '');
  /** Legacy's page: Level 0 and up edit it, until it is Reviewed or Accepted. */
  const canEditInspection = canWriteInspection && !documentsLocked;
  /** What it still lacks, by tab — legacy's submit rules and every unanswered question. */
  const outstanding = inspection ? outstandingOf(inspection, form.data) : null;
  const counts = {
    inspection: countOf(outstanding?.inspection ?? []),
    condition: countOf(outstanding?.condition ?? []),
    hazards: countOf(outstanding?.hazards ?? []),
  };
  const outstandingTotal = counts.inspection + counts.condition + counts.hazards;
  const tabsWithOutstanding = Object.values(counts).filter((count) => count > 0).length;

  return (
    <Grid fullWidth className="default-grid structure-detail inspection-detail">
      <PageTitle
        title={title}
        breadCrumbs={[
          { name: 'Inspection', path: '/inspection' },
          { name: 'Inspection Search', path: '/inspection/inspection-search' },
        ]}
      >
        {inspection && <StatusTag status={inspection.status} testId="inspection-status" />}
      </PageTitle>

      {loaded.isError && (
        <Column sm={4} md={8} lg={16}>
          <InlineNotification
            kind={notFound ? 'warning' : 'error'}
            lowContrast
            hideCloseButton
            title={notFound ? 'Inspection not found' : 'This inspection could not be loaded'}
            subtitle={
              notFound
                ? `No inspection exists for id ${inspectionId}. It may have been deleted.`
                : apiErrorMessage(loaded.error, 'Try again, or go back to Inspection Search.')
            }
            data-testid="inspection-detail-error"
          />
        </Column>
      )}

      {loaded.isPending && (
        <Column sm={4} md={8} lg={16}>
          <div data-testid="inspection-detail-loading">
            <SkeletonText paragraph lineCount={10} />
          </div>
        </Column>
      )}

      {outstandingTotal > 0 && (
        <Column sm={4} md={8} lg={16}>
          {/* The structure page's summary above the tabs, nr-frep's pattern: how much, across how
              many tabs; each tab lists its own. Informational — nothing is refused here. */}
          <OutstandingSummary
            total={outstandingTotal}
            tabs={tabsWithOutstanding}
            subtitle="The tab counts show where; each tab lists what is missing in it."
            testId="inspection-outstanding-summary"
          />
        </Column>
      )}

      {inspection && (
        <Column sm={4} md={8} lg={16}>
          {/* A wrapper the grey pane is styled from, as the structure page's. */}
          <div className="structure-detail__tabs">
            <Tabs
              selectedIndex={selectedTab}
              onChange={({ selectedIndex }) => {
                setSelectedTab(selectedIndex);
                setOpenedTabs((opened) => new Set(opened).add(TABS[selectedIndex].key));
              }}
            >
              <TabList aria-label="Inspection sections" contained>
                {TABS.map(({ key, label, icon }) => (
                  <Tab key={key} renderIcon={icon} data-testid={`inspection-tab-${key}`}>
                    {isOutstandingTab(key) ? (
                      <span className="structure-detail__tab-label">
                        {label}
                        <TabCount count={counts[key]} section={label} />
                      </span>
                    ) : (
                      label
                    )}
                  </Tab>
                ))}
              </TabList>
              <TabPanels>
                <TabPanel>
                  <div className="structure-detail__tab-panel" data-testid="inspection-inspection">
                    <OutstandingPanel
                      groups={outstanding?.inspection ?? []}
                      testId="inspection-inspection-outstanding"
                    />
                    <InspectionTab inspection={inspection} structure={structure.data} />
                  </div>
                </TabPanel>
                <TabPanel>
                  <div className="structure-detail__tab-panel" data-testid="inspection-condition">
                    <OutstandingPanel
                      groups={outstanding?.condition ?? []}
                      testId="inspection-condition-outstanding"
                    />
                    <ConditionView
                      form={form.data}
                      loading={form.isLoading}
                      items={inspection.items}
                      inspectionId={inspection.id}
                      editable={canEditInspection}
                    />
                  </div>
                </TabPanel>
                <TabPanel>
                  <div className="structure-detail__tab-panel" data-testid="inspection-hazards">
                    <OutstandingPanel
                      groups={outstanding?.hazards ?? []}
                      testId="inspection-hazards-outstanding"
                    />
                    <HazardsTab inspection={inspection} />
                  </div>
                </TabPanel>
                <TabPanel>
                  {structureId && (
                    <DocumentsTab
                      structureId={structureId}
                      opened={openedTabs.has('documents')}
                      yearBuilt={structure.data?.details.yearBuilt ?? null}
                      inspection={{ id: inspection.id, date: inspection.inspectionDate }}
                      addLocked={documentsLocked}
                    />
                  )}
                </TabPanel>
                <TabPanel>
                  {structureId && (
                    <RepairsTab
                      structureId={structureId}
                      opened={openedTabs.has('repairs')}
                      inspectionId={inspection.id}
                      locked={locked}
                    />
                  )}
                </TabPanel>
                <TabPanel>
                  {structureId && (
                    <MonitoringTab
                      structureId={structureId}
                      opened={openedTabs.has('monitoring')}
                      inspectionId={inspection.id}
                      locked={locked}
                    />
                  )}
                </TabPanel>
                <TabPanel>
                  <div className="structure-detail__tab-panel" data-testid="inspection-review">
                    <ReviewCards inspection={inspection} />
                  </div>
                </TabPanel>
              </TabPanels>
            </Tabs>
          </div>
        </Column>
      )}
    </Grid>
  );
};

export default InspectionDetailPage;
