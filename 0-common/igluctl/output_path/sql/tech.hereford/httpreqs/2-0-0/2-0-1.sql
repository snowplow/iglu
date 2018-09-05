-- WARNING: only apply this file to your database if the following SQL returns the expected:
--
-- SELECT pg_catalog.obj_description(c.oid) FROM pg_catalog.pg_class c WHERE c.relname = 'tech_hereford_httpreqs_2';
--  obj_description
-- -----------------
--  iglu:tech.hereford/httpreqs/jsonschema/2-0-0
--  (1 row)

BEGIN TRANSACTION;

-- NO ADDED COLUMNS CAN BE EXPRESSED IN SQL MIGRATION

  COMMENT ON TABLE atomic.tech_hereford_httpreqs_2 IS 'iglu:tech.hereford/httpreqs/jsonschema/2-0-1';

END TRANSACTION;
