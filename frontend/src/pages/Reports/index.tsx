import { Column } from '@carbon/react';
import { type FC } from 'react';

import PageTitle from '@/components/core/PageTitle';

/**
 * A report's page, before the report exists: its name, marked under construction. Each report
 * replaces this with its criteria page as it is built.
 */
const ReportPlaceholderPage: FC<{ name: string }> = ({ name }) => (
  <Column lg={16} md={8} sm={4} className="dashboard-column__banner">
    <PageTitle title={name} subtitle="This report is not available yet." experimental />
  </Column>
);

export default ReportPlaceholderPage;
