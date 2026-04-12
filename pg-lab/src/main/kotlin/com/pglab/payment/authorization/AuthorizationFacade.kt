package com.pglab.payment.authorization

import org.springframework.stereotype.Service

@Service
class AuthorizationFacade(
    private val upstreamAuthorizationGateway: UpstreamAuthorizationGateway,
    private val authorizePaymentService: AuthorizePaymentService,
) {
    fun authorize(command: AuthorizePaymentCommand): FacadeAuthorizePaymentResult {
        val upstreamResult = upstreamAuthorizationGateway.authorize(command)
        val saved = authorizePaymentService.authorize(upstreamResult.command)

        return FacadeAuthorizePaymentResult(
            authorizePaymentResult = saved,
            upstreamResult = upstreamResult.result,
        )
    }
}

interface UpstreamAuthorizationGateway {
    fun authorize(command: AuthorizePaymentCommand): UpstreamAuthorizationResponse
}

data class UpstreamAuthorizationResponse(
    val result: String,
    val command: AuthorizePaymentCommand,
)

data class FacadeAuthorizePaymentResult(
    val authorizePaymentResult: AuthorizePaymentResult,
    val upstreamResult: String,
)
