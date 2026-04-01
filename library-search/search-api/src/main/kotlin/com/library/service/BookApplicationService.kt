package com.library.service

import com.library.controller.response.PageResult
import com.library.controller.response.SearchResponse
import com.library.controller.response.StatResponse
import com.library.entity.DailyStat
import com.library.event.SearchEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class BookApplicationService(
    private val dailyStatQueryService: DailyStatQueryService,
    private val bookQueryService: BookQueryService,
    private val eventPublisher: ApplicationEventPublisher

) {

    fun search(query: String, page: Int, size: Int): PageResult<SearchResponse> {
        val result = bookQueryService.search(query, page, size)
        if(result.contents.isNotEmpty()) {
            eventPublisher.publishEvent(SearchEvent(query, LocalDateTime.now()))
        }
        return result
    }

    fun findQueryCount(query: String, date: LocalDate): StatResponse {
        return dailyStatQueryService.findQueryCount(query, date)
    }

    fun findTop5Query(): List<StatResponse> {
        return dailyStatQueryService.findTop5Query()
    }
}