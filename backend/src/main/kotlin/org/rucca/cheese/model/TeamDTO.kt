package org.rucca.cheese.model

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

/**
 * @param id
 * @param name
 * @param createdAt
 * @param updatedAt
 */
data class TeamDTO(
    @Schema(example = "null", description = "") @get:JsonProperty("id") val id: kotlin.Long? = null,
    @Schema(example = "null", description = "")
    @get:JsonProperty("name")
    val name: kotlin.String? = null,
    @Schema(example = "null", description = "")
    @get:JsonProperty("createdAt")
    val createdAt: java.time.OffsetDateTime? = null,
    @Schema(example = "null", description = "")
    @get:JsonProperty("updatedAt")
    val updatedAt: java.time.OffsetDateTime? = null,
) {}
