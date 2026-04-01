package com.library.controller.response

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(description = "검색결과")
data class SearchResponse(
    @field:Schema(description = "도서 제목", example = "HTTP 완벽 가이드")
    val title: String,
    @field:Schema(description = "저자명", example = "데이빗고울리")
    val author: String,
    @field:Schema(description = "출판사", example = "인사이트")
    val publisher: String,
    @field:Schema(description = "출판일", example = "2015-01-01")
    val pubDate: LocalDate,
    @field:Schema(description = "isbn", example = "9788966261208")
    val isbn: String
)