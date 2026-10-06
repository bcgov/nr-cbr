import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@carbon/react';

import type { StructureComment } from './structureResponse';
import type { FC } from 'react';

import { formatDateTime } from '@/utils/date';

type Props = { comments: StructureComment[] };

/**
 * The structure's general comments, newest first — legacy's "Comments" fieldset on the Details tab
 * (`detailsTab.jsp:821-869`), given a tab of its own here so a long run of comments does not push
 * the load ratings and replacement details down the page.
 *
 * <p>Planned-inspection comments are not here: legacy keeps those on the Inspections tab.
 */
const CommentsTab: FC<Props> = ({ comments }) => (
  <div className="structure-detail__details" data-testid="structure-comments-tab">
    {comments.length === 0 ? (
      <p className="structure-detail__empty">No comments.</p>
    ) : (
      <Table size="md" useZebraStyles aria-label="Comments">
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
  </div>
);

export default CommentsTab;
