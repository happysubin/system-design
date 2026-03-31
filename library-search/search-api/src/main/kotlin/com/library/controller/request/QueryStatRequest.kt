package com.library.controller.request

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDate

@Schema(description = "통계 요청을 위한 모델")
data class QueryStatRequest(
    @field:Schema(description = "쿼리", example = "HTTP", requiredMode = RequiredMode.REQUIRED)
    @field:NotBlank(message = "query는 필수입니다.")
    @field:Size(max = 50, message = "query는 50자 이하여야 합니다.")
    val query: String?,
    @field:Schema(description = "쿼리요청일자", example = "2026-01-01", requiredMode = RequiredMode.REQUIRED)
    @field:NotNull(message = "date는 필수입니다.")
    val date: LocalDate?,
)