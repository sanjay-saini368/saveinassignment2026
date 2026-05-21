# Loan Repayment & Allocation Engine

A production-grade backend service built with **Java 17 + Spring Boot 3** that handles loan repayments and allocates payments across EMI schedules and loan-level charges with full auditability.


---

## Tech Stack

| Layer          | Technology                          |
|----------------|-------------------------------------|
| Language       | Java 17                             |
| Framework      | Spring Boot 3.2.5                   |
| Database       | PostgreSQL 15                       |
| ORM            | Spring Data JPA / Hibernate         |
| API Docs       | SpringDoc OpenAPI (Swagger UI)      |
| Testing        | JUnit 5, Mockito, AssertJ           |
| Build Tool     | Maven                               |


---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                    REST Controllers                      │
│          LoanController  │  RepaymentController         │
└─────────────────┬────────┴────────────┬─────────────────┘
                  │                     │
┌─────────────────▼─────────────────────▼─────────────────┐
│                    Service Layer                         │
│          LoanServiceImpl  │  RepaymentServiceImpl       │
└─────────────────┬─────────────────────┬─────────────────┘
                  │                     │
┌─────────────────▼─────────────────────▼─────────────────┐
│              Allocation Strategy (Strategy Pattern)      │
│     CipAllocationStrategy (default: Charges→Int→Prin)   │
│     IpcAllocationStrategy        (Int→Prin→Charges)     │
│     PicAllocationStrategy        (Prin→Int→Charges)     │
└─────────────────────────────────────────────────────────┘
                  │
┌─────────────────▼─────────────────────────────────────┐
│                   Repository Layer                     │
│   LoanRepo │ EmiScheduleRepo │ RepaymentRepo │ ...    │
└─────────────────────────────────────────────────────────┘
                  │
┌─────────────────▼─────────────────────────────────────┐
│               PostgreSQL Database                      │
│  loans │ emi_schedules │ loan_charges │ repayments    │
│                  repayment_allocations                 │
└─────────────────────────────────────────────────────────┘
```

### Key Design Patterns

- **Strategy Pattern** — Pluggable allocation strategies (CIP / IPC / PIC) resolved at runtime via `AllocationStrategyFactory`
- **Immutable Ledger** — `repayment_allocations` is append-only; reversals write compensating negative entries
- **Pessimistic Locking** — `SELECT ... FOR UPDATE` on the `loans` row prevents concurrent repayments from corrupting balances
- **Optimistic Locking** — `@Version` on all mutable entities guards against stale reads
- **Idempotency** — `referenceId` uniqueness check before any processing; duplicates return the existing record without side effects

---

## Setup Instructions

### Prerequisites

- Java 17+
- Maven 3.8+
- PostgreSQL



### Configure the Database

The default connection is:

```
Host:     localhost
Port:     5432
Database: loandb
Username: postgres
Password: postgres
```


The application starts on **http://localhost:8080**.

### Swagger UI

```
http://localhost:8080/swagger-ui.html
```

### Actuator Health

```
http://localhost:8080/actuator/health
```

---

## API Reference

### Loan APIs

#### Create Loan

```http
POST /loans
Content-Type: application/json

{
  "loanId": "LN001",
  "borrowerName": "John Doe",
  "principalAmount": 90000,
  "interestAmount": 18000,
  "tenureMonths": 6,
  "allocationStrategy": "CIP",
  "emiSchedules": [
    { "principalDue": 15000, "interestDue": 3000, "dueDate": "2026-06-05" },
    { "principalDue": 15000, "interestDue": 3000, "dueDate": "2026-07-05" },
    { "principalDue": 15000, "interestDue": 3000, "dueDate": "2026-08-05" },
    { "principalDue": 15000, "interestDue": 3000, "dueDate": "2026-09-05" },
    { "principalDue": 15000, "interestDue": 3000, "dueDate": "2026-10-05" },
    { "principalDue": 15000, "interestDue": 3000, "dueDate": "2026-11-05" }
  ]
}
```

#### Get Loan Summary

```http
GET /loans/{loanId}
```

Returns total/outstanding principal, interest, charges, overdue amount, and next EMI due.

#### Get EMI Schedules

```http
GET /loans/{loanId}/schedules
```

Returns per-EMI breakdown of due, paid, and outstanding amounts. **Charges never appear here.**

#### Add Loan Charge

```http
POST /loans/{loanId}/charges
Content-Type: application/json

