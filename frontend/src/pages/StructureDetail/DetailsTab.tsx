import { useState } from 'react';

import ReadOnlyField from '@/components/core/ReadOnlyField';

import BridgeCard from './BridgeCard';
import CommentDialog from './CommentDialog';
import CommentsCard from './CommentsCard';
import CulvertCard from './CulvertCard';
import { describe, yesNo } from './format';
import LoadRatingCard from './LoadRatingCard';
import OutstandingPanel from './OutstandingPanel';
import ReplacementCard from './ReplacementCard';
import ReplacementHistoryCard from './ReplacementHistoryCard';
import StructureCard from './StructureCard';

import type { OutstandingGroup } from './format';
import type { StructureComment, StructureDetailResponse } from './structureResponse';
import type { FC } from 'react';

import { useAuthorization } from '@/hooks/useAuthorization';

type Props = {
  structure: StructureDetailResponse;
  outstanding: OutstandingGroup[];
};

/** Legacy's culvert type for a wood log culvert, which hides Culvert Type and Open Bottom. */
const WOOD_LOG_CULVERT = 'WLC';

/**
 * The Details tab, read-only: the structure's own fields, then legacy's fieldsets — Load Rating
 * Details, Replacement Details (with legacy's Replacement History) and Comments (`detailsTab.jsp`).
 *
 * <p>Bridge or culvert fields by which record the server sent, as the server decides it from the
 * bridge or culvert row. A field with nothing stored shows an em dash; legacy's invented values —
 * a next inspection a year out, "UNK" signs, a culvert frequency of 3 — are not reproduced.
 */
const DetailsTab: FC<Props> = ({ structure, outstanding }) => {
  const { details, bridge, culvert, loadRating, replacement } = structure;

  /** The card being edited, if any — one at a time, as nr-fspts' sections. */
  const [editing, setEditing] = useState<
    'structure' | 'bridge' | 'culvert' | 'load-rating' | 'replacement' | null
  >(null);
  /** The open comment dialog, if any: a comment to edit, or `'new'` to add one. */
  const [comment, setComment] = useState<StructureComment | 'new' | null>(null);
  const { canEdit } = useAuthorization();

  // First in the Bridge or Culvert card, as legacy has Portable there: a bridge's Type/Class and
  // Portable are edited with that card, beside the Superstructure, Curb Type and abutments their
  // rules check.
  const identity = (
    <>
      <ReadOnlyField label="Type/Class" value={describe(structure.typeClass)} />
      <ReadOnlyField label="Portable Structure?" value={yesNo(details.portable)} />
    </>
  );

  return (
    <div className="structure-detail__tab-panel" data-testid="structure-details-tab">
      <OutstandingPanel groups={outstanding} />

      <StructureCard
        structureId={structure.id}
        details={details}
        // Type/Class and Portable belong to the bridge or culvert card; here only without one.
        extra={!bridge && !culvert ? identity : undefined}
        editing={editing === 'structure'}
        otherEditing={editing !== null && editing !== 'structure'}
        onEdit={() => setEditing('structure')}
        onDone={() => setEditing(null)}
      />

      {bridge && (
        <BridgeCard
          structureId={structure.id}
          bridge={bridge}
          typeClass={structure.typeClass}
          portable={details.portable}
          identity={identity}
          editing={editing === 'bridge'}
          otherEditing={editing !== null && editing !== 'bridge'}
          onEdit={() => setEditing('bridge')}
          onDone={() => setEditing(null)}
        />
      )}

      {culvert && (
        <CulvertCard
          structureId={structure.id}
          culvert={culvert}
          portable={details.portable}
          woodLog={structure.typeClass.code === WOOD_LOG_CULVERT}
          identity={identity}
          typeClass={<ReadOnlyField label="Type/Class" value={describe(structure.typeClass)} />}
          editing={editing === 'culvert'}
          otherEditing={editing !== null && editing !== 'culvert'}
          onEdit={() => setEditing('culvert')}
          onDone={() => setEditing(null)}
        />
      )}

      <LoadRatingCard
        structureId={structure.id}
        loadRating={loadRating}
        yearBuilt={details.yearBuilt}
        editing={editing === 'load-rating'}
        otherEditing={editing !== null && editing !== 'load-rating'}
        onEdit={() => setEditing('load-rating')}
        onDone={() => setEditing(null)}
      />

      <ReplacementHistoryCard
        structureId={structure.id}
        structureName={structure.structureName}
        replaced={structure.replaced}
        replacedBy={structure.replacedBy}
        otherEditing={editing !== null}
      />

      <ReplacementCard
        structureId={structure.id}
        replacement={replacement}
        yearBuilt={details.yearBuilt}
        recreation={structure.site?.siteTypeCode === 'REC'}
        editing={editing === 'replacement'}
        otherEditing={editing !== null && editing !== 'replacement'}
        onEdit={() => setEditing('replacement')}
        onDone={() => setEditing(null)}
      />

      {/* Legacy's /level1Access: Level 1 and up add and edit; there is no delete. */}
      <CommentsCard
        comments={structure.comments}
        onAdd={canEdit ? () => setComment('new') : undefined}
        onEdit={canEdit ? setComment : undefined}
        addDisabled={editing !== null}
      />
      {comment !== null && (
        <CommentDialog
          key={comment === 'new' ? 'new' : comment.id}
          structureId={structure.id}
          comment={comment === 'new' ? null : comment}
          kind="general"
          onClose={() => setComment(null)}
        />
      )}
    </div>
  );
};

export default DetailsTab;
