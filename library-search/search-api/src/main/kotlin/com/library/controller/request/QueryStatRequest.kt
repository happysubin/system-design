package com.library.controller.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDate

data class QueryStatRequest(
    @field:NotBlank(message = "query는 필수입니다.")
    @field:Size(max = 50, message = "query는 50자 이하여야 합니다.")
    val query: String?,
    @field:NotNull(message = "date는 필수입니다.")
    val date: LocalDate?,
)