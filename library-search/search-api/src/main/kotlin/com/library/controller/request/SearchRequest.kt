package com.library.controller.request

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

@Schema(description = "검색 요청을 위한 모델")
data class SearchRequest(
    @field:Schema(description = "쿼리", example = "HTTP", requiredMode = RequiredMode.REQUIRED, maxLength = 50)
    @field:NotBlank(message = "query는 필수입니다.")
    @field:Size(max = 50, message = "query는 50자 이하여야 합니다.")
    val query: String?,

    @field:Schema(description = "페이지 번호", example = "1", requiredMode = RequiredMode.REQUIRED, minLength = 1, maxLength = 10000)
    @field:NotNull(message = "page는 필수입니다.")
    @field:Min(1, message = "페이지 번호는 1 이상이어야 합니다.")
    @field:Max(10000, message = "페이지 번호는 10000 이하여야 합니다.")
    val page: Int?,

    @field:Schema(description = "페이지 사이즈", example = "10", requiredMode = RequiredMode.REQUIRED, minLength = 1, maxLength = 50)
    @field:NotNull(message = "size는 필수입니다.")
    @field:Min(1, message = "페이지 크기는 1 이상이어야 합니다.")
    @field:Max(50, message = "페이지 크기는 50 이하여야 합니다.")
    val size: Int?,
)