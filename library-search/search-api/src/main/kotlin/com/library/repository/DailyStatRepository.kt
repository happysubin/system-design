package com.library.repository

import com.library.controller.response.StatResponse
import com.library.entity.DailyStat
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface DailyStatRepository: JpaRepository<DailyStat, Long> {
    fun countByQueryAndEventDateTimeBetween(query: String, start: LocalDateTime, end: LocalDateTime): Long

    @Query("SELECT new com.library.controller.response.StatResponse(ds.query, count(ds.query))" +
            "FROM DailyStat ds " +
            "GROUP BY ds.query ORDER BY count(ds.query) DESC")
    fun findTopQuery(pageable: Pageable): List<StatResponse>
}