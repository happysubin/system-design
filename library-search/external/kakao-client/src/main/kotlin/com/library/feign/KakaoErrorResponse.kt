package com.library.feign

data class KakaoErrorResponse(
    val errorType: String,
    val message: String
)