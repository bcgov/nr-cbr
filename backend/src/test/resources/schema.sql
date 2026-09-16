-- The entities are schema-qualified to THE, because in production the app connects as
-- PROXY_FSA_CBR_READ_WRITE_USER and owns nothing. The embedded test database has no such schema, so
-- Hibernate's create-drop has nowhere to put the tables unless it is made first. This runs before
-- the EntityManagerFactory is built, which is what makes it work.
CREATE SCHEMA IF NOT EXISTS THE;
