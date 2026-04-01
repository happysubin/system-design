package com.library.service

import com.library.controller.response.PageResult
import com.library.repository.BookRepository
import io.kotest.core.spec.style.StringSpec
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class BookQueryServiceTest : StringSpec({


    val naverBookRepository = mockk<BookRepository>()
    val kakoBookRepository = mockk<BookRepository>()
    val bookQueryService = BookQueryService(naverBookRepository, kakoBookRepository)

    beforeTest {
        clearMocks(naverBookRepository)
    }


    "search시 인자가 그대로 넘어가고 naver book api를 호출한다.." {

        val givenQuery = "HTTP 완벽 가이드"
        val givenPage = 1
        val givenSize = 10

        every { naverBookRepository.search(givenQuery, givenPage, givenSize) } returns PageResult(1, 1, 1, listOf())

        bookQueryService.search(givenQuery, givenPage, givenSize)

        verify(exactly = 1) {
            naverBookRepository.search(givenQuery, givenPage, givenSize)
        }
        verify(exactly = 0) {
            kakoBookRepository.search(givenQuery, givenPage, givenSize)
        }
    }
})
