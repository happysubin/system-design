package com.firstcomecoupon.coupon.api

import com.firstcomecoupon.coupon.domain.Coupon
import com.firstcomecoupon.coupon.domain.CouponIssue
import com.firstcomecoupon.coupon.domain.Member
import com.firstcomecoupon.coupon.infrastructure.persistence.CouponIssueRepository
import com.firstcomecoupon.coupon.infrastructure.persistence.CouponRepository
import com.firstcomecoupon.coupon.infrastructure.persistence.MemberRepository
import com.firstcomecoupon.coupon.infrastructure.redis.CouponClaimGateResult
import com.firstcomecoupon.coupon.infrastructure.redis.CouponClaimRedisGate
import com.firstcomecoupon.coupon.infrastructure.reconciliation.CouponStockReconciliationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CouponClaimApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var couponRepository: CouponRepository

    @Autowired
    lateinit var memberRepository: MemberRepository

    @Autowired
    lateinit var couponIssueRepository: CouponIssueRepository

    @MockitoBean
    lateinit var couponClaimRedisGate: CouponClaimRedisGate

    @MockitoBean
    lateinit var couponStockReconciliationService: CouponStockReconciliationService

    @BeforeEach
    fun setUp() {
        couponIssueRepository.deleteAll()
        couponRepository.deleteAll()
        memberRepository.deleteAll()
    }

    @Test
    fun `claims coupon and persists issuance in h2`() {
        val coupon = couponRepository.save(activeCoupon())
        val member = memberRepository.save(member())

        given(couponClaimRedisGate.tryClaim(coupon.id, member.id)).willReturn(CouponClaimGateResult.PASSED)

        mockMvc.perform(
            post("/api/v1/coupons/{couponId}/claim", coupon.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"memberId": ${member.id}}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.result").value("ISSUED"))
            .andExpect(jsonPath("$.couponId").value(coupon.id))
            .andExpect(jsonPath("$.memberId").value(member.id))
            .andExpect(jsonPath("$.issueId").isNumber)

        assertEquals(1, couponIssueRepository.count())
    }

    @Test
    fun `returns sold out when redis gate rejects stock`() {
        val coupon = couponRepository.save(activeCoupon())
        val member = memberRepository.save(member())

        given(couponClaimRedisGate.tryClaim(coupon.id, member.id)).willReturn(CouponClaimGateResult.SOLD_OUT)

        mockMvc.perform(
            post("/api/v1/coupons/{couponId}/claim", coupon.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"memberId": ${member.id}}"""),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.result").value("SOLD_OUT"))

        assertEquals(0, couponIssueRepository.count())
    }

    @Test
    fun `returns already claimed when sql unique constraint rejects second issuance`() {
        val coupon = couponRepository.save(activeCoupon())
        val member = memberRepository.save(member())

        given(couponClaimRedisGate.tryClaim(coupon.id, member.id)).willReturn(CouponClaimGateResult.PASSED)

        mockMvc.perform(
            post("/api/v1/coupons/{couponId}/claim", coupon.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"memberId": ${member.id}}"""),
        )
            .andExpect(status().isCreated)

        mockMvc.perform(
            post("/api/v1/coupons/{couponId}/claim", coupon.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"memberId": ${member.id}}"""),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.result").value("ALREADY_CLAIMED"))

        assertEquals(1, couponIssueRepository.count())
        verify(couponClaimRedisGate, times(1)).rollback(coupon.id, member.id)
    }

    @Test
    fun `returns sold out when sql capacity guard rejects claim after redis pass`() {
        val coupon = couponRepository.save(
            Coupon(
                name = "선착순 쿠폰",
                totalQuantity = 1,
                issueStartAt = LocalDateTime.now().minusHours(1),
                issueEndAt = LocalDateTime.now().plusHours(1),
            ),
        )
        val member1 = memberRepository.save(Member(email = "one@test.com", name = "one"))
        val member2 = memberRepository.save(Member(email = "two@test.com", name = "two"))

        couponIssueRepository.saveAndFlush(CouponIssue(coupon = coupon, member = member1))
        given(couponClaimRedisGate.tryClaim(coupon.id, member2.id)).willReturn(CouponClaimGateResult.PASSED)

        mockMvc.perform(
            post("/api/v1/coupons/{couponId}/claim", coupon.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"memberId": ${member2.id}}"""),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.result").value("SOLD_OUT"))

        assertEquals(1, couponIssueRepository.count())
        verify(couponClaimRedisGate, times(1)).rollback(coupon.id, member2.id)
    }

    private fun activeCoupon(): Coupon = Coupon(
        name = "선착순 쿠폰",
        totalQuantity = 100,
        issueStartAt = LocalDateTime.now().minusHours(1),
        issueEndAt = LocalDateTime.now().plusHours(1),
    )

    private fun member(): Member = Member(
        email = "member@test.com",
        name = "tester",
    )
}
