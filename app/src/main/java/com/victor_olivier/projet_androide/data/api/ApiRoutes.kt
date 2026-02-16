package com.victor_olivier.projet_androide.data.api

object ApiRoutes {
    const val BASE = "https://polyhome.lesmoulinsdudev.com"
    const val REGISTER = "$BASE/api/users/register"
    const val AUTH = "$BASE/api/users/auth"

    const val HOUSES = "$BASE/api/houses"
    fun DEVICES(houseId: Int) = "$BASE/api/houses/$houseId/devices"
    fun DEVICE_COMMAND_PATH(houseId: Int, deviceId: String, command: String) =
        "$BASE/api/houses/$houseId/devices/$deviceId/command/$command"

    fun DEVICE_COMMANDS_PATH(houseId: Int, deviceId: String, command: String) =
        "$BASE/api/houses/$houseId/devices/$deviceId/commands/$command"

    fun DEVICE_COMMAND_QUERY(houseId: Int, deviceId: String, command: String) =
        "$BASE/api/houses/$houseId/devices/$deviceId?command=$command"

    fun DEVICE_COMMAND(houseId: Int, deviceId: String) =
        "$BASE/api/houses/$houseId/devices/$deviceId/command"

    fun DEVICE_COMMANDS(houseId: Int, deviceId: String) =
        "$BASE/api/houses/$houseId/devices/$deviceId/commands"

    fun DEVICE(houseId: Int, deviceId: String) =
        "$BASE/api/houses/$houseId/devices/$deviceId"

    fun HOUSE_BROWSER(houseId: Int) = "$BASE?houseId=$houseId"
}
