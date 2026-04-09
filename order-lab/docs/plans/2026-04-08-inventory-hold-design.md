# Inventory Hold on Payment Start Design

**Status:** Implemented in current branch

## Problem

The current `payment-lab` flow is payment-centric: an order helper creates `orderId`, `merchantOrderId`, and `checkoutKey`, then `POST /api/v1/payments` creates a `PaymentAttempt` and immediately starts PG approval. There is no inventory model, no stock reservation lifecycle, and no product or line-item concepts yet.

If inventory logic is added directly inside `PaymentApplicationService`, payment state transitions would start owning stock semantics. That would couple stock handling to PG-specific concerns such as `PENDING`, webhook lag, reconciliation, and duplicate callbacks.

## Design Goal

Reserve inventory when a payment request starts, reuse an existing active hold for the same order on retry, and finalize that hold only when payment reaches a final outcome.

## Boundary Decision

- **Payment domain keeps owning payment state only**: attempt creation, approve, webhook verification, reconcile, scheduler-based retry.
- **Inventory becomes a separate lifecycle** with its own entity and status enum.
- **Start-payment orchestration** is responsible for obtaining an inventory hold before payment approval begins.

In the current repo shape, the smallest insertion point is the beginning of `PaymentFacade.startPayment()`. Longer term, this should move to a dedicated checkout/start-payment orchestrator above `PaymentFacade`.

## Reservation Timing

- Reservation happens **when a payment request enters**.
- If an active hold already exists for the same `orderId`, the system **reuses** it instead of creating a new one.
- If no active hold exists, the system creates one.
- If hold creation/reuse fails because stock is unavailable, payment does not start.

## Inventory Hold Lifecycle

Introduce a separate `InventoryHoldStatus` enum, for example:

- `HELD`
- `CONFIRMED`
- `RELEASED`
- `EXPIRED`

Rules:

- `HELD` means stock is reserved for an order and may be reused by payment retries.
- `CONFIRMED` means payment succeeded and the hold is no longer releasable.
- `RELEASED` means payment failed/cancelled and the reserved stock is returned.
- `EXPIRED` means the hold timed out before final confirmation.

## Reuse Rule

- Reuse key: `orderId`
- Reuse condition: there is an existing hold with active state (`HELD`) and it is not expired.
- Retrying payment for the same order must not create a second active hold.

This matches the existing payment-side deduplication style, where the same `checkoutKey` reuses the existing `PaymentAttempt` instead of creating a duplicate.

## Finalization Rule

Payment result drives inventory finalization, but payment does not own inventory state.

- Payment `DONE` → inventory hold `CONFIRMED`
- Payment `FAILED` or `CANCELLED` → inventory hold `RELEASED`
- Payment still `PENDING` → inventory hold remains `HELD`

Because payment can be finalized through both webhook and reconcile paths, inventory finalization must be routed through a **single shared post-finalization handler** instead of duplicating release/confirm logic in two places.

## Timeout Handling

Inventory hold expiration is an inventory concern, not a payment enum concern.

- Holds should store `expiresAt`
- A background job should expire stale `HELD` rows
- Once expired, a later payment retry may create a fresh hold

`PaymentStatus.EXPIRED` should not be retained merely to model inventory timeout. Payment state and inventory hold state are separate lifecycles.

## Enum Cleanup Decision

The current codebase declares `AUTH_REQUESTED` and `EXPIRED` in `PaymentStatus`, but they are unused in the actual payment flow.

Recommended rule:

- Keep `PaymentStatus` focused on payment states only
- Add `InventoryHoldStatus` for hold lifecycle
- Remove unused payment enums only after verifying no existing DB rows depend on them

## Consequences

### Good

- Payment remains focused on PG and payment-attempt state transitions
- Inventory retry/release semantics are explicit
- Duplicate payment requests for the same order do not create duplicate holds
- Webhook and reconcile can share one finalization integration point

### Trade-off

The repo will gain a second lifecycle and more orchestration. That is intentional: inventory uncertainty and payment uncertainty should not be collapsed into one state machine.

## Implementation Notes

The implementation ended up slightly stronger than the original draft:

- `PaymentAttempt` now stores `inventoryHoldId` explicitly instead of resolving the target hold by `orderId` heuristics.
- start-payment orchestration acquires/reuses the hold first and then persists that exact hold id onto the payment attempt.
- finalization now targets the exact linked hold and fails loudly if the hold is missing, belongs to another order, or is no longer `HELD`.
- stale `HELD` holds are expired by a dedicated scheduler and are not reused afterward.
