import { Column, Grid, InlineNotification } from '@carbon/react';
import { useSearchParams } from 'react-router-dom';

import PageTitle from '@/components/core/PageTitle';

import type { FC } from 'react';

/** Legacy's two kinds of inspection a structure's Inspections tab starts. */
const KINDS: Record<string, string> = { ROUT: 'routine', UNP: 'unplanned' };

/**
 * A new inspection of a structure — a placeholder.
 *
 * <p>Legacy equivalent: `showInspection.do?actionMapping=view&structureId={id}&inspectionType=ROUT`
 * (or `UNP`), the blank inspection form the Inspections tab's Add Routine and Add Unplanned
 * Inspection open; nothing is saved until that form is. The form is not built yet, so the buttons
 * land here and the page says so.
 */
const NewInspectionPage: FC = () => {
  const [params] = useSearchParams();
  const structureId = params.get('structureId') ?? '';
  const kind = KINDS[params.get('type') ?? ''] ?? '';

  return (
    <Grid fullWidth className="default-grid">
      <PageTitle
        title={`New ${kind} inspection`.replace(/\s+/g, ' ')}
        experimental
        breadCrumbs={[
          { name: 'Inventory', path: '/inventory' },
          ...(structureId
            ? [{ name: 'Structure', path: `/inventory/structure/${structureId}` }]
            : []),
        ]}
      />

      <Column sm={4} md={8} lg={16}>
        <InlineNotification
          kind="info"
          lowContrast
          hideCloseButton
          title="This page is under construction"
          subtitle="The inspection form — its findings, load rating, repairs and monitoring — will be here."
          data-testid="new-inspection-placeholder"
        />
      </Column>
    </Grid>
  );
};

export default NewInspectionPage;
