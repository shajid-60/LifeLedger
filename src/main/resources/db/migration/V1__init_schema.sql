-- LifeLedger initial schema: 15 tables, from docs/erd.md (frozen Day 2).
-- Never edit this file once merged; later changes go in new timestamped migrations,
-- e.g. V2026_11_20_1430__m3_add_void_reason.sql
--
-- Conventions: snake_case, plural table names, BIGINT identity ids, FK columns <entity>_id,
-- every FK indexed. Tables mapped to BaseEntity carry created_at, updated_at, created_by.

-- =====================================================================
-- M1: Identity, Access & Administration
-- =====================================================================

CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    VARCHAR(255),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE roles (
    id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(30) NOT NULL,
    CONSTRAINT uk_roles_name UNIQUE (name)
);

-- M:M users <-> roles
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles (id),
    PRIMARY KEY (user_id, role_id)
);
CREATE INDEX ix_user_roles_role_id ON user_roles (role_id);

-- 1:1 users (shop manager) <-> repair_shops
CREATE TABLE repair_shops (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    manager_id  BIGINT       NOT NULL REFERENCES users (id),
    name        VARCHAR(150) NOT NULL,
    address     VARCHAR(255) NOT NULL,
    licence_no  VARCHAR(50)  NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    approved_by BIGINT REFERENCES users (id),
    approved_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(255),
    CONSTRAINT uk_repair_shops_manager_id UNIQUE (manager_id),
    CONSTRAINT uk_repair_shops_licence_no UNIQUE (licence_no),
    CONSTRAINT ck_repair_shops_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED'))
);
CREATE INDEX ix_repair_shops_approved_by ON repair_shops (approved_by);
CREATE INDEX ix_repair_shops_status ON repair_shops (status);

CREATE TABLE revoked_tokens (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    jti        VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_revoked_tokens_jti UNIQUE (jti)
);
CREATE INDEX ix_revoked_tokens_expires_at ON revoked_tokens (expires_at);

