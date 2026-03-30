package com.library.config

import com.library.ApplicationException
import com.library.ErrorType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    val log: Logger = LoggerFactory.getLogger(this::class.java)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        log.error("MethodArgumentNotValidException occurred. message = {}, className = {}", e.message, e.javaClass.name)
        val errorMessage = e.bindingResult.fieldErrors
            .mapNotNull { fieldError -> fieldError.defaultMessage }
            .joinToString(", ")

        return ResponseEntity
            .badRequest()
            .body(
                ErrorResponse(
                    errorMessage = errorMessage,
                    errorType = ErrorType.INVALID_PARAMETER
                )
            )
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

    class ErrorResponse(
        val errorMessage: String,
        val errorType: ErrorType,
    )
}