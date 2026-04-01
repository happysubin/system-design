package com.library.event

import com.library.entity.DailyStat
import com.library.service.DailyStatCommandService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class SearchEventHandler(
    private val dailyStatCommandService: DailyStatCommandService
) {

    val log: Logger = LoggerFactory.getLogger(SearchEventHandler::class.java)

    @Async
    @EventListener(SearchEvent::class)
    fun handle(event: SearchEvent) {
        log.info("[SearchEventHandler] handleEvent: {}", event);
        dailyStatCommandService.save(DailyStat(query = event.query, eventDateTime = LocalDateTime.now()))
    }
}
