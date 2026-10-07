import { Building, Pipelines, RecentlyViewed, Renew, Road, Scales } from '@carbon/icons-react';
import {
  Checkbox,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  Tag,
} from '@carbon/react';
import { useState } from 'react';

import ExternalLink from '@/components/core/ExternalLink';
import ReadOnlyField from '@/components/core/ReadOnlyField';

import Card from './Card';
import CommentsCard from './CommentsCard';
import { describe, isOther, money, number, yesNo } from './format';
import OutstandingPanel from './OutstandingPanel';

import type { OutstandingGroup } from './format';
import type {
  BridgeDetails,
  CulvertDetails,
  LoadRatingEntry,
  StructureDetailResponse,
  StructureRef,
} from './structureResponse';
import type { FC } from 'react';

import { formatShortDate } from '@/utils/date';

type Props = {
  structure: StructureDetailResponse;
  outstanding: OutstandingGroup[];
};

/** A code field, followed by its "If Other, please specify" comment when the code is Other. */
const codeWithOther = (
  label: string,
  value: StructureDetailResponse['typeClass'],
  comment: string | null,
  otherLabel = 'If Other, please specify',
) => (
  <>
    <ReadOnlyField label={label} value={describe(value)} />
    {isOther(value) && <ReadOnlyField label={otherLabel} value={comment} />}
  </>
);

/** Legacy's culvert type for a wood log culvert, which hides Culvert Type and Open Bottom. */
const WOOD_LOG_CULVERT = 'WLC';

const BridgeFields: FC<{ bridge: BridgeDetails }> = ({ bridge }) => (
  <>
    <ReadOnlyField label="Number of Spans" value={String(bridge.spanCount)} />
    <ReadOnlyField label="Needle Beams?" value={yesNo(bridge.needleBeams)} />
    <ReadOnlyField label="Bridge Length (metres)" value={number(bridge.lengthMetres)} />
    {codeWithOther('Superstructure', bridge.superstructure, bridge.superstructureComment)}
    {codeWithOther('Deck Type', bridge.deckType, bridge.deckTypeComment)}
    <ReadOnlyField label="Deck Width (metres)" value={number(bridge.deckWidthMetres)} />
    <ReadOnlyField label="Running Surface" value={describe(bridge.runningSurface)} />
    {codeWithOther('Curb Type', bridge.curbType, bridge.curbTypeComment)}
    <ReadOnlyField
      label="Left Abutment (Looking Downstream)"
      value={describe(bridge.leftAbutment)}
    />
    <ReadOnlyField
      label="Right Abutment (Looking Downstream)"
      value={describe(bridge.rightAbutment)}
    />
    {(isOther(bridge.leftAbutment) || isOther(bridge.rightAbutment)) && (
      <ReadOnlyField
        label="If Other (Right or Left), please specify"
        value={bridge.abutmentComment}
      />
    )}
  </>
);

const CulvertFields: FC<{ culvert: CulvertDetails; woodLog: boolean }> = ({ culvert, woodLog }) => (
  <>
    <ReadOnlyField
      label="Culvert Number"
      value={
        culvert.culvertNumber === null
          ? null
          : `${culvert.culvertNumber} of ${culvert.culvertsOnSite}`
      }
    />
    <ReadOnlyField label="Culvert Length (metres)" value={number(culvert.lengthMetres)} />
    <ReadOnlyField label="Gradient" value={number(culvert.gradient)} />
    {!woodLog && <ReadOnlyField label="Culvert Type" value={describe(culvert.culvertType)} />}
    {codeWithOther('Culvert Material', culvert.material, culvert.materialComment)}
    <ReadOnlyField label="Inlet Cover Depth (mm)" value={number(culvert.inletCoverDepthMm)} />
    <ReadOnlyField label="Outlet Cover Depth (mm)" value={number(culvert.outletCoverDepthMm)} />
    <ReadOnlyField label="Opening Height (mm)" value={number(culvert.openingHeightMm)} />
    <ReadOnlyField label="Opening Width (mm)" value={number(culvert.openingWidthMm)} />
    <ReadOnlyField label="Headwall Location" value={describe(culvert.headwallLocation)} />
    {!woodLog && (
      <ReadOnlyField
        label="Open Bottom Substructure"
        value={describe(culvert.openBottomSubstructure)}
      />
    )}
  </>
);

