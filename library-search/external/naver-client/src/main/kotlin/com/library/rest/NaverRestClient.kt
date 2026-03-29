package com.library.rest

import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class NaverRestClient(
    private val restClient: RestClient,
) {

    fun search(query: String): String? {
        return restClient
            .get()
            .uri {
                it
                    .path("/v1/search/book.json")
                    .queryParam("query", query)
                    .queryParam("display", 1)
                    .queryParam("start", 1)
                    .queryParam("sort", "sim")
                    .build()

            }
            .retrieve()
            .body(String::class.java)
    }

}