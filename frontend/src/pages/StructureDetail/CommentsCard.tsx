import { Add, Edit, Forum } from '@carbon/icons-react';
import {
  Button,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react';

import Card from './Card';

import type { StructureComment } from './structureResponse';
import type { FC } from 'react';

import { formatDateTime } from '@/utils/date';

type Props = {
  comments: StructureComment[];
  /** The card's title; the general comments' by default. */
  title?: string;
  testId?: string;
  /** Offers "Add comment" in the card's header — only to a user who may add. */
  onAdd?: () => void;
  /** Offers Edit on each row — only to a user who may edit. */
  onEdit?: (comment: StructureComment) => void;
  /** Holds Add while another card on the tab is being edited, as nr-fspts' sections do. */
  addDisabled?: boolean;
};

/**
 * The structure's general comments, newest first — legacy's "Comments" fieldset, last on its Details
 * tab (`detailsTab.jsp:821-869`), and last of the Details tab's cards here.
 *
 * <p>Also the Inspections tab's Planned Inspection Comments, under that title: legacy keeps those
 * there, in the same table. There they can be added (the card's header) and edited (each row).
 */
const CommentsCard: FC<Props> = ({
  comments,
  title = 'Comments',
  testId = 'structure-section-comments',
  onAdd,
  onEdit,
  addDisabled = false,
}) => (
  <Card
    title={title}
    icon={Forum}
    testId={testId}
    action={
      onAdd && (
        <Button
          kind="tertiary"
          size="sm"
          renderIcon={Add}
          disabled={addDisabled}
          data-testid={`${testId}-add`}
          onClick={onAdd}
        >
          Add comment
        </Button>
      )
    }
  >
    {comments.length === 0 ? (
      <p className="structure-detail__empty">No comments.</p>
    ) : (
      <Table size="md" useZebraStyles aria-label={title}>
        <TableHead>
          <TableRow>
            <TableHeader>Comment</TableHeader>
            <TableHeader className="structure-detail__nowrap">IDIR ID</TableHeader>
            <TableHeader className="structure-detail__nowrap">Date</TableHeader>
            {/* Only for a user who may act, as the other tables' Actions columns. */}
            {onEdit && <TableHeader>Actions</TableHeader>}
          </TableRow>
        </TableHead>
        <TableBody>
          {comments.map((comment) => (
            <TableRow key={comment.id}>
              <TableCell className="structure-detail__comment">{comment.text}</TableCell>
              <TableCell className="structure-detail__nowrap">{comment.userId}</TableCell>
              <TableCell className="structure-detail__nowrap">
                {formatDateTime(comment.timestamp)}
              </TableCell>
              {onEdit && (
                <TableCell className="structure-detail__nowrap">
                  <Button
                    kind="ghost"
                    size="sm"
                    renderIcon={Edit}
                    data-testid={`${testId}-edit-${comment.id}`}
                    onClick={() => onEdit(comment)}
                  >
                    Edit <span className="cds--visually-hidden">comment</span>
                  </Button>
                </TableCell>
              )}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    )}
  </Card>
);

export default CommentsCard;