{
  "chargeType": "BOUNCE_CHARGE",
  "amount": 500,
  "remarks": "Payment bounce on 2026-05-01"
}
```

Supported charge types: `BOUNCE_CHARGE`, `PENAL_CHARGE`, `PROCESSING_FEE`, `PREPAYMENT_CHARGE`, `OTHER`

#### Get Charges

```http
GET /loans/{loanId}/charges
```

---

### Repayment APIs

#### Process Repayment

```http
POST /repayments
Content-Type: application/json

{
  "loanId": "LN001",
  "amount": 10000,
  "paymentDate": "2026-05-20T10:00:00",
  "referenceId": "PAY123"
}
```

- **Idempotent**: Sending the same `referenceId` twice returns the original result without reprocessing.
- Allocation follows the strategy configured on the loan (`CIP` by default).

#### Reverse Repayment

```http
POST /repayments/{referenceId}/reverse
Content-Type: application/json

{
  "reason": "Customer requested refund"
}
```

Reverses all allocations made by the original repayment, restores EMI and charge balances, and marks the repayment as `REVERSED`.

#### Get Repayment Details

```http
GET /repayments/{referenceId}
```

Returns the repayment with its full allocation ledger.

---

## Allocation Strategy

### How It Works

When a repayment is received, the engine allocates funds across loan components in a configured order. EMIs are always processed **chronologically** (earliest due date first). Within each EMI, both interest and principal are tracked separately.

### Available Strategies

| Strategy | Order                              | When to Use                                     |
|----------|------------------------------------|-------------------------------------------------|
| **CIP**  | Charges → Interest → Principal     | Default. Clears penalty charges first to stop accrual |
| **IPC**  | Interest → Principal → Charges     | When reducing outstanding principal is priority |
| **PIC**  | Principal → Interest → Charges     | Pre-payment focused repayments                  |

The strategy is set **per loan** at creation and drives every repayment on that loan.

### Partial Adjustment

If the repayment amount is less than what's due, funds are exhausted in strategy order. Outstanding balances on remaining components/EMIs are left unchanged.

### Overpayment

If the repayment amount exceeds total outstanding (principal + interest + charges), the surplus is returned in the response as `unallocatedAmount`. No additional allocation is made.

### Allocation Ledger

Every rupee allocated is recorded as an immutable entry in `repayment_allocations`:

| repayment_id | entity_type  | entity_id   | component | amount |
|--------------|--------------|-------------|-----------|--------|
| PAY123       | LOAN_CHARGE  | CHG-1       | CHARGE    | 500    |
| PAY123       | EMI          | LN001-EMI-1 | INTEREST  | 3000   |
| PAY123       | EMI          | LN001-EMI-1 | PRINCIPAL | 5500   |

Reversals write compensating **negative** entries — original entries are never mutated.

---

## Database Schema

### Entity Relationship Diagram

```
loans
  │
  ├── emi_schedules         (one loan → many EMIs)
  │     principal_due, principal_paid
  │     interest_due,  interest_paid
  │     due_date, status
  │
  ├── loan_charges          (one loan → many charges; NEVER inside EMI)
  │     charge_type, amount, amount_paid, status
  │
  └── repayments            (one loan → many repayments)
        │
        └── repayment_allocations   (immutable ledger; one repayment → many entries)
              entity_type, entity_id, component, amount
