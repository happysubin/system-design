package com.firstcomecoupon.coupon.infrastructure.persistence

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
class CouponIssueRepositoryTest {

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
    fun `counts issued coupons by coupon id`() {
        val coupon = couponRepository.save(coupon(totalQuantity = 10))
        val otherCoupon = couponRepository.save(coupon(totalQuantity = 20))
        val member1 = memberRepository.save(member("one@test.com"))
        val member2 = memberRepository.save(member("two@test.com"))
        val member3 = memberRepository.save(member("three@test.com"))

        couponIssueRepository.saveAndFlush(CouponIssue(coupon = coupon, member = member1))
        couponIssueRepository.saveAndFlush(CouponIssue(coupon = coupon, member = member2))
        couponIssueRepository.saveAndFlush(CouponIssue(coupon = otherCoupon, member = member3))

        assertEquals(2L, couponIssueRepository.countByCouponId(coupon.id))
        assertEquals(1L, couponIssueRepository.countByCouponId(otherCoupon.id))
    }

    @Test
    fun `returns grouped issued counts for multiple coupons`() {
        val coupon1 = couponRepository.save(coupon(totalQuantity = 10))
        val coupon2 = couponRepository.save(coupon(totalQuantity = 20))
        val member1 = memberRepository.save(member("one@test.com"))
        val member2 = memberRepository.save(member("two@test.com"))
        val member3 = memberRepository.save(member("three@test.com"))

        couponIssueRepository.saveAndFlush(CouponIssue(coupon = coupon1, member = member1))
        couponIssueRepository.saveAndFlush(CouponIssue(coupon = coupon1, member = member2))
        couponIssueRepository.saveAndFlush(CouponIssue(coupon = coupon2, member = member3))

        val countsByCouponId = couponIssueRepository.findIssuedCountsByCouponIds(listOf(coupon1.id, coupon2.id))
            .associateBy({ it.couponId }, { it.issuedCount })

        assertEquals(2L, countsByCouponId[coupon1.id])
        assertEquals(1L, countsByCouponId[coupon2.id])
    }

    private fun coupon(totalQuantity: Int): Coupon = Coupon(
        name = "선착순 쿠폰",
        totalQuantity = totalQuantity,
        issueStartAt = LocalDateTime.now().minusHours(1),
        issueEndAt = LocalDateTime.now().plusHours(1),
    )

    private fun member(email: String): Member = Member(
        email = email,
        name = email.substringBefore('@'),
    )
}
