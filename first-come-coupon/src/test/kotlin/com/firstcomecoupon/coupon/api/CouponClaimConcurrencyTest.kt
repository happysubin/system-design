package com.firstcomecoupon.coupon.api

import com.firstcomecoupon.coupon.domain.Coupon
import com.firstcomecoupon.coupon.domain.Member
import com.firstcomecoupon.coupon.infrastructure.persistence.CouponIssueRepository
import com.firstcomecoupon.coupon.infrastructure.persistence.CouponRepository
import com.firstcomecoupon.coupon.infrastructure.persistence.MemberRepository
import com.firstcomecoupon.support.AbstractPostgresApiTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import java.time.LocalDateTime
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertNotNull

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("postgres-test")
class CouponClaimConcurrencyTest : AbstractPostgresApiTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var couponRepository: CouponRepository

    @Autowired
    lateinit var memberRepository: MemberRepository

    @Autowired
    lateinit var couponIssueRepository: CouponIssueRepository

    @Autowired
    lateinit var stringRedisTemplate: StringRedisTemplate
    @BeforeEach
    fun setUp() {
        couponIssueRepository.deleteAll()
        couponRepository.deleteAll()
        memberRepository.deleteAll()
        stringRedisTemplate.connectionFactory?.connection?.serverCommands()?.flushAll()
    }

    @Test
    fun `고경합 환경에서도 발급 수는 총 수량을 초과하지 않는다`() {
        val coupon = couponRepository.save(activeCoupon(totalQuantity = 50))
        val members = (1..100).map { index ->
            memberRepository.save(Member(email = "load-$index@test.com", name = "member-$index"))
        }
        stringRedisTemplate.opsForValue().set("coupon:stock:${coupon.id}", coupon.totalQuantity.toString())

        val responses = executeConcurrentClaims(coupon.id, members.map { it.id })

        val issuedCount = responses.count { it.status == 201 && it.result == "ISSUED" }
        val soldOutCount = responses.count { it.status == 409 && it.result == "SOLD_OUT" }

        assertEquals(100, responses.size)
        assertEquals(50, issuedCount)
        assertEquals(50, soldOutCount)
        assertEquals(50L, couponIssueRepository.countByCouponId(coupon.id))
        assertEquals("0", stringRedisTemplate.opsForValue().get("coupon:stock:${coupon.id}"))
    }

    @Test
    fun `같은 회원의 동시 발급 요청은 하나만 성공한다`() {
        val coupon = couponRepository.save(activeCoupon(totalQuantity = 100))
        val member = memberRepository.save(Member(email = "duplicate-load@test.com", name = "duplicate-load"))
        stringRedisTemplate.opsForValue().set("coupon:stock:${coupon.id}", coupon.totalQuantity.toString())

        val responses = executeConcurrentClaims(coupon.id, List(50) { member.id })

        val issuedCount = responses.count { it.status == 201 && it.result == "ISSUED" }
        val alreadyClaimedCount = responses.count { it.status == 409 && it.result == "ALREADY_CLAIMED" }

        assertEquals(50, responses.size)
        assertEquals(1, issuedCount)
        assertEquals(49, alreadyClaimedCount)
        assertEquals(1L, couponIssueRepository.countByCouponId(coupon.id))
        assertEquals("99", stringRedisTemplate.opsForValue().get("coupon:stock:${coupon.id}"))
        assertNotNull(stringRedisTemplate.opsForValue().get("coupon:claim:${coupon.id}:${member.id}"))
    }

    private fun executeConcurrentClaims(couponId: Long, memberIds: List<Long>): List<ClaimResponse> {
        val executor = Executors.newFixedThreadPool(memberIds.size.coerceAtMost(32))
        val startLatch = CountDownLatch(1)
        val readyLatch = CountDownLatch(memberIds.size)

        return try {
            val futures = memberIds.map { memberId ->
                executor.submit(
                    Callable {
                        readyLatch.countDown()
                        startLatch.await(10, TimeUnit.SECONDS)

                        val mvcResult = mockMvc.perform(
                            post("/api/v1/coupons/{couponId}/claim", couponId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""{"memberId": $memberId}"""),
                        ).andReturn()

                        ClaimResponse(
                            status = mvcResult.response.status,
                            result = extractResult(mvcResult.response.contentAsString),
                        )
                    },
                )
            }

            readyLatch.await(10, TimeUnit.SECONDS)
            startLatch.countDown()
            futures.map { it.get(10, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun activeCoupon(totalQuantity: Int): Coupon = Coupon(
        name = "동시성 검증 쿠폰",
        totalQuantity = totalQuantity,
        issueStartAt = LocalDateTime.now().minusHours(1),
        issueEndAt = LocalDateTime.now().plusHours(1),
    )

    private fun extractResult(responseBody: String): String =
        RESULT_REGEX.find(responseBody)?.groupValues?.get(1)
            ?: error("result field not found in response: $responseBody")

    private data class ClaimResponse(
        val status: Int,
        val result: String,
    )

    companion object {
        private val RESULT_REGEX = Regex(""""result"\s*:\s*"([^"]+)"""")
    }
}
