import { Column, Grid, InlineNotification } from '@carbon/react';
import { useLocation, useParams } from 'react-router-dom';

import PageTitle from '@/components/core/PageTitle';

import type { FC } from 'react';

/** What the Structure Search link carries, so the title can name the structure without a fetch. */
export type StructureLinkState = { structureName?: string };

/**
 * Structure detail — a placeholder.
 *
 * <p>Legacy equivalent: `showStructure.do`, the seven-tab structure screen. Nothing behind it is
 * built yet, so the page exists to give Structure Search's links somewhere to land, and says so.
 *
 * <p>The title takes the structure's name from the link that opened it. A reload or a pasted URL
 * has no such state and falls back to the id, until there is an endpoint to read the structure from.
 */
const StructureDetailPage: FC = () => {
  const { structureId = '' } = useParams<{ structureId: string }>();
  const state = useLocation().state as StructureLinkState | null;
  const name = state?.structureName || structureId;

  return (
    <Grid fullWidth className="default-grid">
      <PageTitle
        title={`Structure ${name}`}
        experimental
        breadCrumbs={[
          { name: 'Inventory', path: '/inventory' },
          { name: 'Structure Search', path: '/inventory/structure-search' },
        ]}
      />

      <Column sm={4} md={8} lg={16}>
        <InlineNotification
          kind="info"
          lowContrast
          hideCloseButton
          title="This page has not been built yet"
          subtitle="The structure's details will appear here. Use Structure Search to find another structure."
          data-testid="structure-detail-placeholder"
        />
      </Column>
    </Grid>
  );
};

export default StructureDetailPage;
