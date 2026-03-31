package com.library.config

import com.library.ApplicationException
import com.library.ErrorType
import io.swagger.v3.oas.annotations.media.Schema
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class GlobalExceptionHandler {
    val log: Logger = LoggerFactory.getLogger(this::class.java)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        log.error("MethodArgumentNotValidException occurred. message = {}, className = {}", e.message, e.javaClass.name)
        val errorMessage = e.bindingResult.fieldErrors
            .joinToString(", ") { it.toMessage() }

        return ResponseEntity
            .badRequest()
            .body(
                ErrorResponse(
                    errorMessage = errorMessage,
                    errorType = ErrorType.INVALID_PARAMETER
                )
            )
    }

    private fun FieldError.toMessage(): String {
        return when {
            isLocalDateTypeMismatch() -> "$field must be in yyyyMMdd format"
            defaultMessage != null -> "$defaultMessage"
            else -> "$field: invalid input"
        }
    }

    private fun FieldError.isLocalDateTypeMismatch(): Boolean {
        return codes?.contains("typeMismatch.java.time.LocalDate") == true
    }

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFound(e: NoResourceFoundException): ResponseEntity<ErrorResponse> {
        log.error("NoResourceFoundException occurred. message = {}, className = {}", e.message, e.javaClass.name)
        return ResponseEntity
            .badRequest()
            .body(ErrorResponse(errorMessage = ErrorType.NO_RESOURCE.description, errorType = ErrorType.NO_RESOURCE))
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingServletRequestParameterException(e: MissingServletRequestParameterException): ResponseEntity<ErrorResponse> {
        log.error("MissingServletRequestParameterException occurred. parameterName = {}, message = {}", e.parameterName, e.message)
        return ResponseEntity
            .badRequest()
            .body(ErrorResponse(errorMessage = ErrorType.INVALID_PARAMETER.description, errorType = ErrorType.INVALID_PARAMETER))
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleMethodArgumentTypeMismatchException(e: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> {
        log.error("MethodArgumentTypeMismatchException occurred. message = {}", e.message)
        return ResponseEntity
            .badRequest()
            .body(ErrorResponse(errorMessage = ErrorType.INVALID_PARAMETER.description, errorType = ErrorType.INVALID_PARAMETER))
    }

    @ExceptionHandler(ApplicationException::class)
    fun handleApplicationException(e: ApplicationException): ResponseEntity<ErrorResponse> {
        log.error("ApplicationException occurred. message = {}, className = {}", e.message, e.javaClass.name)
        return ResponseEntity
            .status(e.httpStatus.value())
            .body(ErrorResponse(errorMessage = e.errorMessage, errorType = e.errorType))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("Exception occurred. message = {}, className = {}", e.message, e.javaClass.name)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .body(ErrorResponse(errorMessage = ErrorType.UNKNOWN.description, errorType = ErrorType.UNKNOWN))
    }

    @Schema(description = "에러응답")
    data class ErrorResponse(
        @field:Schema(description = "에러 메시지", example = "잘못된 요청값입니다.")
        val errorMessage: String,
        @field:Schema(description = "에러 타입", example = "INVALID_PARAMETER")
        val errorType: ErrorType,
    )
}