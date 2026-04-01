package com.library.repository

import com.library.KakaoBookSearchResponse
import com.library.feign.KakaoFeignClient
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate

class KakaoBookRepositoryTest : StringSpec({

    val kakaoFeignClient = mockk<KakaoFeignClient>()
    val kakaoBookRepository: BookRepository = KakaoBookRepository(kakaoFeignClient)

    afterTest {
        clearMocks(kakaoFeignClient)
    }

    "search 호출시 적절한 데이터 형식으로 변환된다." {

        val givenQuery = "HTTP"
        val givenPage = 1
        val givenSize = 1


        every {
            kakaoFeignClient.search(any(), any(), any(), any(), any())
        } returns KakaoBookSearchResponse(
            meta = KakaoBookSearchResponse.Meta(
                totalCount = 1,
                pageableCount = 1,
                isEnd = false
            ),
            documents = listOf(
                KakaoBookSearchResponse.Document(
                    title = "title",
                    contents = "contents",
                    url = "url",
                    isbn = "isbn",
                    datetime = "2026-02-01T00:00:00.000+09:00",
                    authors = listOf("author1", "author2"),
                    publisher = "publisher",
                    translators = listOf("translators"),
                    price = 1000,
                    salePrice = 10.0,
                    thumbnail = "thumbnail",
                    status = "status",
                ),
            ),
        )


        // when
        val result = kakaoBookRepository.search(givenQuery, givenPage, givenSize)

        result.page shouldBe givenPage
        result.size shouldBe givenSize
        result.totalElements shouldBe 1

        result.contents[0].pubDate shouldBe LocalDate.of(2026, 2, 1)
    }

})
