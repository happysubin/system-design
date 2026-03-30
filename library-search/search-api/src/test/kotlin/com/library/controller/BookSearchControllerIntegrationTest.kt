package com.library.controller

import com.library.ErrorType
import com.library.controller.request.SearchRequest
import com.library.controller.response.PageResult
import com.library.service.BookQueryService
import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.spring.SpringExtension
import io.mockk.every
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath

@ActiveProfiles("test")
@ApplyExtension(SpringExtension::class)
@AutoConfigureMockMvc
@SpringBootTest
class BookSearchControllerIntegrationTest: StringSpec() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var bookQueryService: BookQueryService


    /**
     * 꼼꼼하게 작성하는 것이 중요
     */
    init {
        "정상인자로 요청 시 성공한다" {

            every {
                bookQueryService.search(query = "http", page = 1, size = 10)
            } returns PageResult(1, 10, 10, listOf())

            val request = SearchRequest(query = "http", page = 1, size = 10)
            val result = mockMvc.perform(
                MockMvcRequestBuilders
                    .get("/api/v1/books")
                    .param("query", requireNotNull(request.query))
                    .param("page", request.page.toString())
                    .param("size", request.size.toString())
            )

            result
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.totalElements").value(10))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.contents").isArray)

        }

         "query 비어있다면 BadRequest" {

            val request = SearchRequest(query = "", page = 1, size = 10)
            val result = mockMvc.perform(
                MockMvcRequestBuilders
                    .get("/api/v1/books")
                    .param("query", requireNotNull(request.query))
                    .param("page", request.page.toString())
                    .param("size", request.size.toString())
            )

            result
                .andExpect(MockMvcResultMatchers.status().isBadRequest)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorMessage").value("query는 필수입니다."))
                .andExpect(jsonPath("$.errorType").value(ErrorType.INVALID_PARAMETER.name))
        }

         "page가 음수라면 BadRequest" {

            val request = SearchRequest(query = "http", page = -1, size = 10)
            val result = mockMvc.perform(
                MockMvcRequestBuilders
                    .get("/api/v1/books")
                    .param("query", requireNotNull(request.query))
                    .param("page", request.page.toString())
                    .param("size", request.size.toString())
            )

            result
                .andExpect(MockMvcResultMatchers.status().isBadRequest)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorMessage").value("페이지 번호는 1 이상이어야 합니다."))
                .andExpect(jsonPath("$.errorType").value(ErrorType.INVALID_PARAMETER.name))
        }

         "size가 비어있다면 BadRequest" {

            val request = SearchRequest(query = "http", page = 51, size = 51)
            val result = mockMvc.perform(
                MockMvcRequestBuilders
                    .get("/api/v1/books")
                    .param("query", requireNotNull(request.query))
                    .param("page", request.page.toString())
                    .param("size", request.size.toString())
            )

            result
                .andExpect(MockMvcResultMatchers.status().isBadRequest)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorMessage").value("페이지 크기는 50 이하여야 합니다."))
                .andExpect(jsonPath("$.errorType").value(ErrorType.INVALID_PARAMETER.name))

        }
    }



}