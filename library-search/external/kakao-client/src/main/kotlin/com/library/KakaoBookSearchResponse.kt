package com.library

import com.fasterxml.jackson.annotation.JsonProperty

data class KakaoBookSearchResponse(
    val meta: Meta,
    val documents: List<Document>
) {


    data class Meta(
        @field:JsonProperty("total_count")
        val totalCount: Int,
        @field:JsonProperty("pageable_count")
        val pageableCount: Int,
        @field:JsonProperty("is_end")
        val isEnd: Boolean
    )

    data class Document(
        val title: String,
        val contents: String,
        val url: String,
        val isbn: String,
        val datetime: String,
        val authors: List<String>,
        val publisher: String,
        val translators: List<String>,
        val price: Int,
        @field:JsonProperty("sale_price")
        val salePrice: Double,
        val thumbnail: String,
        val status: String
    )
}