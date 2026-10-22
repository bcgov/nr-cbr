import { type FC } from 'react';

import InspectionsConductedPage from './InspectionsConducted';
import InspectionsDuePage from './InspectionsDue';
import MonitorsSummaryPage from './MonitorsSummary';
import RepairsSummaryPage from './RepairsSummary';
import StructureDetailReportPage from './StructureDetailReport';
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
    case 'structure-detail':
      return <StructureDetailReportPage />;
    case 'repairs-summary':
      return <RepairsSummaryPage />;
    case 'monitors-summary':
      return <MonitorsSummaryPage />;
    // One form for both, keyed so moving between them starts each with its own criteria.
    case 'inspections-conducted-summary':
      return <InspectionsConductedPage key="summary" variant="summary" />;
    case 'inspections-conducted-detail':
      return <InspectionsConductedPage key="detail" variant="detail" />;
    // One form for the three, keyed likewise.
    case 'inspections-due-summary':
      return <InspectionsDuePage key="summary" variant="summary" />;
    case 'inspections-due-detailed':
      return <InspectionsDuePage key="detailed" variant="detailed" />;
    case 'inspections-blank':
      return <InspectionsDuePage key="blank" variant="blank" />;
    default:
      return <ReportPlaceholderPage name={name} />;
  }
};

export default ReportPage;