```

### Key Tables

**`loans`** — Master loan record with version (optimistic lock) and allocation strategy.

**`emi_schedules`** — Tracks only `principal` and `interest`. No charges stored here.

**`loan_charges`** — Loan-level charges (bounce, penal, etc.) maintained independently of EMI schedules.

**`repayments`** — One record per payment event. `reference_id` is unique for idempotency.

**`repayment_allocations`** — Append-only ledger. `@Immutable` in Hibernate. Negative amounts represent reversals.

### Important Indexes

```sql
-- Fast loan lookup
CREATE UNIQUE INDEX idx_loan_loan_id       ON loans (loan_id);
-- Idempotency check
CREATE UNIQUE INDEX idx_repayment_ref_id   ON repayments (reference_id);
-- Chronological EMI allocation
CREATE INDEX        idx_emi_due_date       ON emi_schedules (due_date);
-- Audit/reconciliation queries
CREATE INDEX        idx_alloc_entity       ON repayment_allocations (entity_type, entity_id);
CREATE INDEX        idx_alloc_loan_id      ON repayment_allocations (loan_id);
```


## Architecture Decisions

### Strategy Pattern for Allocation

Allocation order is a business rule likely to change per product or lender. Using the Strategy Pattern keeps each ordering isolated, independently testable, and adds new strategies without touching core service logic.

### Pessimistic Locking on Loan Row

Concurrent repayments on the same loan could cause a race condition where two transactions each read the same EMI balance and both over-allocate. A `SELECT ... FOR UPDATE` on the `loans` row serialises repayment processing per loan while allowing full parallelism across different loans.

### Immutable Ledger for Allocations

`repayment_allocations` is never updated. Reversals write negative compensating entries. This:
- Provides a complete audit trail (double-entry bookkeeping style)
- Makes reconciliation straightforward: `SUM(amount)` per entity gives current paid amount
- Prevents data loss from update bugs

### Flyway for Migrations

Schema changes are version-controlled alongside code, enabling reproducible deployments and rollback capability.

### `@Version` (Optimistic Locking) on Entities

Applied to `loans`, `emi_schedules`, and `loan_charges` as a second safety net against stale reads — particularly useful for charge updates that happen outside the main repayment flow.

---

## Tradeoffs

| Decision | Benefit | Tradeoff |
|---|---|---|
| Pessimistic lock on loan row | Eliminates concurrency bugs | Serialises repayments per loan; throughput scales horizontally across loans, not within one loan |
| Immutable allocations ledger | Full audit trail, no data loss | Storage grows with every repayment; periodic archival needed at scale |
| Strategy per loan (not per repayment) | Simple, consistent behaviour | Cannot change strategy mid-loan without a migration |
| Synchronous processing | Simple, debuggable, transactional | Not suitable for very high throughput without async/queue layer |
| H2 for tests | Fast CI, no external dependency | Minor SQL dialect differences; production migrations use PostgreSQL dialect |

---

## Scalability Considerations

### Horizontal Scaling

The service is stateless — multiple instances can run behind a load balancer. Pessimistic locking ensures correctness because the lock is held at the **database** level, not the application level.

### High-Throughput Repayments

For extreme throughput requirements:
- Move repayment processing to an **async queue** (Kafka / RabbitMQ) with idempotent consumers
- Use a **dead-letter queue** for failed allocations with retry logic
- The `referenceId` idempotency key makes retry-safe consumers straightforward to implement

### Partitioning

The `repayment_allocations` table will grow unboundedly. Partition by `created_at` (monthly) and archive old partitions to cold storage for reconciliation.

### Caching

Loan metadata (strategy, tenure) can be cached with short TTL. EMI and charge balances must be read from the DB within a transaction to ensure accuracy.

### Read Replicas

Summary/reporting APIs (`GET /loans/{id}`, `GET /loans/{id}/schedules`) are annotated `@Transactional(readOnly = true)` and can be routed to a read replica with a routing datasource.

### Future Enhancements

- **Event Sourcing** — emit `RepaymentProcessed` / `RepaymentReversed` domain events for downstream consumers (notifications, analytics)
- **Reconciliation Jobs** — scheduled job to recompute outstanding balances from the ledger and flag discrepancies
- **Multi-tenancy** — add `tenant_id` column and row-level security for SaaS deployments
- **Metrics** — Micrometer + Prometheus for repayment latency, allocation rate, and error rate dashboards

---

## Running Tests

```bash
# All tests
mvn test

