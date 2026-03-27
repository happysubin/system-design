package com.firstcomecoupon.domain

import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DomainEntityStructureTest {

    @Test
    fun `declares coupon member and coupon issue entities under domain package`() {
        val couponClass = Class.forName("com.firstcomecoupon.domain.Coupon")
        val memberClass = Class.forName("com.firstcomecoupon.domain.Member")
        val couponIssueClass = Class.forName("com.firstcomecoupon.domain.CouponIssue")

        assertNotNull(couponClass.getAnnotation(Entity::class.java))
        assertNotNull(memberClass.getAnnotation(Entity::class.java))
        assertNotNull(couponIssueClass.getAnnotation(Entity::class.java))
    }

    @Test
    fun `coupon issue enforces one coupon per member`() {
        val couponIssueClass = Class.forName("com.firstcomecoupon.domain.CouponIssue")
        val table = couponIssueClass.getAnnotation(Table::class.java)

        assertNotNull(table)
        assertEquals("coupon_issues", table.name)

        val hasCouponMemberUniqueConstraint = table.uniqueConstraints.any { uniqueConstraint ->
            uniqueConstraint.columnNames.toList() == listOf("coupon_id", "member_id")
        }

        assertTrue(hasCouponMemberUniqueConstraint)
    }
}
