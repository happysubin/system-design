package com.library.service

import com.library.controller.response.PageResult
import com.library.entity.DailyStat
import io.kotest.core.spec.style.StringSpec
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDateTime

class BookApplicationServiceTest : StringSpec({

    val bookQueryService = mockk<BookQueryService>()
    val dailyStatCommandService = mockk<DailyStatCommandService>()
    val bookApplicationService = BookApplicationService(dailyStatCommandService, bookQueryService)

    afterTest {
        clearAllMocks()
    }

    "search 메서드 호출 시 검색 결과를 반환하면서 통계 데이터를 저장" {
        val givenQuery = "HTTP"
        val page = 1
        val size = 1
        val dailyStat = DailyStat(query = givenQuery, localDateTime = LocalDateTime.now())

        every {
            bookQueryService.search(givenQuery, page, size)
        } returns PageResult(1, 1, 1, listOf())

        every {
            dailyStatCommandService.save(any())
        } just Runs

        val result = bookApplicationService.search(givenQuery, page, size)

        verify(exactly = 1) {
            bookQueryService.search(givenQuery, page, size)
        }
        verify(exactly = 1) {
            dailyStatCommandService.save(match {
                it.query == dailyStat.query
            })
        }
    }


})
