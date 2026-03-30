package com.library.repository

import com.library.feign.NaverBookSearchResponse
import com.library.feign.NaverFeignClient
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate

class NaverBookRepositoryTest : StringSpec({

    val naverFeignClient = mockk<NaverFeignClient>()
    val bookRepository: BookRepository = NaverBookRepository(naverFeignClient)

    afterTest {
        clearMocks(naverFeignClient)
    }

    "search 호출시 적절한 데이터 형식으로 변환된다." {

        val givenQuery = "HTTP"
        val givenPage = 1
        val givenSize = 1


        val items = listOf(
            NaverBookSearchResponse.Item(
                title = "title2",
                link = "link",
                image = "image",
                author = "author",
                discount = 0,
                publisher = "publisher",
                pubDate = "20260101",
                isbn = "isbn",
                description = "description",
            ),
            NaverBookSearchResponse.Item(
                title = "title",
                link = "link",
                image = "image",
                author = "author",
                discount = 0,
                publisher = "publisher",
                pubDate = "20250101",
                isbn = "isbn",
                description = "description",
            )
        )

        every {
            naverFeignClient.search(givenQuery, givenPage, givenSize, "sim")
        } returns NaverBookSearchResponse(
            lastBuildDate = "Wed, 29 May 2025 21:12:29 +0900",
            total = 2,
            start = 1,
            display = 2,
            items = items
        )


        // when
        val result = bookRepository.search(givenQuery, givenPage, givenSize)


        result.page shouldBe givenPage
        result.size shouldBe givenSize
        result.totalElements shouldBe 2

        result.contents[0].pubDate shouldBe LocalDate.of(2026, 1, 1)
        result.contents[1].pubDate shouldBe LocalDate.of(2025, 1, 1)



    }

})
