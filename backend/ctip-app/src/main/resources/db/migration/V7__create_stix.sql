-- V7: stix_objects、stix_relationships(docs/spec/04-data-dictionary.md 表 8、9)
-- STIX 2.1 的衍生投影;domain model 才是 source of truth,本表可隨時重建。
CREATE TABLE stix_objects (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    stix_id         VARCHAR(128) NOT NULL,
    stix_type       VARCHAR(64)  NOT NULL,
    spec_version    VARCHAR(8)   NOT NULL DEFAULT '2.1',
    owner_tenant_id UUID         NOT NULL,
    indicator_id    UUID,
    threat_id       UUID,
    tlp             VARCHAR(16)  NOT NULL,
    stix_created    TIMESTAMPTZ  NOT NULL,
    stix_modified   TIMESTAMPTZ  NOT NULL,
    content         JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_stix_objects         PRIMARY KEY (id),
    CONSTRAINT ux_stix_objects_stix_id UNIQUE (stix_id),
    CONSTRAINT fk_so_tenant    FOREIGN KEY (owner_tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_so_indicator FOREIGN KEY (indicator_id) REFERENCES indicators(id) ON DELETE CASCADE,
    -- fk_so_threat 於 M2 的 V25__create_threats.sql 建立 threats 後補上(M1 不建 threats 表)
    CONSTRAINT ck_so_tlp       CHECK (tlp IN ('CLEAR','GREEN','AMBER','AMBER_STRICT','RED')),
    CONSTRAINT ck_so_origin    CHECK (
          (indicator_id IS NOT NULL AND threat_id IS NULL)
       OR (indicator_id IS NULL AND threat_id IS NOT NULL)
       OR (indicator_id IS NULL AND threat_id IS NULL))
);

CREATE INDEX ix_so_tenant_tlp ON stix_objects (owner_tenant_id, tlp);
CREATE INDEX ix_so_type       ON stix_objects (stix_type);
CREATE INDEX ix_so_indicator  ON stix_objects (indicator_id);

CREATE TABLE stix_relationships (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    stix_id           VARCHAR(128) NOT NULL,
    relationship_type VARCHAR(64)  NOT NULL,
    source_ref        VARCHAR(128) NOT NULL,
    target_ref        VARCHAR(128) NOT NULL,
    owner_tenant_id   UUID         NOT NULL,
    tlp               VARCHAR(16)  NOT NULL,
    stix_created      TIMESTAMPTZ  NOT NULL,
    stix_modified     TIMESTAMPTZ  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_stix_relationships PRIMARY KEY (id),
    CONSTRAINT ux_stix_rel_stix_id   UNIQUE (stix_id),
    CONSTRAINT ux_stix_rel_triple    UNIQUE (relationship_type, source_ref, target_ref),
    CONSTRAINT fk_sr_tenant  FOREIGN KEY (owner_tenant_id) REFERENCES tenants(id),
    CONSTRAINT ck_sr_tlp     CHECK (tlp IN ('CLEAR','GREEN','AMBER','AMBER_STRICT','RED')),
    CONSTRAINT ck_sr_no_self CHECK (source_ref <> target_ref)
);

CREATE INDEX ix_sr_source ON stix_relationships (source_ref);
CREATE INDEX ix_sr_target ON stix_relationships (target_ref);
