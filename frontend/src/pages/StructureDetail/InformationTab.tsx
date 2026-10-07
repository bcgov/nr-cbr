import { Location } from '@carbon/icons-react';

import ExternalLink from '@/components/core/ExternalLink';
import ReadOnlyField from '@/components/core/ReadOnlyField';

import Card from './Card';
import { describe } from './format';

import type { StructureDetailResponse } from './structureResponse';
import type { FC } from 'react';

type Props = { structure: StructureDetailResponse };

/** Legacy's site type for a recreation site, which shows a district and project of its own. */
const RECREATION = 'REC';

/**
 * The Information tab, first of the page's tabs: what the structure is and the site it stands on —
 * legacy's header above its tabs (`structure.jsp:4069-4314`), read-only, moved into a tab of its own
 * as nr-fspts' FSP page opens on an Information tab.
 *
 * <p>Site Status is not here: it sits beside the page title as a status pill, as Site Search shows
 * it.
 *
 * <p>Legacy's two maintainer boxes — client number and location code — and its maintainer name are
 * one field here, as on Site Detail. Inspection Status is decoded; legacy showed the raw code.
 */
const InformationTab: FC<Props> = ({ structure }) => {
  const site = structure.site;
  const recreation = site?.siteTypeCode === RECREATION;
  const projectFile = [site?.forestFileId, site?.roadSectionId].filter(Boolean).join('-');

  return (
    <Card title="Structure and site" icon={Location} testId="structure-information-tab">
      <div className="structure-detail__fields" data-testid="structure-header">
        <ReadOnlyField label="Type/Class" value={describe(structure.typeClass)} />
        <ReadOnlyField
          label="Site #"
          value={
            // Its own tab, as nr-frep opens a linked record: the structure stays where the user
            // left it.
            site?.siteId ? (
              <ExternalLink to={`/inventory/site/${site.siteId}`}>{site.siteId}</ExternalLink>
            ) : null
          }
        />
        <ReadOnlyField label="Inspection Status" value={describe(site?.inspectionStatus)} />

        <ReadOnlyField
          label={recreation ? 'Recreation District' : 'Forest District'}
          value={site?.districtName}
        />
        {!recreation && <ReadOnlyField label="Management Area" value={site?.managementAreaName} />}
        <ReadOnlyField
          label={recreation ? 'Project Name' : 'Forest Service Road'}
          value={recreation ? site?.projectName : site?.forestServiceRoad}
        />
        <ReadOnlyField label="Project File ID#-Br." value={projectFile} />

        <ReadOnlyField label="Crossing Name" value={site?.crossingName} />
        <ReadOnlyField label="Kilometres" value={site?.kilometres} />
        <ReadOnlyField label="Designated Maintainer" value={site?.maintainerLabel} />
      </div>
    </Card>
  );
};

export default InformationTab;
