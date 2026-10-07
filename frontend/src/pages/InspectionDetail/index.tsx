import { Column, Grid, InlineNotification } from '@carbon/react';
import { useParams } from 'react-router-dom';

import PageTitle from '@/components/core/PageTitle';

import type { FC } from 'react';

/**
 * One inspection — a placeholder.
 *
 * <p>Legacy equivalent: `showInspection.do?actionMapping=view&id={inspectionId}`, the inspection
 * form, reached from Inspection Search and from a structure's Inspections tab. Nothing behind it is
 * built yet, so the page gives those links somewhere to land and says so.
 */
const InspectionDetailPage: FC = () => {
  const { inspectionId = '' } = useParams<{ inspectionId: string }>();

  return (
    <Grid fullWidth className="default-grid">
      <PageTitle
        title={`Inspection ${inspectionId}`.trim()}
        experimental
        breadCrumbs={[
          { name: 'Inspection', path: '/inspection' },
          { name: 'Inspection Search', path: '/inspection/inspection-search' },
        ]}
      />

      <Column sm={4} md={8} lg={16}>
        <InlineNotification
          kind="info"
          lowContrast
          hideCloseButton
          title="This page is under construction"
          subtitle="The inspection's details, findings and review will be shown here."
          data-testid="inspection-detail-placeholder"
        />
      </Column>
    </Grid>
  );
};

export default InspectionDetailPage;