-- Insert-only audit trail
CREATE TABLE audit_events (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_id    BIGINT REFERENCES users (id),
    action      VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id   BIGINT,
    details     JSONB       NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_events_actor_id ON audit_events (actor_id);
CREATE INDEX ix_audit_events_entity ON audit_events (entity_type, entity_id);
CREATE INDEX ix_audit_events_occurred_at ON audit_events (occurred_at);

-- =====================================================================
-- M2: Product & Passport
-- =====================================================================

CREATE TABLE product_categories (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code                VARCHAR(30)  NOT NULL,
    name                VARCHAR(100) NOT NULL,
    validation_strategy VARCHAR(100) NOT NULL,
    CONSTRAINT uk_product_categories_code UNIQUE (code)
);

CREATE TABLE products (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_code   VARCHAR(32)  NOT NULL,
    serial_number VARCHAR(100) NOT NULL,
    brand         VARCHAR(100) NOT NULL,
    model         VARCHAR(100) NOT NULL,
    purchase_date DATE,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    owner_id      BIGINT       NOT NULL REFERENCES users (id),
    category_id   BIGINT       NOT NULL REFERENCES product_categories (id),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    VARCHAR(255),
    CONSTRAINT uk_products_public_code UNIQUE (public_code),
    CONSTRAINT ck_products_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);
CREATE INDEX ix_products_owner_id ON products (owner_id);
CREATE INDEX ix_products_category_id ON products (category_id);

CREATE TABLE warranties (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id BIGINT       NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    provider   VARCHAR(150) NOT NULL,
    start_date DATE         NOT NULL,
    end_date   DATE         NOT NULL,
    terms      TEXT,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by VARCHAR(255),
    CONSTRAINT ck_warranties_dates CHECK (end_date > start_date)
);
CREATE INDEX ix_warranties_product_id ON warranties (product_id);

-- M:M products <-> users: owner shares full history with a buyer until expires_at
CREATE TABLE product_access_grants (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id BIGINT      NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    grantee_id BIGINT      NOT NULL REFERENCES users (id),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(255)
);
CREATE INDEX ix_product_access_grants_product_id ON product_access_grants (product_id);
CREATE INDEX ix_product_access_grants_grantee_id ON product_access_grants (grantee_id);

-- =====================================================================
-- M3: Service History & Ownership
-- =====================================================================

CREATE TABLE service_records (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id     BIGINT        NOT NULL REFERENCES products (id),
    repair_shop_id BIGINT        NOT NULL REFERENCES repair_shops (id),
    service_date   DATE          NOT NULL,
    service_type   VARCHAR(20)   NOT NULL,
    description    TEXT          NOT NULL,
    cost           NUMERIC(10, 2),
    evidence_url   VARCHAR(500),
    voided         BOOLEAN       NOT NULL DEFAULT FALSE,
    void_reason    VARCHAR(500),
    voided_at      TIMESTAMPTZ,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by     VARCHAR(255),
    CONSTRAINT ck_service_records_type CHECK (service_type IN ('REPAIR', 'MAINTENANCE', 'INSPECTION')),
    CONSTRAINT ck_service_records_cost CHECK (cost IS NULL OR cost >= 0),
    CONSTRAINT ck_service_records_void_reason CHECK (voided = FALSE OR void_reason IS NOT NULL)
);
CREATE INDEX ix_service_records_product_id ON service_records (product_id);
CREATE INDEX ix_service_records_repair_shop_id ON service_records (repair_shop_id);

CREATE TABLE parts (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    part_number VARCHAR(100) NOT NULL,
    kind        VARCHAR(50)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(255),
    CONSTRAINT uk_parts_part_number UNIQUE (part_number)
);

-- M:M service_records <-> parts, with quantity and genuineness
CREATE TABLE service_record_parts (
    service_record_id BIGINT  NOT NULL REFERENCES service_records (id) ON DELETE CASCADE,
    part_id           BIGINT  NOT NULL REFERENCES parts (id),
    quantity          INTEGER NOT NULL,
    is_genuine        BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (service_record_id, part_id),
    CONSTRAINT ck_service_record_parts_quantity CHECK (quantity > 0)
);
CREATE INDEX ix_service_record_parts_part_id ON service_record_parts (part_id);

CREATE TABLE ownership_transfers (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id   BIGINT      NOT NULL REFERENCES products (id),
    from_user_id BIGINT      NOT NULL REFERENCES users (id),
    to_user_id   BIGINT      NOT NULL REFERENCES users (id),
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    accepted_at  TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    closed_at    TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   VARCHAR(255),
    CONSTRAINT ck_ownership_transfers_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'COMPLETED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_ownership_transfers_not_self CHECK (from_user_id <> to_user_id)
);
CREATE INDEX ix_ownership_transfers_product_id ON ownership_transfers (product_id);
CREATE INDEX ix_ownership_transfers_from_user_id ON ownership_transfers (from_user_id);
CREATE INDEX ix_ownership_transfers_to_user_id ON ownership_transfers (to_user_id);
-- A product has at most one open transfer
CREATE UNIQUE INDEX ux_ownership_transfers_open ON ownership_transfers (product_id)
    WHERE status IN ('PENDING', 'ACCEPTED');

CREATE TABLE ownership_history (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id BIGINT      NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    owner_id   BIGINT      NOT NULL REFERENCES users (id),
    owned_from TIMESTAMPTZ NOT NULL,
    owned_to   TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(255),
    CONSTRAINT ck_ownership_history_dates CHECK (owned_to IS NULL OR owned_to >= owned_from)
);
CREATE INDEX ix_ownership_history_product_id ON ownership_history (product_id);
CREATE INDEX ix_ownership_history_owner_id ON ownership_history (owner_id);
-- A product has exactly one current owner row
CREATE UNIQUE INDEX ux_ownership_history_current ON ownership_history (product_id)
    WHERE owned_to IS NULL;
