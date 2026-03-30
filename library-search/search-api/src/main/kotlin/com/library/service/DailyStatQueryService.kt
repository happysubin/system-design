package com.library.service

import com.library.controller.response.StatResponse
import com.library.repository.DailyStatRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalTime

@Service
class DailyStatQueryService(
    private val dailyStatRepository: DailyStatRepository
) {
    companion object {
        const val PAGE = 0
        const val SIZE = 5
    }

    @Transactional(readOnly = true)
    fun findQueryCount(query: String, date: LocalDate): StatResponse {
        val count = dailyStatRepository
            .countByQueryAndEventDateTimeBetween(query, date.atStartOfDay(), date.atTime(LocalTime.MAX))
        return StatResponse(query = query, count = count)
    }

    @Transactional(readOnly = true)
    fun findTop5Query(): List<StatResponse> {
        val pageable = PageRequest.of(PAGE, SIZE)
        return dailyStatRepository.findTopQuery(pageable)
    }
}