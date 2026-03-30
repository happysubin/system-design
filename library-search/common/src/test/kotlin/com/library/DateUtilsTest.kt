package com.library

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class DateUtilsTest: StringSpec({

    "문자열(yyMMdd)을 LocalDate로 변환한다." {
        "20260101".toLocalDate() shouldBe LocalDate.of(2026, 1, 1)
    }
})