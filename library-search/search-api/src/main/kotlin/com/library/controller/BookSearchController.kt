package com.library.controller

import com.library.controller.request.QueryStatRequest
import com.library.controller.request.SearchRequest
import com.library.controller.response.PageResult
import com.library.controller.response.SearchResponse
import com.library.controller.response.StatResponse
import com.library.service.BookApplicationService
import com.library.service.BookQueryService
import jakarta.validation.Valid
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/books")
class BookSearchController(
    val bookApplicationService: BookApplicationService
) {
    val log: Logger = LoggerFactory.getLogger(BookSearchController::class.java)

    @GetMapping
    fun search(@Valid @ModelAttribute request: SearchRequest): PageResult<SearchResponse> {
        log.info("[BookSearchController] search: {}", request)
        return bookApplicationService.search(requireNotNull(request.query), requireNotNull(request.page), requireNotNull(request.size))
    }

    @GetMapping("/stats")
    fun findQueryStats(@Valid @ModelAttribute request: QueryStatRequest): StatResponse {
        log.info("[BookSearchController] find stats query={}, data={}", request.query, request.date)
        return bookApplicationService.findQueryCount(requireNotNull(request.query), requireNotNull(request.date))
    }

    @GetMapping("/stats/ranking")
    fun findTop5Query(): List<StatResponse> {
        log.info("[BookSearchController] find top 5 query")
        return bookApplicationService.findTop5Query()
    }
}