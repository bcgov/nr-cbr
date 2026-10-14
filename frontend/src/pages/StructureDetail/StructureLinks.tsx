import ExternalLink from '@/components/core/ExternalLink';

import type { StructureRef } from './structureResponse';
import type { FC } from 'react';

/**
 * Other structures, each a link that opens in its own tab — as the site link in the header opens,
 * so this structure stays where the user left it. The page reads its own name, so no state is
 * passed: none survives into a new tab anyway. Give a field `null`, not this, when there are none,
 * so it shows its em dash.
 */
const StructureLinks: FC<{ structures: StructureRef[] }> = ({ structures }) =>
  structures.length === 0 ? null : (
    <span className="structure-detail__links">
      {structures.map((structure) => (
        <ExternalLink key={structure.id} to={`/inventory/structure/${structure.id}`}>
          {structure.structureName || structure.id}
        </ExternalLink>
      ))}
    </span>
  );

export default StructureLinks;
