package com.library.feign

import com.library.ApplicationException
import com.library.ErrorType
import com.library.NaverErrorResponse
import feign.Response
import feign.codec.ErrorDecoder
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import tools.jackson.databind.json.JsonMapper
import java.io.IOException
import java.lang.Exception

class NaverErrorDecoder(
    private val jsonMapper: JsonMapper,
): ErrorDecoder {

    val log = LoggerFactory.getLogger(NaverErrorDecoder::class.java)

    override fun decode(methodKey: String, response: Response): Exception {
        try {
            val body = extractBody(response)
            val errorResponse = jsonMapper.readValue(body, NaverErrorResponse::class.java)
            throw ApplicationException(errorResponse.errorMessage, ErrorType.EXTERNAL_API_ERROR, HttpStatus.valueOf(response.status()))
        } catch (e: IOException) {
            log.error("[Naver] 에러 메시지 파싱 에러 code = {}, request = {}, methodKey = {}, errorMessage = {}", response.status(), response.request(), methodKey, e.message)
            throw ApplicationException("네이버 API 메세지 파싱 에러", ErrorType.EXTERNAL_API_ERROR, HttpStatus.valueOf(response.status()))
        }
    }

    private fun extractBody(response: Response): String? = response
        .body()
        ?.asInputStream()
        ?.readBytes()
        ?.toString(Charsets.UTF_8)
}