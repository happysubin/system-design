package com.library.event

import com.library.service.DailyStatCommandService
import io.kotest.core.spec.style.StringSpec
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDateTime


class SearchEventHandlerTest: StringSpec({


    "handle Event" {
        val commandService = mockk<DailyStatCommandService>()
        
        every {
            commandService.save(any())
        } just Runs
        
        val eventHandler = SearchEventHandler(commandService)
        val event = SearchEvent("HTTP", LocalDateTime.now())
        
        eventHandler.handle(event)
        
        verify(exactly = 1) {
            commandService.save(any())
        }
    }
})