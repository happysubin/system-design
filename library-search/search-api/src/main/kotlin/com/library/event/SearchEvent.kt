package com.library.event

import java.time.LocalDateTime

data class SearchEvent(
    val query: String,
    val timestamp: LocalDateTime
)