package com.library.repository

import com.library.entity.DailyStat
import io.kotest.core.extensions.ApplyExtension
import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime

@ActiveProfiles("test")
@ApplyExtension(SpringExtension::class)
@DataJpaTest
class DailyStatRepositoryTest: StringSpec() {

    @Autowired
    private lateinit var dailyStatRepository: DailyStatRepository

    @Autowired
    lateinit var repository: DailyStatRepository

    @Autowired
    lateinit var em: EntityManager


    init {
        "dailyStat 엔티티 저장 성공" {

            val givenQuery = "HTTP"
            val givenLocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)
            val saved = repository.save(DailyStat(query = givenQuery, eventDateTime = givenLocalDateTime))

            em.clear()

            val result = repository.findById(saved.id).orElseThrow()

            result.id shouldBe saved.id
            result.query shouldBe givenQuery
            result.eventDateTime shouldBe givenLocalDateTime
        }

        "쿼리의 카운트를 조회" {
            val givenQuery = "HTTP"
            val now = LocalDateTime.of(2026, 1, 1, 0, 0)
            val stat1 = DailyStat(query = givenQuery, eventDateTime = now.plusMinutes(10))
            val stat2 = DailyStat(query = givenQuery, eventDateTime = now.minusMinutes(10))
            val stat3 = DailyStat(query = givenQuery, eventDateTime = now.plusMinutes(11))
            val stat4 = DailyStat(query = "JAVA", eventDateTime = now.plusMinutes(10))

            repository.saveAll(listOf(stat1, stat2, stat3, stat4))

            val count = dailyStatRepository.countByQueryAndEventDateTimeBetween(givenQuery, now, now.plusDays(1))

            count shouldBe 2

        }

        "가장 많이 검색된 쿼리 키워드를 개수와 함께 상위 3개 반환" {
            val now = LocalDateTime.now()
            val stat1 = DailyStat(query = "HTTP", eventDateTime = now.plusMinutes(10))
            val stat2 = DailyStat(query = "HTTP", eventDateTime = now.plusMinutes(10))
            val stat3 = DailyStat(query = "HTTP", eventDateTime = now.plusMinutes(10))

            val stat4 = DailyStat(query = "JAVA", eventDateTime = now.plusMinutes(10))
            val stat5 = DailyStat(query = "JAVA", eventDateTime = now.plusMinutes(10))
            val stat6 = DailyStat(query = "JAVA", eventDateTime = now.plusMinutes(10))
            val stat7 = DailyStat(query = "JAVA", eventDateTime = now.plusMinutes(10))

            val stat8 = DailyStat(query = "KOTLIN", eventDateTime = now.plusMinutes(10))
            val stat9 = DailyStat(query = "KOTLIN", eventDateTime = now.plusMinutes(10))

            val stat10 = DailyStat(query = "OS", eventDateTime = now.plusMinutes(10))

            dailyStatRepository.saveAll(
                listOf(stat1, stat2, stat3, stat4, stat5, stat6, stat7, stat8, stat9, stat10)
            )

            val pageRequest = PageRequest.of(0, 3)
            val result = dailyStatRepository.findTopQuery(pageRequest)

            result[0].query shouldBe "JAVA"
            result[0].count shouldBe 4

            result[1].query shouldBe "HTTP"
            result[1].count shouldBe 3

            result[2].query shouldBe "KOTLIN"
            result[2].count shouldBe 2
        }
    }
}