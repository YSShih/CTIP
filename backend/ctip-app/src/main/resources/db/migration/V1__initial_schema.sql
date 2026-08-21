-- V1: extension + tenants(docs/spec/04-data-dictionary.md 表 1)
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE tenants (
    id         UUID         NOT NULL DEFAULT gen_random_uuid(),
    slug       VARCHAR(64)  NOT NULL,
    name       VARCHAR(255) NOT NULL,
    type       VARCHAR(32)  NOT NULL,
    status     VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_tenants             PRIMARY KEY (id),
    CONSTRAINT ux_tenants_slug        UNIQUE (slug),
    CONSTRAINT ck_tenants_type        CHECK (type IN ('SYSTEM','INDIVIDUAL','ORGANIZATION','ENTERPRISE')),
    CONSTRAINT ck_tenants_status      CHECK (status IN ('ACTIVE','SUSPENDED')),
    CONSTRAINT ck_tenants_slug_format CHECK (slug ~ '^[a-z0-9][a-z0-9-]{0,62}[a-z0-9]$')
);
