package com.library.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "daily_stat")
class DailyStat(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "query")
    val query: String,
    @Column(name = "event_date_time")
    val eventDateTime: LocalDateTime
) {


    override fun toString(): String {
        return "DailyStat(id=$id, query='$query', localDateTime=$eventDateTime)"
    }
}