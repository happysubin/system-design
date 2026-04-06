package com.paymentlab.payment.application

import com.paymentlab.payment.api.dto.IssueCheckoutKeyRequest
import com.paymentlab.payment.infrastructure.redis.CheckoutKeyStore
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class CheckoutKeyApplicationServiceTest {

    @Mock
    lateinit var checkoutKeyStore: CheckoutKeyStore

    @Test
    fun `체크아웃 키 발급 요청이 들어오면 저장소에 키를 발급한다`() {
        val service = CheckoutKeyApplicationService(checkoutKeyStore)

        given(checkoutKeyStore.issue(1, "order-1", 15000)).willReturn("checkout-1")

        val result = service.issueCheckoutKey(
            IssueCheckoutKeyRequest(
                orderId = 1,
                merchantOrderId = "order-1",
                amount = 15000,
            ),
        )

        assertEquals("checkout-1", result.checkoutKey)
        verify(checkoutKeyStore).issue(1, "order-1", 15000)
    }
}
