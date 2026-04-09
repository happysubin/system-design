# Stock Decrement with Reserved Inventory Design

**Status:** Implemented design snapshot

## Implemented Behavior

The current branch now manages stock-backed inventory holds and SKU-level quantity mutation.

- payment start can create or reuse an `InventoryHold`
- payment start reserves stock from order items into `SkuStock.reserved`
- payment finalization can confirm or release a linked hold
- stale holds can expire and restore reserved stock

The implemented stock path now answers:

- what product was reserved
- how many units were reserved
- what should be decremented on successful payment

## Scope

Keep payment responsible for payment attempt lifecycle while inventory owns quantity mutation.

The current behavior is:

1. an order contains concrete items (`skuId`, quantity, price)
2. payment start reserves stock for those items
3. payment success confirms the hold and decrements real stock
4. payment failure or expiration releases reserved stock only

## Boundary Decision

Payment remains responsible for payment attempt lifecycle only.

- payment start flow starts payment
- inventory service reserves or reuses stock-backed holds
- payment stores `inventoryHoldId`
- payment finalization signals inventory to confirm or release the linked hold

Inventory owns all quantity mutations.

That means payment must **never** directly decrement `onHand` or recompute available stock.

## Stock Model

The stock aggregate uses explicit reserved quantity.

Example model:

- `SkuStock`
  - `skuId`
  - `onHand`
  - `reserved`

Derived value:

- `available = onHand - reserved`

Why this model:

- reserve = `reserved += quantity`
- release/expire = `reserved -= quantity`
- confirm = `reserved -= quantity` and `onHand -= quantity`

This is safer than a single `availableQuantity` field because the system can distinguish temporary reservation from actual depletion.

## Order and Hold Item Model

The implementation uses explicit order items and hold items.

- `OrderItem`
  - `orderId`
  - `skuId`
  - `quantity`
  - `unitPrice`

- `InventoryHoldItem`
  - `holdId`
  - `skuId`
  - `quantity`

Rules:

- payment start reads `OrderItem`s for the order
- inventory reservation creates or reuses the `InventoryHold`
- if creating a new hold, inventory also creates `InventoryHoldItem`s from the order snapshot
- finalization uses linked `InventoryHoldItem`s to know exactly what to decrement or release

## Reservation Rule

At payment start:

1. read order items
2. try to reuse an active hold for the same order
3. if no reusable hold exists, reserve stock item-by-item
4. create `InventoryHoldItem`s for the reserved quantities
5. link the resulting hold to the payment attempt

Reservation fails if any SKU does not have enough `available` stock.

## Finalization Rule

For the exact hold linked to the payment attempt:

- `DONE` → confirm hold
  - each hold item decrements `reserved`
  - each hold item decrements `onHand`
  - hold status becomes `CONFIRMED`

- `FAILED` / `CANCELLED` → release hold
  - each hold item decrements `reserved`
  - `onHand` stays unchanged
  - hold status becomes `RELEASED`

- expiration scheduler → expire hold
  - each hold item decrements `reserved`
  - `onHand` stays unchanged
  - hold status becomes `EXPIRED`

## Transaction Boundary

Inventory quantity mutation should happen inside inventory-owned transactional methods.

Current implementation keeps reservation, confirmation, release, and expiration inside inventory-owned transactional flows.

Each method should use guarded updates so duplicate webhook/reconcile/expiration execution cannot double-apply stock mutations.

## Concurrency Rule

Two concurrency problems matter here:

1. two payment starts trying to reserve the same SKU
2. two finalization paths trying to confirm or release the same hold

Protection should come from:

- guarded status transitions on `InventoryHold`
- conditional updates on `SkuStock`
- one active hold policy per order
- explicit `inventoryHoldId` linkage on `PaymentAttempt`

## Non-Goals for This Step

This implementation does **not** include:

- cart service design
- warehouse/location-specific inventory
- oversell backorder rules
- partial shipment or split fulfillment
- refund-driven restock logic

Those are still out of scope for the current implementation.

## Consequences

### Good

- real stock is finally modeled explicitly
- hold lifecycle and quantity lifecycle stay aligned
- payment success decrements stock exactly once
- failure/expiration restores reserved capacity without touching sold stock

### Trade-off

This adds several new persistence models and more cross-domain orchestration. That complexity is appropriate because inventory accounting is a separate source of truth from payment state.
