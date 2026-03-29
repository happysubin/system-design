package com.firstcomecoupon.coupon.application

import com.firstcomecoupon.coupon.application.CouponStatisticsQueryService
import com.firstcomecoupon.coupon.domain.Coupon
import com.firstcomecoupon.coupon.domain.CouponIssue
import com.firstcomecoupon.coupon.domain.Member
import com.firstcomecoupon.coupon.infrastructure.persistence.CouponIssueRepository
import com.firstcomecoupon.coupon.infrastructure.persistence.CouponRepository
import com.firstcomecoupon.coupon.infrastructure.persistence.MemberRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime

@SpringBootTest
@ActiveProfiles("test")
class CouponStatisticsQueryServiceTest {

    @Autowired
    lateinit var couponStatisticsQueryService: CouponStatisticsQueryService

    @Autowired
    lateinit var couponIssueRepository: CouponIssueRepository

    @Autowired
    lateinit var couponRepository: CouponRepository

    @Autowired
    lateinit var memberRepository: MemberRepository

    @BeforeEach
    fun setUp() {
        couponIssueRepository.deleteAll()
        couponRepository.deleteAll()
        memberRepository.deleteAll()
    }

    @Test
    fun `returns derived issued and remaining quantities from coupon issues`() {
        val coupon = couponRepository.save(
            Coupon(
                name = "선착순 쿠폰",
                totalQuantity = 5,
                issueStartAt = LocalDateTime.now().minusHours(1),
                issueEndAt = LocalDateTime.now().plusHours(1),
            ),
        )
        val member1 = memberRepository.save(Member(email = "one@test.com", name = "one"))
        val member2 = memberRepository.save(Member(email = "two@test.com", name = "two"))

        couponIssueRepository.saveAndFlush(CouponIssue(coupon = coupon, member = member1))
        couponIssueRepository.saveAndFlush(CouponIssue(coupon = coupon, member = member2))

        val statistics = couponStatisticsQueryService.getCouponStatistics(coupon.id)

        assertEquals(coupon.id, statistics.couponId)
        assertEquals(5, statistics.totalQuantity)
        assertEquals(2L, statistics.issuedCount)
        assertEquals(3L, statistics.remainingQuantity)
    }
}
