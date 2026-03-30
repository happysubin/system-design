package com.library.controller

import com.library.controller.response.PageResult
import com.library.controller.response.SearchResponse
import com.library.service.BookQueryService
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.assertj.MockMvcTester
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import org.springframework.test.web.servlet.setup.MockMvcBuilders


class BookSearchControllerTest : StringSpec({

    val bookQueryService = mockk<BookQueryService>()
    val bookController = BookSearchController(bookQueryService)
    val mockMvc = MockMvcBuilders
        .standaloneSetup(bookController)
        .build()

    beforeTest {
        clearMocks(bookQueryService)
    }

    "search books"  {
        val givenQuery = "HTTP"
        val givenPage = 1
        val givenSize = 10

        every { bookQueryService.search(givenQuery, givenPage, givenSize) } returns PageResult(0 , 0 ,0 , listOf())

        val response = mockMvc
            .perform(
                MockMvcRequestBuilders.get("/api/v1/books?query=${givenQuery}&page=$givenPage&size=$givenSize")
            )
            .andReturn()
            .response


        response.status shouldBe  HttpStatus.OK.value()

        verify(exactly = 1) {
            bookQueryService.search(givenQuery, givenPage, givenSize)
        }
    }
})
