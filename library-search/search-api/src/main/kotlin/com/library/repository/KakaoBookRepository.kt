package com.library.repository

import com.library.KakaoBookSearchResponse
import com.library.controller.response.PageResult
import com.library.controller.response.SearchResponse
import com.library.feign.KakaoFeignClient
import com.library.pareOffsetDateTime
import org.springframework.stereotype.Repository


@Repository
class KakaoBookRepository(
    private val kakaoFeignClient: KakaoFeignClient
): BookRepository {
    override fun search(
        query: String,
        page: Int,
        size: Int
    ): PageResult<SearchResponse> {
        val response = kakaoFeignClient.search(query, "latest", page, size, null)
        return PageResult(
            page = page,
            size = size,
            totalElements = response.meta.totalCount,
            contents = createContents(response.documents)
        )
    }

    fun createContents(response: List<KakaoBookSearchResponse.Document>): List<SearchResponse> {
        return response.map {
            SearchResponse(
                title = it.title,
                author = it.authors.joinToString(", "),
                publisher = it.publisher,
                pubDate = it.datetime.pareOffsetDateTime(),
                isbn = it.isbn
            )
        }
    }
}