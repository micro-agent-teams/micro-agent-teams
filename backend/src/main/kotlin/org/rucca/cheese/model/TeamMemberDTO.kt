package org.rucca.cheese.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import io.swagger.v3.oas.annotations.media.Schema

/**
 * @param userId
 * @param nickname
 * @param role
 */
data class TeamMemberDTO(
    @Schema(example = "null", description = "")
    @get:JsonProperty("userId")
    val userId: kotlin.Long? = null,
    @Schema(example = "null", description = "")
    @get:JsonProperty("nickname")
    val nickname: kotlin.String? = null,
    @Schema(example = "null", description = "")
    @get:JsonProperty("role")
    val role: TeamMemberDTO.Role? = null,
) {

    /** Values: OWNER,ADMIN,MEMBER */
    enum class Role(@get:JsonValue val value: kotlin.String) {

        OWNER("OWNER"),
        ADMIN("ADMIN"),
        MEMBER("MEMBER");

        companion object {
            @JvmStatic
            @JsonCreator
            fun forValue(value: kotlin.String): Role {
                return values().first { it -> it.value == value }
            }
        }
    }
}
