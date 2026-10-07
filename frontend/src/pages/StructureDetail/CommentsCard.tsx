import { Forum } from '@carbon/icons-react';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@carbon/react';

import Card from './Card';

import type { StructureComment } from './structureResponse';
import type { FC } from 'react';

import { formatDateTime } from '@/utils/date';

type Props = {
  comments: StructureComment[];
  /** The card's title; the general comments' by default. */
  title?: string;
  testId?: string;
};

/**
 * The structure's general comments, newest first — legacy's "Comments" fieldset, last on its Details
 * tab (`detailsTab.jsp:821-869`), and last of the Details tab's cards here.
 *
 * <p>Also the Inspections tab's Planned Inspection Comments, under that title: legacy keeps those
 * there, in the same table.
 */
const CommentsCard: FC<Props> = ({
  comments,
  title = 'Comments',
  testId = 'structure-section-comments',
}) => (
  <Card title={title} icon={Forum} testId={testId}>
    {comments.length === 0 ? (
      <p className="structure-detail__empty">No comments.</p>
    ) : (
      <Table size="md" useZebraStyles aria-label={title}>
        <TableHead>
          <TableRow>
            <TableHeader>Comment</TableHeader>
            <TableHeader className="structure-detail__nowrap">IDIR ID</TableHeader>
            <TableHeader className="structure-detail__nowrap">Date</TableHeader>
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
            </TableRow>
          ))}
        </TableBody>
      </Table>
    )}
  </Card>
);

export default CommentsCard;
