package com.library.unit

import com.library.ApplicationException
import com.library.ErrorType
import com.library.feign.KakaoErrorDecoder
import com.library.feign.KakaoErrorResponse
import feign.Request
import feign.Response
import io.kotest.core.spec.style.StringSpec
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions
import org.springframework.http.HttpStatus
import tools.jackson.databind.json.JsonMapper

class KakaoErrorDecoderTest: StringSpec({
    "카카오 에러 디코더에서 에러 발생시 RuntimeException 예외가 throw 된다." {

        val  jsonMapper = mockk<JsonMapper>()
        val errorDecoder = KakaoErrorDecoder(jsonMapper)

        every {
            jsonMapper.readValue(any<String>(), eq(KakaoErrorResponse::class.java))
        } returns KakaoErrorResponse(errorType = "INVALID PARAMETER", message = "올바르지 않은 파라미터")

        val response = Response.builder()
            .status(400)
            .request(Request.create(Request.HttpMethod.GET, "/error", mapOf<String, Collection<String>>(), null, null, null))
            .build()

        val throwable = Assertions.catchThrowable {
            errorDecoder.decode("methodKey", response)
        }

        Assertions.assertThat(throwable).isExactlyInstanceOf(ApplicationException::class.java)

        val exception = throwable as ApplicationException
        Assertions.assertThat(exception.errorMessage).isEqualTo("올바르지 않은 파라미터")
        Assertions.assertThat(exception.errorType).isEqualTo(ErrorType.EXTERNAL_API_ERROR)
        Assertions.assertThat(exception.httpStatus).isEqualTo(HttpStatus.BAD_REQUEST)
    }
})