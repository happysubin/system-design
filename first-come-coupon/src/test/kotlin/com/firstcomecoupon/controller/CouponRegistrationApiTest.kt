package com.firstcomecoupon.controller

import com.firstcomecoupon.repository.CouponRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CouponRegistrationApiTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var couponRepository: CouponRepository

    @MockitoBean
    lateinit var stringRedisTemplate: StringRedisTemplate

    lateinit var valueOperations: ValueOperations<String, String>

    @BeforeEach
    fun setUp() {
        couponRepository.deleteAll()
        @Suppress("UNCHECKED_CAST")
        val mockedOperations = mock(ValueOperations::class.java) as ValueOperations<String, String>
        valueOperations = mockedOperations
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations)
    }

    @Test
    fun `registers coupon and initializes redis stock`() {
        mockMvc.perform(
            post("/api/v1/coupons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "선착순 쿠폰",
                      "totalQuantity": 100,
                      "issueStartAt": "2026-03-28T10:00:00",
                      "issueEndAt": "2026-03-29T10:00:00"
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").isNumber)
            .andExpect(jsonPath("$.name").value("선착순 쿠폰"))
            .andExpect(jsonPath("$.totalQuantity").value(100))
            .andExpect(jsonPath("$.issuedQuantity").value(0))

        val savedCoupon = couponRepository.findAll().single()

        verify(valueOperations).set("coupon:stock:${savedCoupon.id}", savedCoupon.totalQuantity.toString())
    }

    @Test
    fun `persists coupon fields to h2 database`() {
        mockMvc.perform(
            post("/api/v1/coupons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "오픈 기념 쿠폰",
                      "totalQuantity": 50,
                      "issueStartAt": "2026-04-01T09:00:00",
                      "issueEndAt": "2026-04-02T09:00:00"
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.createdAt").exists())

        val savedCoupon = couponRepository.findAll().single()

        org.junit.jupiter.api.Assertions.assertAll(
            { org.junit.jupiter.api.Assertions.assertEquals("오픈 기념 쿠폰", savedCoupon.name) },
            { org.junit.jupiter.api.Assertions.assertEquals(50, savedCoupon.totalQuantity) },
            { org.junit.jupiter.api.Assertions.assertEquals(0, savedCoupon.issuedQuantity) },
            { org.junit.jupiter.api.Assertions.assertEquals(java.time.LocalDateTime.of(2026, 4, 1, 9, 0), savedCoupon.issueStartAt) },
            { org.junit.jupiter.api.Assertions.assertEquals(java.time.LocalDateTime.of(2026, 4, 2, 9, 0), savedCoupon.issueEndAt) },
        )
    }
}
