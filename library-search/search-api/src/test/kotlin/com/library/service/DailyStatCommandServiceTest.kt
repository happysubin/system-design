package com.library.service

import com.library.entity.DailyStat
import com.library.repository.DailyStatRepository
import io.kotest.core.spec.style.StringSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDateTime

class DailyStatCommandServiceTest : StringSpec({

    val dailyStatRepository = mockk<DailyStatRepository> {}

    val dailyStatCommandService = DailyStatCommandService(dailyStatRepository)

    afterTest {
        clearAllMocks()
    }

    "저장시 넘어온 인자가 그대로 호출된다." {
        val dailyStat = DailyStat(query = "HTTP", eventDateTime = LocalDateTime.of(2026, 1, 1, 1, 1),)

        every {
            dailyStatRepository.save(any())
        } returns dailyStat

        dailyStatCommandService.save(dailyStat)

        verify(exactly = 1) {
            dailyStatRepository.save(dailyStat)
        }
    }

})