/**
 * Another structure, in its own tab — as the site link in the header opens, so this structure stays
 * where the user left it. The page reads its own name, so no state is passed: none survives into a
 * new tab anyway.
 */
const structureLink = (structure: StructureRef) => (
  <ExternalLink key={structure.id} to={`/inventory/structure/${structure.id}`}>
    {structure.structureName || structure.id}
  </ExternalLink>
);

/** A list of structure links, or an em dash when there are none. */
const structureLinks = (structures: StructureRef[]) =>
  structures.length === 0 ? null : (
    <span className="structure-detail__links">{structures.map(structureLink)}</span>
  );

/**
 * Whether a load rating predates the superstructure — legacy hides those unless asked
 * (`StructureAction.java:1999-2020`): a rating from before 1 January of the year it was installed.
 */
const beforeInstall = (entry: LoadRatingEntry, yearBuilt: number | null): boolean =>
  yearBuilt !== null && entry.date !== null && entry.date < `${yearBuilt}-01-01`;

/**
 * The Details tab, read-only: the structure's own fields, then legacy's fieldsets — Load Rating
 * Details, Replacement History, Replacement Details and Comments (`detailsTab.jsp`).
 *
 * <p>Bridge or culvert fields by which record the server sent, as the server decides it from the
 * bridge or culvert row. A field with nothing stored shows an em dash; legacy's invented values —
 * a next inspection a year out, "UNK" signs, a culvert frequency of 3 — are not reproduced.
 */
