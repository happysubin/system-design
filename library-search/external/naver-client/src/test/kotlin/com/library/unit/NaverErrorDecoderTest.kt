package com.library.unit

import com.library.ApplicationException
import com.library.ErrorType
import com.library.NaverErrorResponse
import com.library.feign.NaverErrorDecoder
import feign.Request
import feign.Response
import io.kotest.core.spec.style.StringSpec
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions
import org.springframework.http.HttpStatus
import tools.jackson.databind.json.JsonMapper

class NaverErrorDecoderTest: StringSpec({

    "네이버 에러 디코더에서 에러 발생시 RuntimeException 예외가 throw 된다." {

        val  jsonMapper = mockk<JsonMapper>()
        val errorDecoder = NaverErrorDecoder(jsonMapper)

        every {
            jsonMapper.readValue(any<String>(), eq(NaverErrorResponse::class.java))
        } returns NaverErrorResponse(errorMessage = "error!!", errorCode = "404")

        val response = Response.builder()
            .status(400)
            .request(Request.create(Request.HttpMethod.GET, "/error", mapOf<String, Collection<String>>(), null, null, null))
            .build()

        val throwable = Assertions.catchThrowable {
            errorDecoder.decode("methodKey", response)
        }

        Assertions.assertThat(throwable).isExactlyInstanceOf(ApplicationException::class.java)

        val exception = throwable as ApplicationException
        Assertions.assertThat(exception.errorMessage).isEqualTo("error!!")
        Assertions.assertThat(exception.errorType).isEqualTo(ErrorType.EXTERNAL_API_ERROR)
        Assertions.assertThat(exception.httpStatus).isEqualTo(HttpStatus.BAD_REQUEST)
    }
})