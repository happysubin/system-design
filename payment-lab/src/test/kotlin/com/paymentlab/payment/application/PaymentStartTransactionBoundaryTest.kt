package com.paymentlab.payment.application

import com.paymentlab.inventory.application.InventoryHoldApplicationService
import com.paymentlab.inventory.domain.InventoryHold
import com.paymentlab.inventory.domain.InventoryHoldStatus
import com.paymentlab.payment.api.dto.CreatePaymentAttemptRequest
import com.paymentlab.payment.domain.PaymentStatus
import com.paymentlab.payment.infrastructure.persistence.PaymentAttemptRepository
import com.paymentlab.payment.infrastructure.pg.PgApproveOutcome
import com.paymentlab.payment.infrastructure.pg.PgApproveResult
import com.paymentlab.payment.infrastructure.pg.PgClient
import com.paymentlab.payment.infrastructure.redis.CheckoutKeyStore
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.mockito.BDDMockito.given
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@SpringBootTest
class PaymentStartTransactionBoundaryTest {

    @Autowired
    lateinit var paymentFacade: PaymentFacade

    @Autowired
    lateinit var paymentAttemptRepository: PaymentAttemptRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @MockitoBean
    lateinit var pgClient: PgClient

    @MockitoBean
    lateinit var checkoutKeyStore: CheckoutKeyStore

    @MockitoBean
    lateinit var inventoryHoldApplicationService: InventoryHoldApplicationService

    @BeforeEach
    fun setUp() {
        paymentAttemptRepository.deleteAll()
        jdbcTemplate.execute("ALTER TABLE payment_attempts ALTER COLUMN id RESTART WITH 1")
    }

    @Test
    fun `결제 시작은 PG 승인 전에 재고 홀드를 먼저 확보한다`() {
        given(checkoutKeyStore.consumeIfValid("checkout-1", 1, "order-1", 15000)).willReturn(true)
        given(inventoryHoldApplicationService.reserveOrReuse(1L)).willReturn(activeHold(id = 101, orderId = 1))
        given(pgClient.approve(1, "order-1", 15000)).willReturn(
            PgApproveResult(
                pgTransactionId = "pg-tx-1",
                webhookSecret = "secret-1",
                outcome = PgApproveOutcome.PENDING,
            ),
        )

        paymentFacade.startPayment(startRequest(checkoutKey = "checkout-1"))

        val ordered = inOrder(inventoryHoldApplicationService, pgClient)
        ordered.verify(inventoryHoldApplicationService).reserveOrReuse(1L)
        ordered.verify(pgClient).approve(1, "order-1", 15000)

        val savedAttempt = paymentAttemptRepository.findByCheckoutKey("checkout-1")
        assertNotNull(savedAttempt)
        assertEquals(101, savedAttempt.inventoryHoldId)
    }

    @Test
    fun `같은 주문으로 다시 결제를 시작하면 활성 재고 홀드를 재사용한다`() {
        val reusedHold = activeHold(id = 202, orderId = 1)
        given(checkoutKeyStore.consumeIfValid("checkout-1", 1, "order-1", 15000)).willReturn(true)
        given(checkoutKeyStore.consumeIfValid("checkout-2", 1, "order-1", 15000)).willReturn(true)
        given(inventoryHoldApplicationService.reserveOrReuse(1L)).willReturn(reusedHold)
        given(pgClient.approve(1, "order-1", 15000)).willReturn(
            PgApproveResult(
                pgTransactionId = "pg-tx-1",
                webhookSecret = "secret-1",
                outcome = PgApproveOutcome.PENDING,
            ),
        )
        given(pgClient.approve(2, "order-1", 15000)).willReturn(
            PgApproveResult(
                pgTransactionId = "pg-tx-2",
                webhookSecret = "secret-2",
                outcome = PgApproveOutcome.PENDING,
            ),
        )

        paymentFacade.startPayment(startRequest(checkoutKey = "checkout-1"))
        paymentFacade.startPayment(startRequest(checkoutKey = "checkout-2"))

        verify(inventoryHoldApplicationService, times(2)).reserveOrReuse(1L)
        val ordered = inOrder(inventoryHoldApplicationService, pgClient)
        ordered.verify(inventoryHoldApplicationService).reserveOrReuse(1L)
        ordered.verify(pgClient).approve(1, "order-1", 15000)
        ordered.verify(inventoryHoldApplicationService).reserveOrReuse(1L)
        ordered.verify(pgClient).approve(2, "order-1", 15000)
    }

    @Test
    fun `재고 홀드 확보가 실패하면 PG 승인 호출 없이 결제 시작을 중단한다`() {
        given(checkoutKeyStore.consumeIfValid("checkout-1", 1, "order-1", 15000)).willReturn(true)
        given(inventoryHoldApplicationService.reserveOrReuse(1L)).willThrow(RuntimeException("hold unavailable"))

        val exception = assertThrows<RuntimeException> {
            paymentFacade.startPayment(startRequest(checkoutKey = "checkout-1"))
        }

        assertEquals("hold unavailable", exception.message)
        verify(pgClient, never()).approve(1, "order-1", 15000)
    }

    @Test
    fun `PG 승인 호출이 실패해도 결제 시도 저장은 롤백되지 않는다`() {
        given(checkoutKeyStore.consumeIfValid("checkout-1", 1, "order-1", 15000)).willReturn(true)
        given(inventoryHoldApplicationService.reserveOrReuse(1L)).willReturn(activeHold(id = 101, orderId = 1))
        given(pgClient.approve(1, "order-1", 15000)).willThrow(RuntimeException("pg 500"))

        assertThrows<RuntimeException> {
            paymentFacade.startPayment(startRequest(checkoutKey = "checkout-1"))
        }

        val savedAttempt = paymentAttemptRepository.findByCheckoutKey("checkout-1")
        assertNotNull(savedAttempt)
        assertEquals(PaymentStatus.PENDING, savedAttempt.status)
        assertEquals("order-1", savedAttempt.merchantOrderId)
        assertEquals(15000, savedAttempt.amount)
    }

    @Test
    fun `PG 재조회 호출이 실패해도 pending 결제 상태는 롤백되지 않는다`() {
        paymentAttemptRepository.saveAndFlush(
            com.paymentlab.payment.domain.PaymentAttempt(
                orderId = 2,
                merchantOrderId = "order-2",
                checkoutKey = "checkout-2",
                pgTransactionId = "pg-tx-2",
                amount = 20000,
                status = PaymentStatus.PENDING,
            ),
        )
        given(pgClient.query("pg-tx-2")).willThrow(RuntimeException("pg timeout"))

        assertThrows<RuntimeException> {
            paymentFacade.reconcilePaymentAttempt(1)
        }

        val savedAttempt = paymentAttemptRepository.findByPgTransactionId("pg-tx-2")
        assertNotNull(savedAttempt)
        assertEquals(PaymentStatus.PENDING, savedAttempt.status)
    }

    private fun startRequest(
        checkoutKey: String,
        orderId: Long = 1,
        merchantOrderId: String = "order-1",
        amount: Long = 15000,
    ) = CreatePaymentAttemptRequest(
        orderId = orderId,
        merchantOrderId = merchantOrderId,
        amount = amount,
        checkoutKey = checkoutKey,
    )

    private fun activeHold(id: Long, orderId: Long) = InventoryHold(
        id = id,
        orderId = orderId,
        status = InventoryHoldStatus.HELD,
        expiresAt = LocalDateTime.now().plusMinutes(5),
        createdAt = LocalDateTime.now(),
    )
}
