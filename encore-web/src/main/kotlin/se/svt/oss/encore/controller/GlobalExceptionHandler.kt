// SPDX-FileCopyrightText: 2026 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.controller

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import java.net.URI
import java.util.UUID

private val log = KotlinLogging.logger { }

@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(Exception::class)
    fun handleException(ex: Exception, request: HttpServletRequest): ProblemDetail {
        val errorId = UUID.randomUUID()
        log.error(ex) { "Error handling request to ${request.requestURI}, errorId=$errorId, message=${ex.message}" }
        val status = HttpStatus.INTERNAL_SERVER_ERROR

        val problem = ProblemDetail.forStatusAndDetail(
            status,
            "An unexpected error occurred (errorId=$errorId)",
        )

        problem.title = HttpStatus.INTERNAL_SERVER_ERROR.reasonPhrase
        problem.instance = URI.create(request.requestURI)

        return problem
    }
}
