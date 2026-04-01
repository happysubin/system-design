package com.library

import java.time.LocalDate
import java.time.format.DateTimeFormatter


private val YYYY_MM_DD_FORMATTER: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
// yyyyMMdd 형식과 동일

fun String.toLocalDate(): LocalDate = LocalDate.parse(this, YYYY_MM_DD_FORMATTER)

fun String.pareOffsetDateTime(): LocalDate = LocalDate.parse(this, DateTimeFormatter.ISO_OFFSET_DATE_TIME)