import { Column, Grid, InlineNotification } from '@carbon/react';
import { useParams } from 'react-router-dom';

import PageTitle from '@/components/core/PageTitle';

import type { FC } from 'react';

/**
 * Add Structure — a placeholder.
 *
 * <p>Legacy equivalent: Site Detail's Add Structure button, which opens a blank structure form for
 * that site. Nothing behind it is built yet, so the page gives the button somewhere to land and
 * says so. The site is in the path because a structure is always added to one.
 */
const AddStructurePage: FC = () => {
  const { siteId = '' } = useParams<{ siteId: string }>();

  return (
    <Grid fullWidth className="default-grid">
      <PageTitle
        title="Add Structure"
        subtitle={`For site ${siteId}.`}
        experimental
        breadCrumbs={[
          { name: 'Inventory', path: '/inventory' },
          { name: `Site ${siteId}`, path: `/inventory/site/${siteId}` },
        ]}
      />

      <Column sm={4} md={8} lg={16}>
        <InlineNotification
          kind="info"
          lowContrast
          hideCloseButton
          title="This page has not been built yet"
          subtitle="Adding a structure to a site will be done here."
          data-testid="add-structure-placeholder"
        />
      </Column>
    </Grid>
  );
};

export default AddStructurePage;
