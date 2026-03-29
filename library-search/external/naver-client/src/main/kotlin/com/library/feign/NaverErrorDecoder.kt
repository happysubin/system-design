package com.library.feign

import com.library.NaverErrorResponse
import feign.Response
import feign.codec.ErrorDecoder
import tools.jackson.databind.json.JsonMapper
import java.io.IOException
import java.lang.Exception

class NaverErrorDecoder(
    private val jsonMapper: JsonMapper,
): ErrorDecoder {

    override fun decode(methodKey: String, response: Response): Exception {
        try {
            val body = extractBody(response)
            val errorResponse = jsonMapper.readValue(body, NaverErrorResponse::class.java)
            throw RuntimeException(errorResponse.errorMessage)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    private fun extractBody(response: Response): String? = response
        .body()
        ?.asInputStream()
        ?.readBytes()
        ?.toString(Charsets.UTF_8)
}