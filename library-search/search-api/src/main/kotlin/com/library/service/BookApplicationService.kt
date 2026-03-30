package com.library.service

import com.library.controller.response.PageResult
import com.library.controller.response.SearchResponse
import com.library.entity.DailyStat
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class BookApplicationService(
    private val dailyStatCommandService: DailyStatCommandService,
    private val bookQueryService: BookQueryService,
) {

    fun search(query: String, page: Int, size: Int): PageResult<SearchResponse> {
        val result = bookQueryService.search(query, page, size)
        dailyStatCommandService.save(DailyStat(query = query, localDateTime = LocalDateTime.now()))
        return result
    }
}