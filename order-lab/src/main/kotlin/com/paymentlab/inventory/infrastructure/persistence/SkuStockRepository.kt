package com.paymentlab.inventory.infrastructure.persistence

import com.paymentlab.inventory.domain.SkuStock
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SkuStockRepository : JpaRepository<SkuStock, Long> {
    @Modifying
    @Query(
        """
        update SkuStock ss
        set ss.reserved = ss.reserved + :quantity
        where ss.skuId = :skuId
          and (ss.onHand - ss.reserved) >= :quantity
        """,
    )
    fun incrementReservedIfAvailable(
        @Param("skuId") skuId: Long,
        @Param("quantity") quantity: Int,
    ): Int

    @Modifying
    @Query(
        """
        update SkuStock ss
        set ss.reserved = ss.reserved - :quantity
        where ss.skuId = :skuId
          and ss.reserved >= :quantity
        """,
    )
    fun decrementReserved(
        @Param("skuId") skuId: Long,
        @Param("quantity") quantity: Int,
    ): Int

    @Modifying
    @Query(
        """
        update SkuStock ss
        set ss.reserved = ss.reserved - :quantity,
            ss.onHand = ss.onHand - :quantity
        where ss.skuId = :skuId
          and ss.reserved >= :quantity
          and ss.onHand >= :quantity
        """,
    )
    fun decrementReservedAndOnHand(
        @Param("skuId") skuId: Long,
        @Param("quantity") quantity: Int,
    ): Int
}
