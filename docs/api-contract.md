# LifeLedger — API Contract

Status: **draft for team sign-off** (freeze by end of Day 2). After the freeze, a change needs a PR that updates this file.

10 controllers. Full CRUD on 4 entities: Product, Warranty, ServiceRecord, RepairShop. All paths start with `/api/v1`.

## Conventions

| Case | Response |
|---|---|
| Create | `201 Created` + `Location` header |
| Delete | `204 No Content` |
| Validation error | `400 Bad Request` |
| No or invalid token | `401 Unauthorized` |
| Wrong role or not the owner | `403 Forbidden` |
| Not found | `404 Not Found` |
| Duplicate or invalid state transition | `409 Conflict` |

- Errors are `ProblemDetail` (RFC 7807) from one `GlobalExceptionHandler`; no stack traces in responses.
- Lists are paged: `?page=0&size=20&sort=createdAt,desc`, returning Spring's `Page` JSON.
- Auth: `Authorization: Bearer <JWT>`, token valid 1 h.
- Dates are ISO-8601 (`2026-11-19`, `2026-11-19T10:15:30Z`).

Example error:

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Cannot complete a transfer in state PENDING",
  "instance": "/api/v1/ownership-transfers/42/complete"
}
```

---

## Member 1 — Identity, Access & Administration

| Controller | Method and path | Access | Notes |
|---|---|---|---|
| AuthController | `POST /auth/register` | Public | Chooses SELLER or BUYER; ADMIN is seeded, REPAIR_SHOP is granted on approval |
| AuthController | `POST /auth/login` | Public | Returns JWT (1 h) |
| AuthController | `POST /auth/logout` | Authenticated | Adds token `jti` to `revoked_tokens` |
| AuthController | `GET /auth/me`, `PUT /auth/me` | Authenticated | View or update own profile |
| RepairShopController | `POST /repair-shops` | SELLER, BUYER | Apply; status PENDING |
| RepairShopController | `GET /repair-shops`, `GET /repair-shops/{id}` | Public | Approved shops only |
| RepairShopController | `PUT /repair-shops/{id}` | REPAIR_SHOP (own) | Edit profile |
| RepairShopController | `DELETE /repair-shops/{id}` | ADMIN | Sets SUSPENDED, removes REPAIR_SHOP role |
| AdminController | `GET /admin/users`, `PATCH /admin/users/{id}/status`, `PUT /admin/users/{id}/roles` | ADMIN | Manage accounts |
| AdminController | `GET /admin/repair-shops?status=PENDING` | ADMIN | Review queue |
| AdminController | `POST /admin/repair-shops/{id}/approve`, `POST /admin/repair-shops/{id}/reject` | ADMIN | Approve grants REPAIR_SHOP |
| AdminController | `GET /admin/audit-events` | ADMIN | Filter by actor, entity, date |

## Member 2 — Product & Passport

| Controller | Method and path | Access | Notes |
|---|---|---|---|
| CategoryController | `GET /categories` | Public | LAPTOP, DESKTOP |
| ProductController | `POST /products` | SELLER | Strategy validates by category; generates `public_code` |
| ProductController | `GET /products?search=&category=` | SELLER | Own products, paged |
| ProductController | `GET /products/{id}`, `PUT /products/{id}` | Owner, ADMIN | |
| ProductController | `DELETE /products/{id}` | Owner | Deletes if no history, otherwise archives |
| ProductController | `GET /products/{id}/qr` | Owner | `image/png` QR pointing to the public passport URL |
| WarrantyController | `POST /products/{id}/warranties`, `GET /products/{id}/warranties` | Owner (GET also share holders) | |
| WarrantyController | `PUT /products/{id}/warranties/{wid}`, `DELETE /products/{id}/warranties/{wid}` | Owner | |
| PassportController | `POST /products/{id}/access-grants`, `GET /products/{id}/access-grants`, `DELETE /products/{id}/access-grants/{grantId}` | Owner | Share full history with a BUYER by email |
| PassportController | `GET /passport/{publicCode}` | Public | Limited view: model, category, record count and dates, verification labels; no serial, no owner |
| PassportController | `GET /passport/{publicCode}/full` | BUYER with active grant, owner, ADMIN | Full history |

## Member 3 — Service History & Ownership

| Controller | Method and path | Access | Notes |
|---|---|---|---|
| ServiceRecordController | `POST /products/{productId}/service-records` | REPAIR_SHOP (APPROVED) | With parts list; 403 if shop not approved |
| ServiceRecordController | `GET /products/{productId}/service-records` | Owner, share holder, ADMIN | Voided records shown with a label |
| ServiceRecordController | `GET /service-records/{id}`, `PUT /service-records/{id}` | Creating shop | PUT only within 24 h; audited |
| ServiceRecordController | `DELETE /service-records/{id}` | Creating shop, ADMIN | Voids with reason; never hard-deletes |
| PartController | `GET /parts` | Authenticated | Part catalogue |
| PartController | `POST /parts` | REPAIR_SHOP, ADMIN | Add a part |
| OwnershipController | `POST /ownership-transfers` | SELLER (current owner) | `productId`, `recipientEmail` → PENDING |
| OwnershipController | `GET /ownership-transfers?direction=incoming\|outgoing` | Authenticated | Own transfers only |
| OwnershipController | `POST /ownership-transfers/{id}/accept`, `POST /ownership-transfers/{id}/reject` | Recipient | |
| OwnershipController | `POST /ownership-transfers/{id}/cancel`, `POST /ownership-transfers/{id}/complete` | Sender | Complete runs in one transaction |
| OwnershipController | `GET /products/{id}/ownership-history` | Owner, share holder, ADMIN | |
| OwnershipController | `GET /products/{id}/timeline` | Owner, share holder, ADMIN | Service, ownership and warranty events merged by date |

### M3 request and response shapes

`POST /products/{productId}/service-records`

```json
{
  "serviceDate": "2026-11-19",
  "serviceType": "REPAIR",
  "description": "Replaced battery and cleaned fan",
  "cost": 4500.00,
  "evidenceUrl": "https://example.com/invoice-123.pdf",
  "parts": [
    { "partId": 3, "quantity": 1, "genuine": true }
  ]
}
```

Response `201`, `Location: /api/v1/service-records/17`:

```json
{
  "id": 17,
  "productId": 5,
  "repairShop": { "id": 2, "name": "FixIt Khulna" },
  "serviceDate": "2026-11-19",
  "serviceType": "REPAIR",
  "description": "Replaced battery and cleaned fan",
  "cost": 4500.00,
  "evidenceUrl": "https://example.com/invoice-123.pdf",
  "parts": [
    { "partId": 3, "name": "Battery 56Wh", "partNumber": "BAT-56", "quantity": 1, "genuine": true }
  ],
  "voided": false,
  "voidReason": null,
  "editableUntil": "2026-11-20T10:15:30Z",
  "createdAt": "2026-11-19T10:15:30Z"
}
```

`DELETE /service-records/{id}` body: `{ "reason": "Entered on the wrong product" }` → `204`. A missing reason → `400`.

`POST /parts`: `{ "name": "Battery 56Wh", "partNumber": "BAT-56", "kind": "BATTERY" }` → `201`; a duplicate `partNumber` → `409`.

`POST /ownership-transfers`

```json
{ "productId": 5, "recipientEmail": "buyer@example.com" }
```

Response `201`:

```json
{
  "id": 42,
  "productId": 5,
  "fromUser": { "id": 3, "name": "Seller One" },
  "toUser": { "id": 7, "name": "Buyer One" },
  "status": "PENDING",
  "createdAt": "2026-11-19T10:15:30Z",
  "acceptedAt": null,
  "completedAt": null
}
```

Errors: not the current owner → `403`; transfer to self → `400`; the product already has an open transfer → `409`; unknown recipient email → `404`.

`POST /ownership-transfers/{id}/accept|reject|cancel|complete` → `200` with the updated transfer; an invalid transition → `409`.

`GET /products/{id}/ownership-history` → `[{ "owner": { "id": 3, "name": "Seller One" }, "ownedFrom": "...", "ownedTo": null }]`

`GET /products/{id}/timeline` → events sorted by date, newest first:

```json
[
  { "date": "2026-11-19T10:15:30Z", "type": "SERVICE", "title": "REPAIR by FixIt Khulna", "refId": 17, "voided": false },
  { "date": "2026-10-01T00:00:00Z", "type": "OWNERSHIP", "title": "Ownership transferred", "refId": 42 },
  { "date": "2026-06-01T00:00:00Z", "type": "WARRANTY", "title": "Warranty started: Dell Care", "refId": 9 }
]
```

---

## Cross-module service interfaces

These are Java contracts, not HTTP endpoints. Each owner publishes the interface first; the others code against it.

```java
// M1 -> M3  (package com.lifeledger.repairshop)
public interface RepairShopQueryService {
    boolean isApproved(Long shopId);
    Optional<Long> findApprovedShopIdByManager(Long userId);
}

// M1 -> M2, M3  (package com.lifeledger.common.security)
public interface CurrentUserProvider {
    Long currentUserId();              // 401 if nobody is logged in
    AuthenticatedUser currentUser();   // id, email, roles
    boolean hasRole(String role);      // e.g. hasRole("ADMIN")
}
public record AuthenticatedUser(Long id, String email, Set<String> roles) {}

// M1 <- M2, M3  (package com.lifeledger.audit) — publish with ApplicationEventPublisher
public record AuditableEvent(Long actorId, String action, String entityType, Long entityId,
                             Map<String, Object> details, Instant occurredAt) {
    static AuditableEvent of(Long actorId, String action, String entityType, Long entityId,
                             Map<String, Object> details);   // sets occurredAt = now
}

// M2 -> M3  (package com.lifeledger.passport)
public interface ProductAccessService {
    boolean canViewHistory(Long productId, Long userId);
}

// M2, called by M3 when a transfer completes (same transaction)
ProductService.changeOwner(Long productId, Long newOwnerId);
AccessGrantService.revokeAll(Long productId);
```

## Sign-off

- [ ] Shahriar (M1)
- [ ] Ariful (M2)
- [ ] Majharul (M3)
