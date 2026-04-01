package com.library.service

import com.library.controller.response.PageResult
import com.library.controller.response.SearchResponse
import com.library.controller.response.StatResponse
import com.library.entity.DailyStat
import com.library.event.SearchEvent
import com.library.toLocalDate
import io.kotest.core.spec.style.StringSpec
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import java.time.LocalDate
import java.time.LocalDateTime

class BookApplicationServiceTest : StringSpec({

    val bookQueryService = mockk<BookQueryService>()
    val dailyStatQueryService = mockk<DailyStatQueryService>()
    val eventPublisher = mockk<ApplicationEventPublisher>()
    val bookApplicationService = BookApplicationService(dailyStatQueryService, bookQueryService, eventPublisher)

    afterTest {
        clearAllMocks()
    }

    "search 메서드 호출 시 검색 결과를 반환하면서 통계 데이터를 저장" {
        val givenQuery = "HTTP"
        val page = 1
        val size = 1

        every {
            bookQueryService.search(any(), any(), any())
        } returns PageResult(1, 1, 1, listOf(
            SearchResponse(
                title = "title",
                author = "author",
                publisher = "publisher",
                pubDate = LocalDate.parse("2020-01-01"),
                isbn = "isbn",
            )
        ))

        every {
            eventPublisher.publishEvent(any<Any>())
        } just Runs

        bookApplicationService.search(givenQuery, page, size)

        verify(exactly = 1) {
            bookQueryService.search(givenQuery, page, size)
        }
        verify(exactly = 1) {
        eventPublisher.publishEvent(match<Any> {
            it is SearchEvent && it.query == givenQuery
        })
    }
    }

    "findQueryCount 메서드 호출 시 인자를 그대로 넘긴다." {

        val givenQuery = "HTTP"
        val date = LocalDate.of(2020, 1, 1)

        every {
            dailyStatQueryService.findQueryCount(any(), any())
        } returns StatResponse(givenQuery, 2)

        bookApplicationService.findQueryCount(givenQuery, date)

        verify(exactly = 1) {
            dailyStatQueryService.findQueryCount(givenQuery, date)
        }
    }

    "findTop5Query 메서드 호출 시 dailyStatQuerySevice의 findTop5Query 메서드가 호출된다." {

        every {
            dailyStatQueryService.findTop5Query()
        } returns listOf()

        bookApplicationService.findTop5Query()

        verify(exactly = 1) {
            dailyStatQueryService.findTop5Query()
        }
    }

})
