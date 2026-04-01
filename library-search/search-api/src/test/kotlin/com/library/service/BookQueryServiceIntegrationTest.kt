package com.library.service

import com.library.controller.response.PageResult
import com.library.controller.response.SearchResponse
import com.library.repository.KakaoBookRepository
import com.library.repository.NaverBookRepository
import com.ninjasquad.springmockk.MockkBean
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
@ApplyExtension(SpringExtension::class)
@AutoConfigureMockMvc
@SpringBootTest
class BookQueryServiceIntegrationTest: StringSpec() {

    @Autowired
    lateinit var bookQueryService: BookQueryService

    @Autowired
    lateinit var circuitBreakerRegistry: CircuitBreakerRegistry

    @MockkBean
    lateinit var kakaoBookRepository: KakaoBookRepository

    @MockkBean
    lateinit var naverBookRepository: NaverBookRepository

    init {


        afterTest {
            clearAllMocks()
        }

        "정상상황에서는 Circuit의 상태가 CLOSED이고 naver 쪽으로 호출이 들어간다." {

            // given
            val query = "HTTP"
            val page = 1
            val size = 10

            every { naverBookRepository.search(any(), any(), any()) } returns PageResult(1, 1, 1, listOf())

            //when
            bookQueryService.search(query, page, size)

            // then
            circuitBreakerRegistry.allCircuitBreakers.first().state shouldBe CircuitBreaker.State.CLOSED

            verify(exactly = 1) {
                naverBookRepository.search(query, page, size)
            }

            verify(exactly = 0) {
                kakaoBookRepository.search(query, page, size)
            }
        }

        "circuit이 open되면 kakao쪽으로 요청을 한다." {
            val query = "HTTP"
            val page = 1
            val size = 10
            val givenResult = PageResult<SearchResponse>(1, 1, 1, listOf())

            val config = CircuitBreakerConfig.custom()
                .slidingWindowSize(1)
                .minimumNumberOfCalls(1)
                .failureRateThreshold(50.0F)
                .build()

            every { naverBookRepository.search(any(), any(), any()) } throws RuntimeException("항상 실패")
            every { kakaoBookRepository.search(any(), any(), any()) } returns givenResult

            circuitBreakerRegistry.circuitBreaker("naverSearch", config)

            //when
            val result = bookQueryService.search(query, page, size)

            //then

            circuitBreakerRegistry.allCircuitBreakers.first().state shouldBe CircuitBreaker.State.OPEN
            result shouldBe givenResult

            verify(exactly = 1) {
                naverBookRepository.search(query, page, size)
            }

            verify(exactly = 1) {
                kakaoBookRepository.search(query, page, size)
            }

        }
    }
}