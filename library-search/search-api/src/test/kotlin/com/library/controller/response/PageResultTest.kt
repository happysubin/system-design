package com.library.controller.response

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.mockito.Mockito.mock

class PageResultTest : StringSpec({

    "pageResult 객체 생성한다" {
        val givenPage = 1
        val givenSize = 1
        val givenTotalElements = 1
        val response1 = mock<SearchResponse>()
        val response2 = mock<SearchResponse>()

        val pageResult =
            PageResult(page = givenPage, size = givenSize, totalElements = givenTotalElements, contents = listOf(response1, response2))

        pageResult.page shouldBe givenPage
        pageResult.size shouldBe givenSize
        pageResult.totalElements shouldBe givenTotalElements
        pageResult.contents.size shouldBe listOf(response1, response2).size
    }

})
