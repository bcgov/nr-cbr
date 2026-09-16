import { Column, Grid, InlineNotification } from '@carbon/react';
import { useParams } from 'react-router-dom';

import PageTitle from '@/components/core/PageTitle';

import type { FC } from 'react';

/**
 * Site detail — the screen a site number in the search results leads to.
 *
 * <p><b>A placeholder.</b> It exists so the results table's links go somewhere that names what will
 * be there, rather than nowhere: a link to a route that does not exist renders the "page not found"
 * screen, which reads as a defect in the search rather than as work not yet done.
 *
 * <p>Legacy equivalent: `showSite.do` → `SiteAction` → the site screens, which carry the site's
 * location and tenure, its structures, and the inspections hanging off those
 * (cbr-overview.local.md §5).
 */
const SiteDetailPage: FC = () => {
  const { siteId } = useParams<{ siteId: string }>();

  return (
    <Grid fullWidth className="default-grid">
      <PageTitle
        title={`Site ${siteId ?? ''}`.trim()}
        subtitle="Location, tenure, structures and inspections for this crossing site."
        experimental
        breadCrumbs={[
          { name: 'Inventory', path: '/inventory' },
          { name: 'Site Search', path: '/inventory/site-search' },
        ]}
      />

      <Column sm={4} md={8} lg={16}>
        <InlineNotification
          kind="info"
          lowContrast
          hideCloseButton
          title="Not built yet"
          subtitle={
            'This screen will show the site’s location and tenure, the structures on it, and the ' +
            'inspections recorded against them.'
          }
          data-testid="site-detail-placeholder"
        />
      </Column>
    </Grid>
  );
};

export default SiteDetailPage;
