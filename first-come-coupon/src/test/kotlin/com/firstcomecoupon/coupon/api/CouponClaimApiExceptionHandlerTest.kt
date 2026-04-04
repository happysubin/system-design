package com.firstcomecoupon.coupon.api

import com.firstcomecoupon.coupon.api.dto.IssueCouponRequest
import com.firstcomecoupon.coupon.application.CouponClaimApplicationService
import com.firstcomecoupon.coupon.domain.CouponClaimResult
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CouponClaimApiExceptionHandlerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var couponClaimApplicationService: CouponClaimApplicationService

    @Test
    fun `returns stable internal failure response when unexpected runtime exception happens`() {
        given(couponClaimApplicationService.claimCoupon(1L, IssueCouponRequest(7L))).willThrow(IllegalStateException("boom"))

        mockMvc.perform(
            post("/api/v1/coupons/{couponId}/claim", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"memberId": 7}"""),
        )
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.result").value("INTERNAL_FAILURE"))
            .andExpect(jsonPath("$.couponId").value(1L))
            .andExpect(jsonPath("$.memberId").value(7L))
            .andExpect(jsonPath("$.message").value("unexpected coupon claim failure"))
    }

    @Test
    fun `returns stable internal failure response when claim service reports stock not initialized`() {
        given(couponClaimApplicationService.claimCoupon(1L, IssueCouponRequest(7L))).willReturn(
            CouponClaimResult.InternalFailure("coupon stock is not initialized"),
        )

        mockMvc.perform(
            post("/api/v1/coupons/{couponId}/claim", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"memberId": 7}"""),
        )
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.result").value("INTERNAL_FAILURE"))
            .andExpect(jsonPath("$.couponId").value(1L))
            .andExpect(jsonPath("$.memberId").value(7L))
            .andExpect(jsonPath("$.message").value("coupon stock is not initialized"))
    }
}
