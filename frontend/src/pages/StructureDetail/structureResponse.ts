/**
 * What `GET /api/v1/structures/{structureId}` answers with — the structure page's header and its
 * Details tab.
 *
 * <p><b>The contract for a backend not yet built.</b> The page is built against this shape first;
 * the endpoint is the next piece of work, and has to answer in it. Field by field it is legacy's
 * structure page (`cbr-structure-page.local.md` §1–2), with the values legacy left raw or invented
 * corrected: every code arrives decoded, and a field with nothing stored is `null` rather than a
 * made-up value.
 *
 * <p>Dates are ISO `yyyy-MM-dd`, timestamps ISO `yyyy-MM-ddTHH:mm:ss`, both local Pacific time as
 * the audit columns hold them. Decimals that are measurements arrive as numbers.
 */

/** A stored code and what it means. Both null when nothing is stored. */
export type CodeValue = { code: string | null; description: string | null };

/** Another structure, as a link target. */
export type StructureRef = { id: string; structureName: string | null };

/** Which part of the page an outstanding item belongs to — the tab that will show it. */
export type OutstandingSection = 'SITE' | 'DETAILS' | 'INSPECTIONS';

/**
 * One reason the structure counts as incomplete — legacy's red "Warning: Structure data is
 * incomplete.", itemised. The same rules as Structure Search's "Incomplete Data?".
 */
export type OutstandingItem = {
  section: OutstandingSection;
  /** What is missing, as the user reads it: "Deck Width", "Estimated Load Restriction (year)". */
  label: string;
};

/** The site the structure stands on — the header above the tabs. */
export type StructureSite = {
  siteId: string;
  /** Decides Recreation District / Project Name over Forest District / Forest Service Road. */
  siteTypeCode: string | null;
  siteStatus: CodeValue;
  /** Legacy shows this as a raw code; decoded here. */
  inspectionStatus: CodeValue;
  /** Forest District, or Recreation District on a recreation site. */
  districtName: string | null;
  /** Null on a recreation site, which has none. */
  managementAreaName: string | null;
  forestServiceRoad: string | null;
  /** A recreation site's project name, shown where a crossing shows its road. */
  projectName: string | null;
  crossingName: string | null;
  forestFileId: string | null;
  roadSectionId: string | null;
  kilometres: string | null;
  /** "CANFOR CORPORATION · Vancouver · 00001012-01", as Site Detail builds it. */
  maintainerLabel: string | null;
};

/** Fields every structure has. */
export type StructureCommonDetails = {
  builtBy: CodeValue;
  yearFabricated: number | null;
  yearBuilt: number | null;
  inventoryAddedYear: number | null;
  source: CodeValue;
  /** "End of Design Life (year)" — `ORIGINAL_REPLACEMENT_DATE`, a year. */
  endOfDesignLifeYear: number | null;
  asBuiltInfoOnFile: boolean;
  installationCost: number | null;
  materialCost: number | null;
  portable: boolean;
};

/** `FOREST_SERVICE_BRIDGE`, for a bridge. */
export type BridgeDetails = {
  spanCount: number;
  needleBeams: boolean;
  lengthMetres: number | null;
  superstructure: CodeValue;
  superstructureComment: string | null;
  deckType: CodeValue;
  deckTypeComment: string | null;
  deckWidthMetres: number | null;
  runningSurface: CodeValue;
  curbType: CodeValue;
  curbTypeComment: string | null;
  leftAbutment: CodeValue;
  rightAbutment: CodeValue;
  abutmentComment: string | null;
};

/** `FOREST_SERVICE_CULVERT`, for a culvert. */
export type CulvertDetails = {
  culvertNumber: number | null;
  /** How many culverts stand on the site — the "of N" after the number. */
  culvertsOnSite: number;
  lengthMetres: number | null;
  gradient: number | null;
  culvertType: CodeValue;
  material: CodeValue;
  materialComment: string | null;
  inletCoverDepthMm: number | null;
  outletCoverDepthMm: number | null;
  openingHeightMm: number | null;
  openingWidthMm: number | null;
  headwallLocation: CodeValue;
  openBottomSubstructure: CodeValue;
};

/** A general comment on the structure. */
export type StructureComment = {
  id: string;
  text: string;
  /** `UPDATE_USERID` — the IDIR ID column. */
  userId: string | null;
  /** `UPDATE_TIMESTAMP`. */
  timestamp: string | null;
};

/** A comment's text, to add or change one — the backend's `CommentRequest`. */
export type CommentRequest = { comment: string };

/** The comment just added — the backend's `CreatedResponse`. */
export type CreatedResponse = { id: string };

/**
 * One row of the load rating history: a manual rating, or an inspection whose report was reviewed.
 */
export type LoadRatingEntry = {
  id: string;
  /** Tonnes GVW. Null for a reviewed inspection that recorded none — legacy's "UNK". */
  rating: number | null;
  reason: CodeValue;
  reasonComment: string | null;
  /** The rating's date: its entry date when manual, the inspection date when reviewed. */
  date: string | null;
  userId: string | null;
  /** When the inspection was reviewed; null for a manual rating. */
  reviewedDate: string | null;
  status: 'MANUAL' | 'REVIEWED';
  /** The inspection it came from, when it came from one. */
  inspectionId: string | null;
  /** True on the row whose rating is the structure's current one. */
  current: boolean;
};

export type LoadRatingDetails = {
  /** The current rating in tonnes, from the same rule as `LoadRatingService`. */
  currentRating: number | null;
  /** Newest first. */
  history: LoadRatingEntry[];
  loadPostingSigns: CodeValue;
  reviewRequired: boolean;
  designVehicle: CodeValue;
  designVehicleComment: string | null;
  designLoadRating: number | null;
};

export type ReplacementDetails = {
  estimatedClosureYear: number | null;
  estimatedReplacementYear: number | null;
  estimatedLoadRestrictionYear: number | null;
  estimatedReplacementCost: number | null;
  replacementCostComment: string | null;
};

export type StructureDetailResponse = {
  id: string;
  structureName: string | null;
  /** False once archived — `ACTIVE_IND = 'N'`. Legacy never shows this. */
  active: boolean;
  typeClass: CodeValue;
  site: StructureSite | null;
  details: StructureCommonDetails;
  /** Present for a bridge, null otherwise. */
  bridge: BridgeDetails | null;
  /** Present for a culvert, null otherwise. */
  culvert: CulvertDetails | null;
  /** Newest first. */
  comments: StructureComment[];
  loadRating: LoadRatingDetails;
  /** Structures this one replaced. */
  replaced: StructureRef[];
  /** Structures that replaced this one. */
  replacedBy: StructureRef[];
  replacement: ReplacementDetails;
  /** Empty when the structure is complete. */
  outstanding: OutstandingItem[];
};
