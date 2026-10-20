import { Column, Grid, InlineNotification } from '@carbon/react';

import PageTitle from '@/components/core/PageTitle';

import './reportCriteria.scss';

import type { FC, ReactNode } from 'react';

type Props = {
  /** The prefix of the page's test ids — the report's. */
  id: string;
  title: string;
  subtitle: string;
  /** True when a dropdown list failed to load. */
  codeTablesFailed: boolean;
  children: ReactNode;
};

/**
 * A report's page: its title under Reports, a notice when a list failed to load — every other
 * criterion still works, so the form stays — and the form.
 */
const ReportPageLayout: FC<Props> = ({ id, title, subtitle, codeTablesFailed, children }) => (
  <Grid fullWidth className="default-grid report-criteria-grid">
    <PageTitle
      title={title}
      subtitle={subtitle}
      breadCrumbs={[{ name: 'Reports', path: '/reports' }]}
    />

    {codeTablesFailed && (
      <Column sm={4} md={8} lg={16}>
        <InlineNotification
          kind="error"
          lowContrast
          hideCloseButton
          title="Some filters could not be loaded"
          subtitle={
            'One or more of the dropdown lists is unavailable, so those filters are empty. ' +
            'Every other criterion still works.'
          }
          data-testid={`${id}-codes-error`}
        />
      </Column>
    )}

    <Column sm={4} md={8} lg={16}>
      {children}
    </Column>
  </Grid>
);

export default ReportPageLayout;
