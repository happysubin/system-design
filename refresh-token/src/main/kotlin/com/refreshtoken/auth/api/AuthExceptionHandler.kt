package com.refreshtoken.auth.api

import com.refreshtoken.auth.application.InvalidCredentialsException
import com.refreshtoken.auth.application.InvalidRefreshTokenException
import com.refreshtoken.auth.application.RefreshUnavailableException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class AuthExceptionHandler {
    @ExceptionHandler(InvalidCredentialsException::class)
    fun handleInvalidCredentials(): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse("Invalid credentials"))

    @ExceptionHandler(InvalidRefreshTokenException::class)
    fun handleInvalidRefreshToken(): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse("Invalid refresh token"))

    @ExceptionHandler(RefreshUnavailableException::class)
    fun handleRefreshUnavailable(): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ErrorResponse("Session renewal unavailable. Please sign in again."))
}
