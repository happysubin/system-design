package com.library.controller.request

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class SearchRequest(
    @field:NotBlank(message = "query는 필수입니다.")
    @field:Size(max = 50, message = "query는 50자 이하여야 합니다.")
    val query: String?,

    @field:NotNull(message = "page는 필수입니다.")
    @field:Min(1, message = "페이지 번호는 1 이상이어야 합니다.")
    @field:Max(10000, message = "페이지 번호는 10000 이하여야 합니다.")
    val page: Int?,

    @field:NotNull(message = "size는 필수입니다.")
    @field:Min(1, message = "페이지 크기는 1 이상이어야 합니다.")
    @field:Max(50, message = "페이지 크기는 50 이하여야 합니다.")
    val size: Int?,
)