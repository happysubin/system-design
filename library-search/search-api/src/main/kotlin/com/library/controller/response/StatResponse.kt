package com.library.controller.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "검색 통계")
data class StatResponse(
    @field:Schema(description = "쿼리", example = "HTTP")
    val query: String,
    @field:Schema(description = "검색횟수", example = "10")
    val count: Long
)