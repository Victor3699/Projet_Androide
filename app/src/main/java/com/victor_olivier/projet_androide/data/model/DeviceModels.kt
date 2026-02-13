package com.victor_olivier.projet_androide.data.model

data class DevicesResponse(
    val devices: List<Device>
)

data class Device(
    val id: String,
    val type: String,
    val availableCommands: List<String> = emptyList(),
    val opening: Int? = null,
    val power: Int? = null
) {
    override fun toString(): String {
        val state = when {
            opening != null -> "opening=$opening"
            power != null -> "power=$power"
            else -> ""
        }
        return "$type ($id) $state"
    }
}
