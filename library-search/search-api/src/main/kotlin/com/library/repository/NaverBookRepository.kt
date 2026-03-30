package com.library.repository

import com.library.controller.response.PageResult
import com.library.controller.response.SearchResponse
import com.library.feign.NaverBookSearchResponse
import com.library.feign.NaverFeignClient
import com.library.toLocalDate
import org.springframework.stereotype.Repository

@Repository
class NaverBookRepository(
    val naverFeignClient: NaverFeignClient
): BookRepository {

    override fun search(
        query: String,
        page: Int,
        size: Int
    ): PageResult<SearchResponse> {
        val response = naverFeignClient.search(query, page, size, "sim")

        return PageResult(
            page = page,
            size = size,
            totalElements = response.total,
            contents = createContents(response)
        )
    }

    private fun createContents(response: NaverBookSearchResponse): List<SearchResponse> = response.items.map {
        SearchResponse(
            title = it.title,
            author = it.author,
            publisher = it.publisher,
            pubDate = it.pubDate.toLocalDate(),
            isbn = it.isbn
        )
    }
}