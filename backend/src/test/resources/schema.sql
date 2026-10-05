-- The entities are schema-qualified to THE, because in production the app connects as
-- PROXY_FSA_CBR_READ_WRITE_USER and owns nothing. The embedded test database has no such schema, so
-- Hibernate's create-drop has nowhere to put the tables unless it is made first. This runs before
-- the EntityManagerFactory is built, which is what makes it work.
CREATE SCHEMA IF NOT EXISTS THE;

-- Tables a structure delete clears but no entity maps: the delete reaches them with native SQL, so
-- Hibernate's create-drop does not make them. Only the columns the delete and its tests touch; the
-- real definitions are in nr-mof-db.
CREATE TABLE IF NOT EXISTS THE.FOREST_SERVICE_BRIDGE_PIER (
  FOREST_SERVICE_BRIDGE_PIER_ID NUMBER(10) NOT NULL PRIMARY KEY,
  FOREST_SERVICE_BRIDGE_ID      NUMBER(10) NOT NULL
);
CREATE TABLE IF NOT EXISTS THE.FOREST_SERVICE_BRIDGE_SPAN (
  FOREST_SERVICE_BRIDGE_SPAN_ID NUMBER(10) NOT NULL PRIMARY KEY,
  FOREST_SERVICE_BRIDGE_ID      NUMBER(10) NOT NULL
);
CREATE TABLE IF NOT EXISTS THE.STRUCTURE_COMMENT (
  STRUCTURE_COMMENT_ID  NUMBER(10) NOT NULL PRIMARY KEY,
  CROSSING_STRUCTURE_ID NUMBER(10)
);
CREATE TABLE IF NOT EXISTS THE.CROSSING_STRUCTURE_NAME_HIST (
  OLD_CROSSING_STRUCTURE_NAME VARCHAR2(240) NOT NULL,
  CROSSING_STRUCTURE_ID       NUMBER(10)
);
