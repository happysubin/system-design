package com.library.controller.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "도서 검색 응답")
data class PageResult<T>(
    @field:Schema(description = "현재 페이지 번호", example = "1")
    val page: Int,
    @field:Schema(description = "페이지 크기", example = "10")
    val size: Int,
    @field:Schema(description = "전체 요소수", example = "100")
    val totalElements: Int,
    @field:Schema(description = "본문")
    val contents: List<T>
)