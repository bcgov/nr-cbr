import { Column, Grid } from '@carbon/react';

import Bulletin from '@/components/Bulletin';
import PageTitle from '@/components/core/PageTitle';

import type { FC } from 'react';

import { APP_FULL_NAME } from '@/constants/appName';

/**
 * Dashboard — where a signed-in user lands, as legacy's welcome page (`showWelcome.do`) was: the
 * administrator's bulletin, under its heading.
 */
const DashboardPage: FC = () => (
  <Grid fullWidth className="default-grid">
    <PageTitle title="Dashboard" subtitle={`Welcome to the ${APP_FULL_NAME}.`} />

    <Column sm={4} md={8} lg={12}>
      <Bulletin id="dashboard" />
    </Column>
  </Grid>
);

export default DashboardPage;
