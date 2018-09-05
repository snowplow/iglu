-- WARNING: only apply this file to your database if the following SQL returns the expected:
--
-- SELECT pg_catalog.obj_description(c.oid) FROM pg_catalog.pg_class c WHERE c.relname = 'tech_hereford_httpreqs_1';
--  obj_description
-- -----------------
--  iglu:tech.hereford/httpreqs/jsonschema/1-0-0
--  (1 row)

BEGIN TRANSACTION;

  ALTER TABLE atomic.tech_hereford_httpreqs_1
    ADD COLUMN "temp_ui_ds.adnxs.expires" TIMESTAMP ENCODE ZSTD;
  ALTER TABLE atomic.tech_hereford_httpreqs_1
    ADD COLUMN "temp_ui_ds.adnxs.uid" VARCHAR(4096) ENCODE ZSTD;
  ALTER TABLE atomic.tech_hereford_httpreqs_1
    ADD COLUMN "temp_ui_ds.openx.expires" TIMESTAMP ENCODE ZSTD;
  ALTER TABLE atomic.tech_hereford_httpreqs_1
    ADD COLUMN "temp_ui_ds.openx.uid" CHAR(36) ENCODE ZSTD;

  COMMENT ON TABLE atomic.tech_hereford_httpreqs_1 IS 'iglu:tech.hereford/httpreqs/jsonschema/1-0-1';

END TRANSACTION;
