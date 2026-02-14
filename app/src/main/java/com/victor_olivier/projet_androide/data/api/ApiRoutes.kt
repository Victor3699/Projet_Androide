package com.victor_olivier.projet_androide.data.api

object ApiRoutes {
    const val BASE = "https://polyhome.lesmoulinsdudev.com"
    const val REGISTER = "$BASE/api/users/register"
    const val AUTH = "$BASE/api/users/auth"

    const val HOUSES = "$BASE/api/houses"
    fun DEVICES(houseId: Int) = "$BASE/api/houses/$houseId/devices"
    fun DEVICE_COMMAND(houseId: Int, deviceId: String, command: String) =
        "$BASE/api/houses/$houseId/devices/$deviceId/commands/$command"

    fun HOUSE_BROWSER(houseId: Int) = "$BASE?houseId=$houseId"
}
