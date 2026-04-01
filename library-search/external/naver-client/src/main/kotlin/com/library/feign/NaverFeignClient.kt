package com.library.feign

import com.library.NaverBookSearchResponse
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(
    name = "naver-feign-client",
    url = "\${spring.external.naver.url}",
    configuration = [NaverFeignClientConfiguration::class]
)
interface NaverFeignClient {

    @GetMapping("/v1/search/book.json")
    fun search(
        @RequestParam("query") query: String,
        @RequestParam("display") display: Int,
        @RequestParam("start") start: Int,
        @RequestParam("sort") sort: String,
    ): NaverBookSearchResponse
}