package com.firstcomecoupon.coupon.api

import com.firstcomecoupon.coupon.infrastructure.persistence.CouponIssueRepository
import com.firstcomecoupon.coupon.infrastructure.persistence.CouponRepository
import com.firstcomecoupon.coupon.infrastructure.persistence.CouponStockRepository
import com.firstcomecoupon.coupon.infrastructure.persistence.MemberRepository
import com.firstcomecoupon.support.AbstractPostgresApiTest
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
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("postgres-test")
class CouponRegistrationApiPostgresTest : AbstractPostgresApiTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var couponRepository: CouponRepository

    @Autowired
    lateinit var couponIssueRepository: CouponIssueRepository

    @Autowired
    lateinit var memberRepository: MemberRepository

    @Autowired
    lateinit var stringRedisTemplate: StringRedisTemplate

    @Autowired
    lateinit var couponStockRepository: CouponStockRepository

    @BeforeEach
    fun setUp() {
        couponIssueRepository.deleteAll()
        couponRepository.deleteAll()
        memberRepository.deleteAll()
        stringRedisTemplate.connectionFactory?.connection?.serverCommands()?.flushAll()
    }

    @Test
    fun `쿠폰 등록 API 호출 시 PostgreSQL에 저장되고 Redis 재고가 초기화된다`() {
        mockMvc.perform(
            post("/api/v1/coupons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "PostgreSQL 등록 쿠폰",
                      "totalQuantity": 30,
                      "issueStartAt": "2026-04-01T09:00:00",
                      "issueEndAt": "2026-04-02T09:00:00"
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").isNumber)
            .andExpect(jsonPath("$.name").value("PostgreSQL 등록 쿠폰"))
            .andExpect(jsonPath("$.totalQuantity").value(30))

        val savedCoupon = couponRepository.findAll().single()
        assertEquals("30", stringRedisTemplate.opsForValue().get("coupon:stock:${savedCoupon.id}"))
        assertEquals(30, couponStockRepository.findByCouponId(savedCoupon.id)?.remainingQuantity)
    }
}
