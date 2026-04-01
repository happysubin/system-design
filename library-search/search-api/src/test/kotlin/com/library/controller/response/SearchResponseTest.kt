package com.library.controller.response

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class SearchResponseTest : StringSpec({

    "searchResponse 객체가 생성된다."() {
        val givenTitle = "HTTP"
        val givenAuthor = "데이빗 고울리"
        val givenPublisher = "인사이트"
        val givenPubDate = LocalDate.of(2024, 12, 31)
        val givenIsbn = "9788966261208"

        val result = SearchResponse(
            title = givenTitle,
            author = givenAuthor,
            publisher = givenPublisher,
            pubDate = givenPubDate,
            isbn = givenIsbn,
        )

        result.title shouldBe givenTitle
        result.author shouldBe givenAuthor
        result.publisher shouldBe givenPublisher
        result.isbn shouldBe givenIsbn
        result.author shouldBe givenAuthor

    }

})
