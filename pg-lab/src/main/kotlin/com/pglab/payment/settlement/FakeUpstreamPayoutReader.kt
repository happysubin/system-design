package com.pglab.payment.settlement

import org.springframework.stereotype.Component
import java.time.OffsetDateTime

@Component
class FakeUpstreamPayoutReader : UpstreamPayoutReader {
    override fun check(bankTransferRequestId: String): UpstreamPayoutCheckResult =
        UpstreamPayoutCheckResult(
            status = UpstreamPayoutStatus.SUCCEEDED,
            bankTransferTransactionId = "confirmed-$bankTransferRequestId",
            checkedAt = OffsetDateTime.now(),
        )
}
