# LifeLedger — Use-Case Diagram

Actors: **Visitor** (not logged in), **Buyer**, **Seller** (product owner), **Repair Shop** (approved shop), **Admin**. Buyer, Seller and Repair Shop are all registered users; product ownership is checked per record, not by role.

```mermaid
flowchart LR
    visitor(["👤 Visitor"])
    buyer(["👤 Buyer"])
    seller(["👤 Seller"])
    shop(["👤 Repair Shop"])
    admin(["👤 Admin"])

    subgraph sys["LifeLedger"]
        direction TB
        subgraph id["Identity & Admin (M1)"]
            uc1(["Register / log in / log out"])
            uc2(["Manage own profile"])
            uc3(["Apply as repair shop"])
            uc4(["Approve / reject / suspend shop"])
            uc5(["Manage users and roles"])
            uc6(["View audit log"])
        end
        subgraph prod["Product & Passport (M2)"]
            uc7(["Register product"])
            uc8(["Manage products & warranties"])
            uc9(["Get QR code"])
            uc10(["View public passport"])
            uc11(["Share full history with buyer"])
            uc12(["View full history"])
        end
        subgraph svc["Service & Ownership (M3)"]
            uc13(["Add service record with parts"])
            uc14(["Edit record within 24 h / void record"])
            uc15(["View product timeline"])
            uc16(["Initiate / cancel / complete transfer"])
            uc17(["Accept / reject transfer"])
            uc18(["View ownership history"])
        end
    end

    visitor --- uc1
    visitor --- uc10

    buyer --- uc1
    buyer --- uc2
    buyer --- uc3
    buyer --- uc12
    buyer --- uc17
    buyer --- uc15

    seller --- uc2
    seller --- uc3
    seller --- uc7
    seller --- uc8
    seller --- uc9
    seller --- uc11
    seller --- uc15
    seller --- uc16
    seller --- uc18

    shop --- uc13
    shop --- uc14

    admin --- uc4
    admin --- uc5
    admin --- uc6
    admin --- uc12
    admin --- uc14
```

## Use-case notes

| Use case | Actor | Rule |
|---|---|---|
| Register | Visitor | Chooses SELLER or BUYER. ADMIN is seeded; REPAIR_SHOP is granted on approval. |
| Apply as repair shop | Seller, Buyer | Creates a PENDING shop; Admin approval grants REPAIR_SHOP. |
| Add service record | Repair Shop | Only APPROVED shops; otherwise 403. |
| Edit / void record | Repair Shop (creator), Admin | Edit only within 24 h; deletion voids with a reason, never hard-deletes. |
| View public passport | Anyone | Model, category, record count and dates, verification labels; no serial number, no owner. |
| View full history / timeline | Owner, Buyer with active grant, Admin | Service, ownership and warranty events merged by date. |
| Initiate transfer | Seller (current owner) | Recipient by email; cannot transfer to self; one open transfer per product. |
| Accept / reject transfer | Recipient | Only from PENDING. |
| Complete transfer | Seller (sender) | Only from ACCEPTED; owner changes, shares revoked, recipient gains SELLER. |
