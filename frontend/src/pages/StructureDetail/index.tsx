import {
  DocumentAttachment,
  Inspection as InspectionIcon,
  Report,
  RulerAlt,
  TableOfContents,
  Tools,
  View,
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
  Tag,
} from '@carbon/react';
import { useRef, useState } from 'react';
import { useLocation, useParams } from 'react-router-dom';

import PageTitle from '@/components/core/PageTitle';

import DetailsTab from './DetailsTab';
import DocumentsTab from './DocumentsTab';
import { groupOutstanding } from './format';
import InformationTab from './InformationTab';
import InspectionsTab from './InspectionsTab';
import MonitoringTab from './MonitoringTab';
import OutstandingSummary from './OutstandingSummary';
import RepairsTab from './RepairsTab';
import SpansAndPiersTab from './SpansAndPiersTab';
import TabCount from './TabCount';

import './structureDetail.scss';

import type { OutstandingSection } from './structureResponse';
import type { CarbonIconType } from '@carbon/icons-react';
import type { FC } from 'react';

import { useStructure } from '@/hooks/useStructureSearch';
import { apiErrorMessage } from '@/utils/apiError';

/**
 * What a link to this page carries. Structure Search sends the name, so the title can name the
 * structure before it loads; Site Detail sends its site, so the breadcrumb leads back to it.
 */
export type StructureLinkState = { structureName?: string; fromSiteId?: string };

/**
 * One structure — legacy's `showStructure.do` (`cbr-structure-page.local.md`), read-only.
 *
 * <p>So far an Information tab — legacy's header above its tabs — then the Details tab, comments
 * last on it as legacy has them, for a bridge the Spans & Piers tab, then Documents & Photos and
 * Inspections, Repairs and Monitoring — every tab legacy has. Icon tabs over a grey pane, each
 * section a white card. The page stays marked under construction until its editing is built.
 *
 * <p>Read-only throughout. Legacy has no view mode: the page is always a form, each field deciding
 * for itself whether the user may change it. Editing comes later, behind an explicit Edit, as on
 * Site Detail.
 *
 * <p>Incomplete data is shown as a checklist rather than legacy's red line: a notice
 * above the tabs counts what is missing, the Details tab carries a count badge, and the tab opens on
 * the list itself.
 */
/** The type/classes legacy shows Spans & Piers for: portable and timber bridges. */
const BRIDGE_TYPE_CLASSES = new Set(['PB', 'TB']);

/** The page's tabs, in order. Spans & Piers is there for a bridge only. */
type TabKey =
  | 'information'
  | 'details'
  | 'spans-and-piers'
  | 'documents'
  | 'inspections'
  | 'repairs'
  | 'monitoring';

/**
 * Each tab's label and icon. The tabs and their panels are both drawn from one list of keys: Carbon
 * numbers tabs by their place among its children, an empty `{bridge && …}` slot included, so a
 * hand-written list with a conditional tab put Documents & Photos at a place the page did not
 * expect.
 */
const TAB_LABELS: Record<TabKey, { label: string; icon: CarbonIconType }> = {
  'information': { label: 'Information', icon: TableOfContents },
  'details': { label: 'Details', icon: Report },
  'spans-and-piers': { label: 'Spans & Piers', icon: RulerAlt },
  'documents': { label: 'Documents & Photos', icon: DocumentAttachment },
  'inspections': { label: 'Inspections', icon: InspectionIcon },
  'repairs': { label: 'Repairs', icon: Tools },
  'monitoring': { label: 'Monitoring', icon: View },
};

/** The tabs that list outstanding items, and whose. */
const TAB_SECTIONS: Partial<Record<TabKey, OutstandingSection>> = {
  information: 'SITE',
  details: 'DETAILS',
  inspections: 'INSPECTIONS',
};

