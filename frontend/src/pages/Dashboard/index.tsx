import { Column, Grid, InlineNotification } from '@carbon/react';

import type { FC } from 'react';

import { APP_FULL_NAME, APP_NAME } from '@/constants/appName';
import { useAuthorization } from '@/hooks/useAuthorization';

import './dashboard.scss';

/**
 * Post-login home.
 *
 * <p>Ported from nr-frep's Dashboard, which renders a grid of ClickableTiles — one per screen. CBR
 * has no screens yet, so this deliberately shows the shell and says so rather than presenting tiles
 * that navigate nowhere. Restore the tile grid from nr-frep's version as the screens land; the
 * inventory to build against is in cbr-overview.local.md §5 (site, structure with 7 tabs,
 * inspection, documents, 12 reports, 3 admin screens, search).
 */
const DashboardPage: FC = () => {
  const { isSysAdmin, isPeng, canRead, canWriteInspection, canEdit, canDelete } =
    useAuthorization();

  return (
    <Grid fullWidth className="default-grid dashboard-grid">
      <Column sm={4} md={8} lg={16}>
        <h1 className="dashboard__title">{APP_NAME}</h1>
        <p className="dashboard__subtitle">{APP_FULL_NAME}</p>
      </Column>

      <Column sm={4} md={8} lg={16}>
        <InlineNotification
          kind="info"
          lowContrast
          hideCloseButton
          title="Application shell"
          subtitle={
            'Authentication and authorization are wired to FAM. The CBR screens have not been ' +
            'built yet — see cbr-overview.local.md for the inventory they will cover.'
          }
        />
      </Column>

      {/* Renders the resolved roles so FAM group wiring can be verified end-to-end without a
          screen to exercise. Remove once real screens exist. */}
      <Column sm={4} md={8} lg={16}>
        <dl className="dashboard__roles">
          <dt>Administrator</dt>
          <dd>{isSysAdmin ? 'yes' : 'no'}</dd>
          <dt>Can read</dt>
          <dd>{canRead ? 'yes' : 'no'}</dd>
          <dt>Can record an inspection</dt>
          <dd>{canWriteInspection ? 'yes' : 'no'}</dd>
          <dt>Can edit</dt>
          <dd>{canEdit ? 'yes' : 'no'}</dd>
          <dt>Can delete / archive</dt>
          <dd>{canDelete ? 'yes' : 'no'}</dd>
          <dt>Can sign off inspections</dt>
          <dd>{isPeng ? 'yes' : 'no'}</dd>
        </dl>
      </Column>
    </Grid>
  );
};

export default DashboardPage;
