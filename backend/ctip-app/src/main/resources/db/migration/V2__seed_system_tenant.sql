-- V2: public system tenant(docs/spec/04-data-dictionary.md 表 1;冪等)
INSERT INTO tenants (id, slug, name, type, status)
VALUES ('00000000-0000-0000-0000-000000000000', 'public', 'Public', 'SYSTEM', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

-- 不變量 T2 的 DB 層深度防禦(docs/spec/02-ddd-model.md;決策記錄:docs/architecture/decisions/0001):
-- public tenant 不可刪除、不可更名、不可變更 type。domain 層自 Phase 4 起同樣強制。
CREATE OR REPLACE FUNCTION protect_public_tenant() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.id = '00000000-0000-0000-0000-000000000000'::uuid THEN
        IF TG_OP = 'DELETE' THEN
            RAISE EXCEPTION 'public system tenant cannot be deleted';
        END IF;
        IF NEW.slug <> OLD.slug OR NEW.name <> OLD.name OR NEW.type <> OLD.type THEN
            RAISE EXCEPTION 'public system tenant cannot be renamed or retyped';
        END IF;
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_tenants_protect_public ON tenants;
CREATE TRIGGER trg_tenants_protect_public
    BEFORE UPDATE OR DELETE ON tenants
    FOR EACH ROW EXECUTE FUNCTION protect_public_tenant();
