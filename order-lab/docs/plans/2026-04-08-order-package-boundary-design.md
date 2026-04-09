# Order Package Boundary Cleanup Design

**Status:** Implemented package boundary

## Problem

The codebase previously had a mixed package boundary around order concepts.

Before cleanup:

- `OrderItem` and `OrderItemRepository` already lived under `com.paymentlab.order.*`
- but the aggregate root `Order` still lived under `com.paymentlab.payment.domain`
- `OrderRepository`, `OrderApplicationService`, `CreateOrderRequest/Response`, and `OrderController` also still lived under `com.paymentlab.payment.*`

That shape made the package structure misleading. Stock reservation, hold items, and local order creation already treated order as its own concept, but the aggregate and API boundary still looked payment-owned.

## Goal

Keep order-related aggregate, persistence, service, controller, and DTO code under `com.paymentlab.order.*` so package boundaries match the actual domain boundaries.

## Boundary Decision

Current boundary after cleanup:

- `order` owns order aggregate and order-item composition
- `payment` owns payment attempt lifecycle, PG integration, webhook handling, reconcile, and payment finalization
- `inventory` owns hold and stock mutation lifecycle

This does **not** change runtime behavior. It is a package-boundary refactor that makes later feature work safer and more readable.

## Current Package Layout

These classes now live together under `order.*`:

- `com.paymentlab.order.domain.Order`
- `com.paymentlab.order.infrastructure.persistence.OrderRepository`
- `com.paymentlab.order.application.OrderApplicationService`
- `com.paymentlab.order.api.OrderController`
- `com.paymentlab.order.api.dto.CreateOrderRequest`
- `com.paymentlab.order.api.dto.CreateOrderResponse`
- `com.paymentlab.order.domain.OrderItem`
- `com.paymentlab.order.infrastructure.persistence.OrderItemRepository`

## Why They Live Together

Leaving only part of the order code in `order` while repository, service, controller, or DTO code stayed in `payment` would keep an awkward mixed boundary.

Keeping the full set together gives one clear result:

- everything for local order creation and order composition is under `order.*`
- payment starts from an existing order instead of pretending to own order creation

## Current Dependency Shape

- `inventory` depends on `order.*` for order-side references such as `OrderItemRepository`
- `payment` depends on an existing order from `order.*` where necessary
- local order creation lives under `order.*`, but the current code still uses `payment.infrastructure.redis.CheckoutKeyStore` to issue `checkoutKey`

This dependency direction matches current business meaning:

- payment starts from an existing order
- inventory reserves stock for an existing order through `order.*`
- order itself does not own PG, webhook, or payment finalization, even though local order creation still shares the current checkout key store implementation

## Testing Impact

No behavioral contract should change, but many tests will need import/package updates.

Most likely affected:

- `OrderApplicationServiceTest`
- `OrderApiTest`
- `OrderItemPersistenceTest`
- inventory tests importing `payment.domain.Order`
- payment structure tests asserting old class names

The important rule is to update tests to verify the **same behavior** under the new package boundary, not to change test intent.

## Documentation Impact

README should describe the local order creation API as belonging to the `order` side of the monolith, while keeping the note that the main payment flow still starts from an existing order.

## Consequences

### Good

- package names match real domain ownership
- future work like active hold constraints can depend on `order` cleanly
- stock and order relationships become easier to reason about
- fewer mixed imports between `payment` and `order`

### Trade-off

This refactor touches many imports and test files without changing user-visible behavior. That is acceptable because it removes a misleading package boundary before more functionality is layered on top.
