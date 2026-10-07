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
import { useLocation, useParams } from 'react-router-dom';

import PageTitle from '@/components/core/PageTitle';

import CommentsTab from './CommentsTab';
import DetailsTab from './DetailsTab';
import { groupOutstanding } from './format';
import OutstandingSummary from './OutstandingSummary';
import StructureHeader from './StructureHeader';
import TabCount from './TabCount';

import './structureDetail.scss';

import type { FC } from 'react';

import { useStructure } from '@/hooks/useStructureSearch';
import { apiErrorMessage } from '@/utils/apiError';
import { siteStatusLabel, siteStatusTagType } from '@/utils/siteStatus';

/**
 * What a link to this page carries. Structure Search sends the name, so the title can name the
 * structure before it loads; Site Detail sends its site, so the breadcrumb leads back to it.
 */
export type StructureLinkState = { structureName?: string; fromSiteId?: string };

/**
 * One structure — legacy's `showStructure.do` (`cbr-structure-page.local.md`), read-only.
 *
 * <p>So far the header, the Details tab and a Comments tab — legacy's comments fieldset, moved out
 * of Details. Legacy's other tabs — Spans & Piers, Documents & Photos, Inspections, Repairs,
 * Monitoring — follow, each into this same tab strip; the page stays marked under construction
 * until they do.
 *
 * <p>Read-only throughout. Legacy has no view mode: the page is always a form, each field deciding
 * for itself whether the user may change it. Editing comes later, behind an explicit Edit, as on
 * Site Detail.
 *
 * <p>Incomplete data follows nr-frep's checklist pattern rather than legacy's red line: a notice
 * above the tabs counts what is missing, the Details tab carries a count badge, and the tab opens on
 * the list itself.
 */
const StructureDetailPage: FC = () => {
  const { structureId } = useParams<{ structureId: string }>();
  const linkState = useLocation().state as StructureLinkState | null;
  const loaded = useStructure(structureId);

  const structure = loaded.data;
  const name = structure?.structureName || linkState?.structureName || structureId || '';
  const outstanding = groupOutstanding(structure?.outstanding ?? []);
  const outstandingTotal = structure?.outstanding.length ?? 0;
  const notFound = (loaded.error as { status?: number } | null)?.status === 404;

  return (
    <Grid fullWidth className="default-grid structure-detail">
      <PageTitle
        title={`Structure ${name}`.trim()}
        experimental
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
        {/* The site's status, as Site Search shows it: a pill coloured by the code. Nothing when
            the site has no status, rather than an empty pill. */}
        {(structure?.site?.siteStatus.code || structure?.site?.siteStatus.description) && (
          <Tag
            type={siteStatusTagType(structure.site.siteStatus.code)}
            size="sm"
            data-testid="structure-site-status"
          >
            {siteStatusLabel(structure.site.siteStatus.code, structure.site.siteStatus.description)}
          </Tag>
        )}
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
              <OutstandingSummary total={outstandingTotal} />
            </Column>
          )}

          <Column sm={4} md={8} lg={16}>
            <StructureHeader structure={structure} />
          </Column>

          <Column sm={4} md={8} lg={16}>
            <Tabs>
              <TabList aria-label="Structure sections" contained>
                <Tab data-testid="structure-tab-details">
                  <span className="structure-detail__tab-label">
                    Details
                    <TabCount count={outstandingTotal} section="Details" />
                  </span>
                </Tab>
                <Tab data-testid="structure-tab-comments">Comments</Tab>
              </TabList>
              <TabPanels>
                <TabPanel>
                  <DetailsTab structure={structure} outstanding={outstanding} />
                </TabPanel>
                <TabPanel>
                  <CommentsTab comments={structure.comments} />
                </TabPanel>
              </TabPanels>
            </Tabs>
          </Column>
        </>
      )}
    </Grid>
  );
};

export default StructureDetailPage;
