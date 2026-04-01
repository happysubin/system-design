package com.library

import com.fasterxml.jackson.annotation.JsonProperty

data class NaverBookSearchResponse(
    val lastBuildDate: String,
    val total: Int,
    val start: Int,
    val display: Int,
    val items: List<Item>
) {
    data class Item(
        val title: String,
        val link: String,
        val image: String,
        val author: String,
        val discount: Int,
        val publisher: String,
        @JsonProperty("pubdate")
        val pubDate: String,
        val isbn: String,
        val description: String
    )
}