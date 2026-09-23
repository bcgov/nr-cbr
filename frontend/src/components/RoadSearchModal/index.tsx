import { Search as SearchIcon } from '@carbon/icons-react';
import {
  Button,
  DataTableSkeleton,
  Pagination,
  InlineNotification,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  TextInput,
} from '@carbon/react';
import { useMutation } from '@tanstack/react-query';
import { useCallback, useState, type FC, type KeyboardEvent } from 'react';

import ClientCombo from '@/components/core/ClientCombo';
import { Modal } from '@/components/Modal';

import type { RoadSearchCriteria, RoadSearchResult } from '@/services/road.service';
import type { ClientSuggestion } from '@/types/client';

import API from '@/services/APIs';
import { EMPTY_ROAD_CRITERIA } from '@/services/road.service';
import { apiErrorMessage } from '@/utils/apiError';
import { clientLabel } from '@/utils/clientSearch';

import './index.scss';

/**
 * Rows per page.
 *
 * <p>Fifteen fits the dialog without it scrolling on a laptop, which is the point of paging a
 * lookup: the user is scanning for one road, and a list they have to scroll past the Search button
 * to read is harder to scan than a short one they can step through.
 */
const ROADS_PER_PAGE = 15;

/** The server caps at two hundred, so nothing above that is worth offering. */
const ROAD_PAGE_SIZES = [15, 50, 100];

type Props = {
  /** A road was chosen — the two halves of the pair the site form records. */
  onSelect: (road: RoadSearchResult) => void;
  onClose: () => void;
};

/**
 * The Project File ID# lookup: search for a road, pick one, and the form takes the pair.
 *
 * <p>Replaces legacy's `showRoadSearch()`, which opens a **second browser window** —
 * `window.open(…, '_blank', 'height=350, width=550, toolbar=0')` — and writes back into the opener
 * through `window.opener.$('SiteForm')`. A popup is blocked by default in every current browser,
 * is unreachable on a phone, and leaves the form it is editing behind an unrelated window.
 *
 * <p><b>Mounted only while open</b>, by the page. Every opening therefore starts clean, which is
 * what a dialog should do — one that reopens holding the last search answers a question the user
 * has not asked, against a form that has moved on since.
 *
 * <p><b>Searched on demand, not as the user types.</b> The criteria are six partial matches over a
 * view of every road section in the province; a keystroke-driven search would run the widest
 * possible query most often. Legacy has a Search button for the same reason, and this keeps it.
 */
