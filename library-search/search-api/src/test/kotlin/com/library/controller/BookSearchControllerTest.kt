package com.library.controller

import com.library.controller.response.PageResult
import com.library.service.BookApplicationService
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.setup.MockMvcBuilders


class BookSearchControllerTest : StringSpec({

    val bookApplicationService = mockk<BookApplicationService>()
    val bookController = BookSearchController(bookApplicationService)
    val mockMvc = MockMvcBuilders
        .standaloneSetup(bookController)
        .build()

    beforeTest {
        clearMocks(bookApplicationService)
    }

    "search books"  {
        val givenQuery = "HTTP"
        val givenPage = 1
        val givenSize = 10

        every { bookApplicationService.search(givenQuery, givenPage, givenSize) } returns PageResult(0 , 0 ,0 , listOf())

        val response = mockMvc
            .perform(
                MockMvcRequestBuilders.get("/api/v1/books?query=${givenQuery}&page=$givenPage&size=$givenSize")
            )
            .andReturn()
            .response


        response.status shouldBe  HttpStatus.OK.value()

        verify(exactly = 1) {
            bookApplicationService.search(givenQuery, givenPage, givenSize)
        }
    }
})
