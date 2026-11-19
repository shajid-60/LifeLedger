# LifeLedger — Entity Relationship Diagram

Status: **draft for team sign-off** (freeze by end of Day 2). After the freeze, any change needs a new Flyway migration and a reviewed PR.

15 tables. Owners: **M1** Shahriar (identity), **M2** Ariful (product), **M3** Majharul (service & ownership).

```mermaid
erDiagram
    users ||--o{ user_roles : has
    roles ||--o{ user_roles : grants
    users ||--o| repair_shops : manages
    users ||--o{ repair_shops : approves
    users ||--o{ audit_events : performs

    product_categories ||--o{ products : classifies
    users ||--o{ products : "currently owns"
    products ||--o{ warranties : covers
    products ||--o{ product_access_grants : "shared via"
    users ||--o{ product_access_grants : receives

    products ||--o{ service_records : "serviced in"
    repair_shops ||--o{ service_records : performs
    service_records ||--o{ service_record_parts : uses
    parts ||--o{ service_record_parts : "used in"

    products ||--o{ ownership_transfers : "transferred by"
    users ||--o{ ownership_transfers : sends
    users ||--o{ ownership_transfers : receives
    products ||--o{ ownership_history : "owned in"
    users ||--o{ ownership_history : "owned by"

    users {
        bigint id PK
        varchar name
        varchar email UK
        varchar password_hash "BCrypt"
        boolean enabled
        timestamptz created_at
        timestamptz updated_at
        varchar created_by
    }
    roles {
        bigint id PK
        varchar name UK "ADMIN SELLER BUYER REPAIR_SHOP"
    }
    user_roles {
        bigint user_id PK,FK
        bigint role_id PK,FK
    }
    repair_shops {
        bigint id PK
        bigint manager_id FK,UK "1:1 users"
        varchar name
        varchar address
        varchar licence_no UK
        varchar status "PENDING APPROVED REJECTED SUSPENDED"
        bigint approved_by FK "users"
        timestamptz approved_at
        timestamptz created_at
        timestamptz updated_at
        varchar created_by
    }
    revoked_tokens {
        bigint id PK
        varchar jti UK
        timestamptz expires_at "purged by scheduled job"
        timestamptz revoked_at
    }
    audit_events {
        bigint id PK
        bigint actor_id FK "users, nullable for system"
        varchar action
        varchar entity_type
        bigint entity_id
        jsonb details
        timestamptz occurred_at "insert-only table"
    }
    product_categories {
        bigint id PK
        varchar code UK "LAPTOP DESKTOP"
        varchar name
        varchar validation_strategy "Strategy bean key"
    }
    products {
        bigint id PK
        varchar public_code UK "random, used in QR"
        varchar serial_number "private"
        varchar brand
        varchar model
        date purchase_date
        varchar status "ACTIVE ARCHIVED"
        bigint owner_id FK "users"
        bigint category_id FK
        timestamptz created_at
        timestamptz updated_at
        varchar created_by
    }
    warranties {
        bigint id PK
        bigint product_id FK
        varchar provider
        date start_date
        date end_date "must be after start_date"
        text terms
        timestamptz created_at
        timestamptz updated_at
        varchar created_by
    }
    product_access_grants {
        bigint id PK
        bigint product_id FK
        bigint grantee_id FK "users, must be BUYER"
        timestamptz expires_at
        timestamptz revoked_at
        timestamptz created_at
        timestamptz updated_at
        varchar created_by
    }
    service_records {
        bigint id PK
        bigint product_id FK
        bigint repair_shop_id FK
        date service_date
        varchar service_type "REPAIR MAINTENANCE INSPECTION"
        text description
        numeric cost "10,2"
        varchar evidence_url "URL, no uploads"
        boolean voided
        varchar void_reason
        timestamptz voided_at
        timestamptz created_at "edit window = created_at + 24h"
        timestamptz updated_at
        varchar created_by
    }
    parts {
        bigint id PK
        varchar name
        varchar part_number UK
        varchar kind
        timestamptz created_at
        timestamptz updated_at
        varchar created_by
    }
    service_record_parts {
        bigint service_record_id PK,FK
        bigint part_id PK,FK
        int quantity "greater than 0"
        boolean is_genuine
    }
    ownership_transfers {
        bigint id PK
        bigint product_id FK
        bigint from_user_id FK "sender"
        bigint to_user_id FK "recipient"
        varchar status "PENDING ACCEPTED COMPLETED REJECTED CANCELLED"
        timestamptz accepted_at
        timestamptz completed_at
        timestamptz closed_at "rejected or cancelled"
        timestamptz created_at
        timestamptz updated_at
        varchar created_by
    }
    ownership_history {
        bigint id PK
        bigint product_id FK
        bigint owner_id FK "users"
        timestamptz owned_from
        timestamptz owned_to "null = current owner"
        timestamptz created_at
        timestamptz updated_at
        varchar created_by
    }
```

## Relationship coverage (rubric: entity relationships, 10 marks)

| Type | Example |
|---|---|
| 1:M | `product_categories` → `products`, `products` → `warranties`, `repair_shops` → `service_records` |
| M:1 | `products` → `users` (owner), `service_records` → `products` |
| 1:1 | `users` ↔ `repair_shops` (shop manager, unique `manager_id`) |
| M:M | `users` ↔ `roles` via `user_roles`; `service_records` ↔ `parts` via `service_record_parts` (with `quantity`, `is_genuine`); `products` ↔ `users` via `product_access_grants` |

## Constraints and indexes

- Unique: `users.email`, `roles.name`, `products.public_code`, `product_categories.code`, `repair_shops.manager_id`, `repair_shops.licence_no`, `parts.part_number`, `revoked_tokens.jti`.
- Index every foreign key column.
- Partial unique index, so a product has at most one open transfer:
  `CREATE UNIQUE INDEX ux_open_transfer ON ownership_transfers(product_id) WHERE status IN ('PENDING','ACCEPTED');`
- Partial unique index, so a product has exactly one current owner row:
  `CREATE UNIQUE INDEX ux_current_owner ON ownership_history(product_id) WHERE owned_to IS NULL;`
- Checks: `service_record_parts.quantity > 0`, `warranties.end_date > start_date`, `ownership_transfers.from_user_id <> to_user_id`, `voided = false OR void_reason IS NOT NULL`.

## Rules

- **Naming:** snake_case, plural table names, `id BIGINT GENERATED ALWAYS AS IDENTITY`, FKs named `<entity>_id` (role-named FKs such as `from_user_id`, `approved_by` where one table references `users` twice).
- **Auditing columns:** `created_at`, `updated_at`, `created_by` via the shared `BaseEntity` and JPA auditing, on every table except the reference tables (`roles`, `product_categories`), the join tables (`user_roles`, `service_record_parts`) and the log tables (`revoked_tokens`, `audit_events`). The schema itself is `src/main/resources/db/migration/V1__init_schema.sql`.
- **History is never overwritten:** service records are voided, not deleted; ownership history rows are closed with `owned_to`; audit events are insert-only.
- **Privacy:** `serial_number` and the owner never appear in the public passport DTO.