# Specific test class
mvn test -Dtest=CipAllocationStrategyTest

# With coverage report
mvn test jacoco:report
# Open: target/site/jacoco/index.html
```

Tests use **H2 in-memory** (PostgreSQL compatibility mode) — no external database required.

### Test Coverage

| Area | Tests |
|---|---|
| CIP strategy | Full payment, partial, overpayment, multi-EMI, ledger entries, zero payment |
| IPC / PIC strategies | Order verification, charges paid last/first |
| Strategy factory | All three strategies resolved correctly |
| LoanService | Create, duplicate ID, not found, outstanding calculation, closed loan charge |
| RepaymentService | Idempotency, closed loan, successful processing, double reversal, loan closure |

---

## Sample Scenario Walkthrough

### Setup

**Loan:** 90,000 principal, 18,000 interest, 6 EMIs of 15,000 + 3,000 each

**Charges added:**
- CHG-1: BOUNCE_CHARGE — ₹500
- CHG-2: PENAL_CHARGE — ₹1,000

**Repayment received:** ₹10,000 with strategy `CIP`

### Allocation (CIP: Charges → Interest → Principal)

| Step | Target  | Component | Amount   | Remaining |
|------|---------|-----------|----------|-----------|
| 1    | CHG-1   | CHARGE    | ₹500     | ₹9,500    |
| 2    | CHG-2   | CHARGE    | ₹1,000   | ₹8,500    |
| 3    | EMI-1   | INTEREST  | ₹3,000   | ₹5,500    |
| 4    | EMI-1   | PRINCIPAL | ₹5,500   | ₹0        |

### Resulting State

| Entity | Component  | Due    | Paid   | Outstanding |
|--------|------------|--------|--------|-------------|
| CHG-1  | CHARGE     | ₹500   | ₹500   | ₹0          |
| CHG-2  | CHARGE     | ₹1,000 | ₹1,000 | ₹0          |
| EMI-1  | INTEREST   | ₹3,000 | ₹3,000 | ₹0          |
| EMI-1  | PRINCIPAL  | ₹15,000| ₹5,500 | ₹9,500      |
| EMI-2–6| (all)     | ...    | ₹0     | full amount |

### Reversal

Calling `POST /repayments/PAY123/reverse` restores all balances exactly:
- CHG-1 outstanding → ₹500
- CHG-2 outstanding → ₹1,000
- EMI-1 interest outstanding → ₹3,000
- EMI-1 principal outstanding → ₹15,000

The original `PAY123` is marked `REVERSED`. Compensating negative ledger entries are written for full traceability.



<img width="1916" height="1080" alt="Screenshot from 2026-05-21 12-02-11" src="https://github.com/user-attachments/assets/47af53f7-d9ad-4adc-978e-6f2b0a5f0998" />

<img width="1916" height="1080" alt="Screenshot from 2026-05-21 12-03-14" src="https://github.com/user-attachments/assets/afab229e-a8c0-4f33-9616-d75006205477" />

<img width="1916" height="1080" alt="Screenshot from 2026-05-21 12-03-21" src="https://github.com/user-attachments/assets/6f5133b5-9935-4cd3-ad47-86837e8f8ea5" />

<img width="1916" height="1080" alt="Screenshot from 2026-05-21 12-03-30" src="https://github.com/user-attachments/assets/2a6ccd5f-ae51-41a3-b754-3f043b80aad6" />

<img width="1916" height="1080" alt="Screenshot from 2026-05-21 12-03-59" src="https://github.com/user-attachments/assets/3ffad7a1-bf15-4d05-8021-da0708952d33" />


