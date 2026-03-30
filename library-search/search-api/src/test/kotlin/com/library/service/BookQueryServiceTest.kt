package com.library.service

import com.library.controller.response.PageResult
import com.library.controller.response.SearchResponse
import com.library.repository.BookRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class BookQueryServiceTest : StringSpec({

    val bookRepository = mockk<BookRepository>()
    val bookQueryService = BookQueryService(bookRepository)

    beforeTest {
        clearMocks(bookRepository)
    }


    "search시 인자가 그대로 넘어간다." {

        val givenQuery = "HTTP 완벽 가이드"
        val givenPage = 1
        val givenSize = 10

        every { bookRepository.search(givenQuery, givenPage, givenSize) } returns PageResult(1, 1, 1, listOf())

        bookQueryService.search(givenQuery, givenPage, givenSize)

        verify(exactly = 1) {
            bookRepository.search(givenQuery, givenPage, givenSize)
        }
    }
})
