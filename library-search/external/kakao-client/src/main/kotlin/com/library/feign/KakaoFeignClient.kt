package com.library.feign

import com.library.KakaoBookSearchResponse
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam


@FeignClient(
    name = "kakao-feign-client",
    url = "\${spring.external.kakao.url}",
    configuration = [KakaoFeignClientConfiguration::class]
)
interface KakaoFeignClient {

    @GetMapping("/v3/search/book")
    fun search(
        @RequestParam("query") query: String,
        @RequestParam("sort") sort: String?,
        @RequestParam("page") page: Int?,
        @RequestParam("size") size: Int?,
        // 사용 가능한 값: title(제목), isbn (ISBN), publisher(출판사), person(인명)
        @RequestParam("target") target: String?,
    ): KakaoBookSearchResponse
}