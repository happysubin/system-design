package com.firstcomecoupon.controller

import com.firstcomecoupon.domain.Coupon
import com.firstcomecoupon.domain.Member
import com.firstcomecoupon.repository.CouponIssueRepository
import com.firstcomecoupon.repository.CouponRepository
import com.firstcomecoupon.repository.MemberRepository
import com.firstcomecoupon.serivce.CouponClaimGateResult
import com.firstcomecoupon.serivce.CouponClaimRedisGate
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
        assertEquals(0, couponRepository.findById(coupon.id).orElseThrow().issuedQuantity)
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
        assertEquals(0, couponRepository.findById(coupon.id).orElseThrow().issuedQuantity)
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

    private fun activeCoupon(): Coupon = Coupon(
        name = "선착순 쿠폰",
        totalQuantity = 100,
        issuedQuantity = 0,
        issueStartAt = LocalDateTime.now().minusHours(1),
        issueEndAt = LocalDateTime.now().plusHours(1),
    )

    private fun member(): Member = Member(
        email = "member@test.com",
        name = "tester",
    )
}
