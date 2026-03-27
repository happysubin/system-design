package com.firstcomecoupon.serivce

import com.firstcomecoupon.controller.dto.CreateCouponRequest
import com.firstcomecoupon.domain.Coupon
import com.firstcomecoupon.repository.CouponRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.LocalDateTime
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class CouponServiceTest {

    @Mock
    lateinit var couponRepository: CouponRepository

    @Mock
    lateinit var stringRedisTemplate: StringRedisTemplate

    @Mock
    lateinit var valueOperations: ValueOperations<String, String>

    @Test
    fun `createCoupon saves coupon and returns mapped response`() {
        val request = CreateCouponRequest(
            name = "오픈 기념 쿠폰",
            totalQuantity = 50,
            issueStartAt = LocalDateTime.of(2026, 3, 28, 10, 0),
            issueEndAt = LocalDateTime.of(2026, 3, 29, 10, 0),
        )
        val savedCoupon = Coupon(
            id = 1L,
            name = request.name,
            totalQuantity = request.totalQuantity,
            issuedQuantity = 0,
            issueStartAt = request.issueStartAt,
            issueEndAt = request.issueEndAt,
            createdAt = LocalDateTime.of(2026, 3, 27, 22, 0),
        )
        val service = CouponService(couponRepository, stringRedisTemplate)

        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations)
        given(couponRepository.save(org.mockito.ArgumentMatchers.any(Coupon::class.java))).willReturn(savedCoupon)

        val response = service.createCoupon(request)

        val couponCaptor = ArgumentCaptor.forClass(Coupon::class.java)
        verify(couponRepository).save(couponCaptor.capture())
        val couponToSave = couponCaptor.value

        assertEquals(request.name, couponToSave.name)
        assertEquals(request.totalQuantity, couponToSave.totalQuantity)
        assertEquals(0, couponToSave.issuedQuantity)
        assertEquals(request.issueStartAt, couponToSave.issueStartAt)
        assertEquals(request.issueEndAt, couponToSave.issueEndAt)

        assertEquals(savedCoupon.id, response.id)
        assertEquals(savedCoupon.name, response.name)
        assertEquals(savedCoupon.totalQuantity, response.totalQuantity)
        assertEquals(savedCoupon.issuedQuantity, response.issuedQuantity)
        assertEquals(savedCoupon.issueStartAt, response.issueStartAt)
        assertEquals(savedCoupon.issueEndAt, response.issueEndAt)
        assertEquals(savedCoupon.createdAt, response.createdAt)
    }

    @Test
    fun `createCoupon initializes redis stock after coupon is saved`() {
        val request = CreateCouponRequest(
            name = "선착순 쿠폰",
            totalQuantity = 100,
            issueStartAt = LocalDateTime.of(2026, 3, 28, 10, 0),
            issueEndAt = LocalDateTime.of(2026, 3, 29, 10, 0),
        )
        val savedCoupon = Coupon(
            id = 7L,
            name = request.name,
            totalQuantity = request.totalQuantity,
            issuedQuantity = 0,
            issueStartAt = request.issueStartAt,
            issueEndAt = request.issueEndAt,
        )
        val service = CouponService(couponRepository, stringRedisTemplate)

        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations)
        given(couponRepository.save(org.mockito.ArgumentMatchers.any(Coupon::class.java))).willReturn(savedCoupon)

        service.createCoupon(request)

        val inOrder = inOrder(couponRepository, valueOperations)
        inOrder.verify(couponRepository).save(org.mockito.ArgumentMatchers.any(Coupon::class.java))
        inOrder.verify(valueOperations).set("coupon:stock:${savedCoupon.id}", savedCoupon.totalQuantity.toString())
    }
}