const RoadSearchModal: FC<Props> = ({ onSelect, onClose }) => {
  const [criteria, setCriteria] = useState<RoadSearchCriteria>(EMPTY_ROAD_CRITERIA);
  /** The label of the client picked from the lookup, so the field survives a re-render. */
  const [clientLabelText, setClientLabelText] = useState('');
  /** One-based, as Carbon's Pagination counts. */
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(ROADS_PER_PAGE);

  // A mutation rather than a query: this runs when the user presses Search, not when a key
  // changes. A query keyed on the criteria would re-run on every keystroke, which is the
  // behaviour the Search button exists to avoid.
  const results = useMutation({
    mutationFn: (asked: RoadSearchCriteria) => API.road.searchRoads(asked),
  });

  const update = useCallback(
    (field: keyof RoadSearchCriteria, value: string) =>
      setCriteria((current) => ({ ...current, [field]: value })),
    [],
  );

  /**
   * A client was picked: narrow to that one, and drop any name the user had typed. Both would
   * otherwise apply, and the number is the more precise of the two.
   */
  const selectClient = useCallback((client: ClientSuggestion | null) => {
    setClientLabelText(client ? clientLabel(client) : '');
    setCriteria((current) => ({
      ...current,
      clientNumber: client?.clientNumber ?? '',
      clientName: '',
    }));
  }, []);

  /**
   * Typed text that is not a pick still searches, on the name.
   *
   * <p>This is what the plain text box gave and a combo box would otherwise take away: several
   * distinct clients are called Canfor, and no single pick covers them.
   */
  const typeClient = useCallback((term: string) => {
    setClientLabelText('');
    setCriteria((current) => ({ ...current, clientName: term, clientNumber: '' }));
  }, []);

  const text = (field: keyof RoadSearchCriteria, labelText: string, maxLength: number) => (
    <TextInput
      id={`road-search-${field}`}
      data-testid={`road-search-${field}`}
      labelText={labelText}
      maxLength={maxLength}
      value={criteria[field]}
      onChange={(event) => update(field, event.target.value)}
    />
  );

  const rows = results.data ?? [];
  // Sliced here rather than asked for a page at a time: the server already capped the answer at
  // two hundred, so every row is in hand and a round trip per page would be fetching what we have.
  const visible = rows.slice((page - 1) * pageSize, page * pageSize);

  return (
    <Modal
      open
      modalHeading="Road Search"
      passiveModal
      size="lg"
      aria-label="Road Search"
      onRequestClose={onClose}
    >
      <form
        className="road-search__form"
        data-testid="road-search-form"
        onSubmit={(event) => {
          event.preventDefault();
          // Back to the first page: page 7 of the previous answer is an empty table in this one.
          setPage(1);
          results.mutate(criteria);
        }}
      >
        <div className="road-search__criteria">
          {text('forestServiceRoad', 'Forest Service Road', 35)}
          <div className="road-search__paired">
            {text('forestFileId', 'Project File ID#', 10)}
            {text('roadSectionId', 'Br.', 30)}
          </div>
          {/* Legacy prints this key beside the box, because the view carries the code and nothing
              in the database decodes it. */}
          <TextInput
            id="road-search-tenureType"
            data-testid="road-search-tenureType"
            labelText="Tenure Type"
            helperText="B01 = RP, B40 = FSR, S01 = SUP-F, S02 = SUP-NF"
            maxLength={10}
            value={criteria.tenureType}
            onChange={(event) => update('tenureType', event.target.value)}
          />
          {/* One lookup where legacy has two boxes, as the Designated Maintainer field does — and
              scoped to the clients that actually hold a road file, which is a different set from
              the site maintainers that field offers.

              A pick narrows to one client number. A term that resolves to nothing still searches,
              on the name, so "canfor" keeps returning the roads of every Canfor entity — the
              broader search the plain text box used to give. */}
          <ClientCombo
            id="road-search-client"
            titleText="Client"
            scope="ROAD_FILE_HOLDERS"
            placeholder="Search for a client"
            helperText="Name or client number of the company holding the road file"
            selectedLabel={clientLabelText}
            onSelect={selectClient}
            onTermChange={typeClient}
          />
          <div className="road-search__actions">
            <Button kind="tertiary" type="button" onClick={onClose}>
              Cancel
            </Button>
            <Button
              kind="primary"
              type="submit"
              renderIcon={SearchIcon}
              data-testid="road-search-submit"
              disabled={results.isPending}
            >
              Search
            </Button>
          </div>
        </div>
      </form>

      {results.isError && (
        <InlineNotification
          kind="error"
          lowContrast
          hideCloseButton
          title="The search could not be run"
          subtitle={apiErrorMessage(results.error, 'Try again, or narrow the criteria.')}
          data-testid="road-search-error"
        />
      )}

      {results.isPending && results.isIdle === false && (
        <DataTableSkeleton
          role="progressbar"
          aria-label="Searching"
          data-testid="road-search-loading"
          columnCount={6}
          rowCount={4}
          showHeader={false}
          showToolbar={false}
        />
      )}

      {results.isSuccess && (
        <TableContainer
          // Legacy says "Search returned 200 or more records, 200 shown" when the cap fills. The
          // cap is the server's, so a full page is indistinguishable from exactly two hundred
          // matches — which is what that wording admits.
          title={
            rows.length === 200
              ? '200 or more roads found — showing the first 200'
              : `${rows.length} road${rows.length === 1 ? '' : 's'} found`
          }
          className="bordered-table"
          data-testid="road-search-results"
        >
          <Table useZebraStyles size="sm">
            <TableHead>
              <TableRow>
                <TableHeader>Forest Service Road</TableHeader>
                <TableHeader>Project File ID#</TableHeader>
                <TableHeader>Br.</TableHeader>
                <TableHeader>Tenure Type</TableHeader>
                <TableHeader>Client Name</TableHeader>
                <TableHeader>Client #</TableHeader>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6}>No roads found.</TableCell>
                </TableRow>
              ) : (
                visible.map((road) => (
                  /* The whole row picks the road, because the whole row is what the user is
                     reading — scanning the tenure type or the client and then having to travel
                     back to the first cell to act on it is work the dialog can spare them.

                     The row is a convenience for a mouse and nothing more: a <tr> cannot be
                     focused or activated from a keyboard, and giving it a role that could would
                     cost the table its own semantics. The button in the first cell stays as the
                     real control, which is what a keyboard and a screen reader use. */
                  <TableRow
                    key={`${road.forestFileId}-${road.roadSectionId}`}
                    className="road-search__row"
                    data-testid={`road-search-row-${road.forestFileId}-${road.roadSectionId}`}
                    // Focusable, so the row is reachable without a mouse. `tabIndex` on a `<tr>`
                    // costs the table none of its semantics — a screen reader still announces a
                    // row and its columns — where `role="button"` would have replaced them.
                    tabIndex={0}
                    onClick={() => onSelect(road)}
                    onKeyDown={(event: KeyboardEvent) => {
                      // Enter and Space, the two keys that activate anything else on the page.
                      // Space would otherwise scroll the dialog out from under the user.
                      if (event.key === 'Enter' || event.key === ' ') {
                        event.preventDefault();
                        onSelect(road);
                      }
                    }}
                  >
                    {/* Plain text. The whole row is the control, so underlining one cell of it
                        promised something narrower than the truth — and read as a link, which
                        this has never been: picking a road fills in the form behind the dialog
                        rather than going anywhere. */}
                    <TableCell>
                      {road.forestServiceRoad || `${road.forestFileId}-${road.roadSectionId}`}
                    </TableCell>
                    <TableCell>{road.forestFileId}</TableCell>
                    <TableCell>{road.roadSectionId}</TableCell>
                    <TableCell>{road.tenureType}</TableCell>
                    <TableCell>{road.clientName}</TableCell>
                    <TableCell>{road.clientNumber}</TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
          <Pagination
            data-testid="road-search-pagination"
            page={page}
            pageSize={pageSize}
            pageSizes={ROAD_PAGE_SIZES}
            totalItems={rows.length}
            onChange={({ page: nextPage, pageSize: nextPageSize }) => {
              setPage(nextPage);
              setPageSize(nextPageSize);
            }}
          />
        </TableContainer>
      )}
    </Modal>
  );
};

export default RoadSearchModal;
