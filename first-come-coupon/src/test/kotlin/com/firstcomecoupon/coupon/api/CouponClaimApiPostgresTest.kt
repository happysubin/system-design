package com.firstcomecoupon.coupon.api

import com.firstcomecoupon.coupon.domain.Coupon
import com.firstcomecoupon.coupon.domain.CouponIssue
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime
import kotlin.test.assertNull

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("postgres-test")
class CouponClaimApiPostgresTest : AbstractPostgresApiTest() {

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
    fun `쿠폰 발급 API 호출 시 PostgreSQL에 CouponIssue가 저장되고 Redis 재고와 claim marker가 반영된다`() {
        val coupon = couponRepository.save(activeCoupon())
        val member = memberRepository.save(member("member@test.com"))
        stringRedisTemplate.opsForValue().set("coupon:stock:${coupon.id}", coupon.totalQuantity.toString())

        mockMvc.perform(
            post("/api/v1/coupons/{couponId}/claim", coupon.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"memberId": ${member.id}}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.result").value("ISSUED"))

        assertEquals(1, couponIssueRepository.count())
        assertEquals("99", stringRedisTemplate.opsForValue().get("coupon:stock:${coupon.id}"))
        assertEquals("1", stringRedisTemplate.opsForValue().get("coupon:claim:${coupon.id}:${member.id}"))
    }

    @Test
    fun `같은 회원이 두 번 발급 요청하면 실제 Redis claim marker에 의해 이미 발급됨으로 처리된다`() {
        val coupon = couponRepository.save(activeCoupon())
        val member = memberRepository.save(member("duplicate@test.com"))
        stringRedisTemplate.opsForValue().set("coupon:stock:${coupon.id}", coupon.totalQuantity.toString())

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
        assertEquals("99", stringRedisTemplate.opsForValue().get("coupon:stock:${coupon.id}"))
    }

    @Test
    fun `Redis 통과 후에도 SQL capacity guard에서 수량이 초과되면 품절로 처리되고 Redis 재고가 SQL truth로 재정렬된다`() {
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
        stringRedisTemplate.opsForValue().set("coupon:stock:${coupon.id}", "1")

        mockMvc.perform(
            post("/api/v1/coupons/{couponId}/claim", coupon.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"memberId": ${member2.id}}"""),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.result").value("SOLD_OUT"))

        assertEquals(1, couponIssueRepository.count())
        assertEquals("0", stringRedisTemplate.opsForValue().get("coupon:stock:${coupon.id}"))
        assertNull(stringRedisTemplate.opsForValue().get("coupon:claim:${coupon.id}:${member2.id}"))
    }

    private fun activeCoupon(): Coupon = Coupon(
        name = "PostgreSQL 발급 쿠폰",
        totalQuantity = 100,
        issueStartAt = LocalDateTime.now().minusHours(1),
        issueEndAt = LocalDateTime.now().plusHours(1),
    )

    private fun member(email: String): Member = Member(
        email = email,
        name = email.substringBefore('@'),
    )
}