const DetailsTab: FC<Props> = ({ structure, outstanding }) => {
  const { details, bridge, culvert, loadRating, replacement } = structure;
  const [showEarlyRatings, setShowEarlyRatings] = useState(false);

  const hiddenRatings = loadRating.history.filter((entry) =>
    beforeInstall(entry, details.yearBuilt),
  ).length;
  const ratings = showEarlyRatings
    ? loadRating.history
    : loadRating.history.filter((entry) => !beforeInstall(entry, details.yearBuilt));

  return (
    <div className="structure-detail__tab-panel" data-testid="structure-details-tab">
      <OutstandingPanel groups={outstanding} />

      <Card title="Structure" icon={Building} testId="structure-section-common">
        <div className="structure-detail__fields">
          <ReadOnlyField label="Built For" value={describe(details.builtBy)} />
          <ReadOnlyField
            label="Year Superstructure Fabricated"
            value={number(details.yearFabricated)}
          />
          <ReadOnlyField label="Year Superstructure Installed" value={number(details.yearBuilt)} />
          <ReadOnlyField
            label="Year Added to Inventory"
            value={number(details.inventoryAddedYear)}
          />
          <ReadOnlyField label="Source" value={describe(details.source)} />
          <ReadOnlyField
            label="End of Design Life (year)"
            value={number(details.endOfDesignLifeYear)}
          />
          <ReadOnlyField
            label="As Built Information on File?"
            value={yesNo(details.asBuiltInfoOnFile)}
          />
          <ReadOnlyField label="Portable Structure?" value={yesNo(details.portable)} />
          <ReadOnlyField label="Installation Cost ($)" value={money(details.installationCost)} />
          <ReadOnlyField label="Material Cost ($)" value={money(details.materialCost)} />
        </div>
      </Card>

      {bridge && (
        <Card title="Bridge" icon={Road} testId="structure-section-bridge">
          <div className="structure-detail__fields">
            <BridgeFields bridge={bridge} />
          </div>
        </Card>
      )}

      {culvert && (
        <Card title="Culvert" icon={Pipelines} testId="structure-section-culvert">
          <div className="structure-detail__fields">
            <CulvertFields
              culvert={culvert}
              woodLog={structure.typeClass.code === WOOD_LOG_CULVERT}
            />
          </div>
        </Card>
      )}

      <Card title="Load Rating Details" icon={Scales} testId="structure-section-load-rating">
        <div className="structure-detail__fields">
          <ReadOnlyField
            label="Current Load Rating GVW (tonnes)"
            value={number(loadRating.currentRating)}
          />
          <ReadOnlyField
            label="Original Design Load Rating GVW (tonnes)"
            value={number(loadRating.designLoadRating)}
          />
          <ReadOnlyField
            label="Are Load Posting Signs Warranted?"
            value={describe(loadRating.loadPostingSigns)}
          />
          <ReadOnlyField
            label="Load Rating Review Required"
            value={yesNo(loadRating.reviewRequired)}
          />
          {codeWithOther(
            'Design Vehicle Configuration',
            loadRating.designVehicle,
            loadRating.designVehicleComment,
          )}
        </div>

        {loadRating.currentRating !== null &&
          loadRating.designLoadRating !== null &&
          loadRating.designLoadRating > loadRating.currentRating && (
            <p className="structure-detail__note" data-testid="structure-downrated">
              The current load rating is less than the design load rating.
            </p>
          )}

        {loadRating.history.length === 0 ? (
          <p className="structure-detail__empty">No load ratings.</p>
        ) : (
          <div className="structure-detail__history" data-testid="load-rating-history">
            {hiddenRatings > 0 && (
              <div className="structure-detail__history-toggle">
                <Checkbox
                  id="structure-show-early-ratings"
                  labelText={`Show ratings from before the superstructure was installed (${hiddenRatings})`}
                  checked={showEarlyRatings}
                  onChange={(_, { checked }) => setShowEarlyRatings(checked)}
                />
              </div>
            )}
            <Table size="md" useZebraStyles aria-label="Load rating history">
              <TableHead>
                <TableRow>
                  <TableHeader className="structure-detail__nowrap">
                    Load Rating GVW (tonnes)
                  </TableHeader>
                  <TableHeader>Reason</TableHeader>
                  <TableHeader className="structure-detail__nowrap">Date</TableHeader>
                  <TableHeader className="structure-detail__nowrap">IDIR ID</TableHeader>
                  <TableHeader className="structure-detail__nowrap">Reviewed Date</TableHeader>
                  <TableHeader className="structure-detail__nowrap">Status</TableHeader>
                </TableRow>
              </TableHead>
              <TableBody>
                {ratings.map((entry) => (
                  <TableRow
                    key={entry.id}
                    className={entry.current ? 'structure-detail__current-rating' : undefined}
                    data-testid={`load-rating-${entry.id}`}
                  >
                    <TableCell className="structure-detail__nowrap">
                      {entry.rating === null ? 'Not recorded' : entry.rating}
                      {entry.current && (
                        <Tag type="blue" size="sm" className="structure-detail__current-tag">
                          Current
                        </Tag>
                      )}
                    </TableCell>
                    <TableCell>
                      {[entry.reason.code, entry.reason.description].filter(Boolean).join(' - ')}
                    </TableCell>
                    <TableCell className="structure-detail__nowrap">
                      {formatShortDate(entry.date)}
                    </TableCell>
                    <TableCell className="structure-detail__nowrap">{entry.userId}</TableCell>
                    <TableCell className="structure-detail__nowrap">
                      {formatShortDate(entry.reviewedDate)}
                    </TableCell>
                    <TableCell className="structure-detail__nowrap">
                      {entry.status === 'MANUAL' ? 'Manual' : 'Reviewed'}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
      </Card>

      <Card
        title="Replacement History"
        icon={RecentlyViewed}
        testId="structure-section-replacement-history"
      >
        <div className="structure-detail__fields">
          <ReadOnlyField
            label="This structure replaced structure #"
            value={structureLinks(structure.replaced)}
          />
          <ReadOnlyField
            label="This structure was replaced by structure #"
            value={structureLinks(structure.replacedBy)}
          />
        </div>
      </Card>

      <Card title="Replacement Details" icon={Renew} testId="structure-section-replacement">
        <div className="structure-detail__fields">
          <ReadOnlyField
            label="Estimated Closure (year)"
            value={number(replacement.estimatedClosureYear)}
          />
          <ReadOnlyField
            label="Estimated Replacement (year)"
            value={number(replacement.estimatedReplacementYear)}
          />
          <ReadOnlyField
            label="Estimated Load Restriction (year)"
            value={number(replacement.estimatedLoadRestrictionYear)}
          />
          <ReadOnlyField
            label="Estimated Replacement Cost ($)"
            value={money(replacement.estimatedReplacementCost)}
          />
        </div>
        <div className="structure-detail__wide">
          <ReadOnlyField
            label="Replacement Cost Comments"
            value={replacement.replacementCostComment}
          />
        </div>
      </Card>

      <CommentsCard comments={structure.comments} />
    </div>
  );
};

export default DetailsTab;
