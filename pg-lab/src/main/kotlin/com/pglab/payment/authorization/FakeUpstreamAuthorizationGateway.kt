package com.pglab.payment.authorization

import org.springframework.stereotype.Component

@Component
class FakeUpstreamAuthorizationGateway : UpstreamAuthorizationGateway {
    override fun authorize(command: AuthorizePaymentCommand): UpstreamAuthorizationResponse =
        UpstreamAuthorizationResponse(
            result = "APPROVED",
            command = command,
        )
}
