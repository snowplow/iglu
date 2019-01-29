CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TYPE key_action AS ENUM ('create', 'delete');
CREATE TYPE schema_action AS ENUM ('read', 'bump', 'create', 'create_vendor');

CREATE TABLE permissions (
    apikey              UUID            NOT NULL,
    vendor              VARCHAR(128)    NOT NULL,
    key_action          key_action[]    NOT NULL,
    schema_action       schema_action,
    PRIMARY KEY (apikey)
);

-- Examples
-- INSERT INTO permissions VALUES (uuid_generate_v4(), 'com.acme', '{create}', NULL);
-- INSERT INTO permissions VALUES (uuid_generate_v4(), 'com.acme.bumpers', '{create, delete}', 'bump');