const StructureDetailPage: FC = () => {
  const { structureId } = useParams<{ structureId: string }>();
  const linkState = useLocation().state as StructureLinkState | null;
  const loaded = useStructure(structureId);
  const [selectedTab, setSelectedTab] = useState(0);
  /**
   * The tabs opened so far. A tab with data of its own loads it when first opened, and keeps it.
   * By name, not by place: where a tab sits depends on whether the structure is a bridge.
   */
  const [openedTabs, setOpenedTabs] = useState<ReadonlySet<TabKey>>(new Set(['information']));
  /** Add Inspection was pressed on the incomplete structure: what is outstanding turns red. */
  const [addRefused, setAddRefused] = useState(false);
  const summaryRef = useRef<HTMLDivElement>(null);

  const structure = loaded.data;
  const name = structure?.structureName || linkState?.structureName || structureId || '';
  const outstandingItems = structure?.outstanding ?? [];
  const outstandingTotal = outstandingItems.length;
  /** Each tab's items, grouped by the section they are in. */
  const outstandingIn = (section: OutstandingSection) =>
    groupOutstanding(outstandingItems, section);
  const countIn = (section: OutstandingSection) =>
    outstandingItems.filter((item) => item.section === section).length;
  // Legacy decides by the type/class, not by whether a bridge row exists (S:1404-1418).
  const bridge = BRIDGE_TYPE_CLASSES.has(structure?.typeClass.code ?? '');
  const tabs: TabKey[] = [
    'information',
    'details',
    ...(bridge ? (['spans-and-piers'] as const) : []),
    'documents',
    'inspections',
    'repairs',
    'monitoring',
  ];
  const tabsWithOutstanding = (['SITE', 'DETAILS', 'INSPECTIONS'] as const).filter(
    (section) => countIn(section) > 0,
  ).length;
  const refused = addRefused && outstandingTotal > 0;
  const tone = refused ? 'error' : 'neutral';
  const notFound = (loaded.error as { status?: number } | null)?.status === 404;

  return (
    <Grid fullWidth className="default-grid structure-detail">
      <PageTitle
        title={`Structure ${name}`.trim()}
        breadCrumbs={
          // Back the way the user came: to the site from Display Structures, otherwise to the
          // search. State does not survive a new tab or a pasted link, which fall back to search.
          linkState?.fromSiteId
            ? [
                { name: 'Inventory', path: '/inventory' },
                { name: 'Site Search', path: '/inventory/site-search' },
                {
                  name: `Site ${linkState.fromSiteId}`,
                  path: `/inventory/site/${encodeURIComponent(linkState.fromSiteId)}`,
                },
              ]
            : [
                { name: 'Inventory', path: '/inventory' },
                { name: 'Structure Search', path: '/inventory/structure-search' },
              ]
        }
      >
        {/* Legacy never shows this. An archived structure looks like any other there, which
            matters now that Structure Search can include archived structures and archive them. */}
        {structure && !structure.active && (
          <Tag type="gray" size="sm" data-testid="structure-archived">
            Archived
          </Tag>
        )}
      </PageTitle>

      {loaded.isError && (
        <Column sm={4} md={8} lg={16}>
          <InlineNotification
            kind={notFound ? 'warning' : 'error'}
            lowContrast
            hideCloseButton
            title={notFound ? 'Structure not found' : 'This structure could not be loaded'}
            subtitle={
              notFound
                ? `No structure exists for id ${structureId}. It may have been deleted.`
                : apiErrorMessage(loaded.error, 'Try again, or go back to Structure Search.')
            }
            data-testid="structure-detail-error"
          />
        </Column>
      )}

      {loaded.isPending && (
        <Column sm={4} md={8} lg={16}>
          {/* The test id on a wrapper: Carbon repeats a paragraph skeleton's props on every line. */}
          <div data-testid="structure-detail-loading">
            <SkeletonText paragraph lineCount={10} />
          </div>
        </Column>
      )}

      {structure && (
        <>
          {outstandingTotal > 0 && (
            <Column sm={4} md={8} lg={16}>
              <div ref={summaryRef}>
                <OutstandingSummary
                  total={outstandingTotal}
                  tabs={tabsWithOutstanding}
                  refused={refused}
                />
              </div>
            </Column>
          )}

          <Column sm={4} md={8} lg={16}>
            {/* A wrapper the grey pane can be styled from: <Tabs> renders no element of its own,
                so its tab list and panels are this div's direct children. */}
            <div className="structure-detail__tabs">
              <Tabs
                selectedIndex={selectedTab}
                onChange={({ selectedIndex }) => {
                  setSelectedTab(selectedIndex);
                  setOpenedTabs((opened) => new Set(opened).add(tabs[selectedIndex]));
                }}
              >
                <TabList aria-label="Structure sections" contained>
                  {tabs.map((key) => {
                    const { label, icon } = TAB_LABELS[key];
                    return (
                      <Tab key={key} renderIcon={icon} data-testid={`structure-tab-${key}`}>
                        {TAB_SECTIONS[key] ? (
                          <span className="structure-detail__tab-label">
                            {label}
                            <TabCount
                              count={countIn(TAB_SECTIONS[key])}
                              section={label}
                              tone={tone}
                              blocking="adding an inspection"
                            />
                          </span>
                        ) : (
                          label
                        )}
                      </Tab>
                    );
                  })}
                </TabList>
                <TabPanels>
                  {tabs.map((key) => (
                    <TabPanel key={key}>
                      {key === 'information' && (
                        <InformationTab
                          structure={structure}
                          outstanding={outstandingIn('SITE')}
                          outstandingTone={tone}
                        />
                      )}
                      {key === 'details' && (
                        <DetailsTab
                          structure={structure}
                          outstanding={outstandingIn('DETAILS')}
                          outstandingTone={tone}
                        />
                      )}
                      {key === 'spans-and-piers' && (
                        <SpansAndPiersTab
                          structureId={structure.id}
                          hasBridgeRecord={structure.bridge !== null}
                          opened={openedTabs.has('spans-and-piers')}
                        />
                      )}
                      {key === 'inspections' && (
                        <InspectionsTab
                          structureId={structure.id}
                          opened={openedTabs.has('inspections')}
                          complete={outstandingTotal === 0}
                          outstanding={outstandingIn('INSPECTIONS')}
                          outstandingTone={tone}
                          onAddRefused={() => {
                            setAddRefused(true);
                            // The banner is above the tabs, likely scrolled past by now.
                            summaryRef.current?.scrollIntoView?.({
                              behavior: 'smooth',
                              block: 'center',
                            });
                          }}
                        />
                      )}
                      {key === 'repairs' && (
                        <RepairsTab structureId={structure.id} opened={openedTabs.has('repairs')} />
                      )}
                      {key === 'monitoring' && (
                        <MonitoringTab
                          structureId={structure.id}
                          opened={openedTabs.has('monitoring')}
                        />
                      )}
                      {key === 'documents' && (
                        <DocumentsTab
                          structureId={structure.id}
                          opened={openedTabs.has('documents')}
                          yearBuilt={structure.details.yearBuilt}
                        />
                      )}
                    </TabPanel>
                  ))}
                </TabPanels>
              </Tabs>
            </div>
          </Column>
        </>
      )}
    </Grid>
  );
};

export default StructureDetailPage;
