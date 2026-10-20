import { type FC } from 'react';

import StructureInventoryDetailedPage from './StructureInventoryDetailed';
import StructureInventorySummaryPage from './StructureInventorySummary';

import ReportPlaceholderPage from './index';

/** A report's page: its criteria page once built, a placeholder naming it until then. */
const ReportPage: FC<{ path: string; name: string }> = ({ path, name }) => {
  switch (path) {
    case 'structure-inventory-summary':
      return <StructureInventorySummaryPage />;
    case 'structure-inventory-detailed':
      return <StructureInventoryDetailedPage />;
    default:
      return <ReportPlaceholderPage name={name} />;
  }
};

export default ReportPage;
