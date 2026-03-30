package com.library.controller

import com.library.controller.request.SearchRequest
import com.library.controller.response.PageResult
import com.library.controller.response.SearchResponse
import com.library.service.BookQueryService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/books")
class BookSearchController(
    val bookQueryService: BookQueryService
) {

    @GetMapping
    fun search(@Valid @ModelAttribute request: SearchRequest): PageResult<SearchResponse> {
        return bookQueryService.search(requireNotNull(request.query), requireNotNull(request.page), requireNotNull(request.size))
    }
}