package com.library.entity

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime


class DailyStatTest : StringSpec({

    "DailyStat 엔티티 생성" {
        val givenQuery = "HTTP"
        val givenLocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)

        val result = DailyStat(query = givenQuery, localDateTime = givenLocalDateTime)

        result.query shouldBe givenQuery
        result.localDateTime shouldBe givenLocalDateTime
    }
})
