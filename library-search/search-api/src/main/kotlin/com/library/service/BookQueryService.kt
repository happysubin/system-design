package com.library.service

import com.library.controller.response.PageResult
import com.library.controller.response.SearchResponse
import com.library.repository.BookRepository
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

@Service
class BookQueryService(
    @Qualifier("naverBookRepository")
    private val naverBookRepository: BookRepository,
    @Qualifier("kakaoBookRepository")
    private val kakaoBookRepository: BookRepository
) {
    val log: Logger = LoggerFactory.getLogger(this::class.java)

    @CircuitBreaker(name = "naverSearch", fallbackMethod = "searchFallBack")
    fun search(query: String, page: Int, size: Int): PageResult<SearchResponse> {
        log.info("[BookQueryService] naver query = {}, page = {}, size = {}", query, page, size);
        return naverBookRepository.search(query, page, size)
    }

    fun searchFallBack(query: String, page: Int, size: Int, throwable: Throwable): PageResult<SearchResponse> {
        if (throwable is CallNotPermittedException) {
            return handleOpenCircuit(query, page, size)
        }
        return handleException(query, page, size, throwable)
    }

    // warn으로 찍는걸 추천. 우리 애플리케이션에서 추적해야하는 진짜 오류와 협동하는 시스템 에러가 섞이므로
    private fun handleOpenCircuit(query: String, page: Int, size: Int): PageResult<SearchResponse> {
        log.warn("[BookQueryService] Circuit Breaker is open! Fallback to kakao search.")
        return kakaoBookRepository.search(query, page, size)
    }

    private fun handleException(query: String, page: Int, size: Int, throwable: Throwable): PageResult<SearchResponse> {
        log.error("[BookQueryService] An error occurred! Fallback to kakao search. errorMessage={}", throwable.message);
        return kakaoBookRepository.search(query, page, size)
    }

}