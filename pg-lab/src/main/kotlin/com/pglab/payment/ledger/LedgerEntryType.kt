package com.pglab.payment.ledger

/**
 * 원장 엔트리가 어떤 금전 사실을 기록하는지 분류하는 기준이다.
 *
 * 같은 주문과 같은 승인이라도 승인 확정, 취소, 환불, 수수료 인식, 정산 확정은
 * 서로 다른 회계적 사건이므로 append-only 원장에서는 별도 타입으로 남겨야 한다.
 * 이 enum은 그 사건의 종류를 고정하여 원장 집계와 후속 정산 로직의 기준점이 된다.
 *
 * - `AUTH_CAPTURED`: 승인 금액이 확정되어 원장에 기록된 상태
 * - `CANCELLED`: 승인 금액의 전체 또는 일부 취소 사실이 기록된 상태
 * - `REFUNDED`: 환불 사실이 기록된 상태
 * - `FEE_BOOKED`: 수수료가 비용 또는 차감 항목으로 계상된 상태
 * - `SETTLEMENT_CONFIRMED`: 정산 대상 금액이 확정 사실로 기록된 상태
 */
enum class LedgerEntryType {
    AUTH_CAPTURED,
    CANCELLED,
    REFUNDED,
    FEE_BOOKED,
    SETTLEMENT_CONFIRMED,
}
