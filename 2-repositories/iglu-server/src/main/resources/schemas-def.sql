CREATE TABLE schemas (
    vendor      VARCHAR(128)    NOT NULL,
    name        VARCHAR(128)    NOT NULL,
    format      VARCHAR(128)    NOT NULL,
    model       INTEGER         NOT NULL,
    revision    INTEGER         NOT NULL,
    addition    INTEGER         NOT NULL,

    created_at  TIMESTAMP       NOT NULL,
    updated_at  TIMESTAMP       NOT NULL,
    is_public   BOOLEAN         NOT NULL,
    body        JSON            NOT NULL
)