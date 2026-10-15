import ReadOnlyField from '@/components/core/ReadOnlyField';

import type { StructureSite } from './structureResponse';
import type { FC } from 'react';

/** A recreation site, whose district and road read differently. */
const RECREATION = 'REC';

/** The site's own fields, as the tab shows them — for the structure's site, or one to move to. */
const SiteFields: FC<{ site: StructureSite | null }> = ({ site }) => {
  const recreation = site?.siteTypeCode === RECREATION;
  const projectFile = [site?.forestFileId, site?.roadSectionId].filter(Boolean).join('-');
  return (
    <>
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
    </>
  );
};

export default SiteFields;
