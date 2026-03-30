package com.library.service

import com.library.repository.DailyStatRepository
import io.kotest.core.spec.style.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate
import java.time.LocalTime

class DailyStatQueryServiceTest : StringSpec({

    val dailyStatRepository = mockk<DailyStatRepository>()
    val dailyStatQueryService = DailyStatQueryService(dailyStatRepository)

    "findQueryCount 조회시 하루치를 조회하면서 쿼리 개수가 반환된다." {
        val givenQuery = "HTTP"
        val givenDate = LocalDate.of(2026, 1, 1)

        every {
            dailyStatRepository
                .countByQueryAndEventDateTimeBetween(any(), any(), any())
        } returns 2

        dailyStatQueryService.findQueryCount(givenQuery, givenDate)


        verify(exactly = 1) {
            dailyStatRepository
                .countByQueryAndEventDateTimeBetween(givenQuery, givenDate.atStartOfDay(), givenDate.atTime(LocalTime.MAX))
        }
    }

})
