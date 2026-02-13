package com.victor_olivier.projet_androide.data.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class Api {

    inline fun <reified T> get(
        path: String,
        crossinline onSuccess: (Int, T?) -> Unit,
        securityToken: String? = null
    ) {
        request<T, Unit>(path, "GET", onSuccess, null, securityToken)
    }

    inline fun <reified K, reified T> post(
        path: String,
        data: K,
        crossinline onSuccess: (Int, T?) -> Unit,
        securityToken: String? = null
    ) {
        request<T, K>(path, "POST", onSuccess, data, securityToken)
    }

    inline fun <reified K> post(
        path: String,
        data: K,
        crossinline onSuccess: (Int) -> Unit,
        securityToken: String? = null
    ) {
        request<K>(path, "POST", onSuccess, data, securityToken)
    }

    inline fun <reified K> put(
        path: String,
        data: K,
        crossinline onSuccess: (Int) -> Unit,
        securityToken: String? = null
    ) {
        request<K>(path, "PUT", onSuccess, data, securityToken)
    }

    inline fun delete(
        path: String,
        crossinline onSuccess: (Int) -> Unit,
        securityToken: String? = null
    ) {
        request<Unit>(path, "DELETE", onSuccess, null, securityToken)
    }

    inline fun <reified K> delete(
        path: String,
        data: K,
        crossinline onSuccess: (Int) -> Unit,
        securityToken: String? = null
    ) {
        request<K>(path, "DELETE", onSuccess, data, securityToken)
    }

    // ---------------------------------------------------
    // REQUEST WITH RESPONSE BODY
    // ---------------------------------------------------
    inline fun <reified T, reified K> request(
        path: String,
        method: String,
        crossinline onSuccess: (Int, T?) -> Unit,
        data: K? = null,
        securityToken: String? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val connection = prepareConnection(path, method, data, securityToken)
            val responseCode = connection.responseCode

            val body: T? = if (responseCode == 200) {
                processData(connection)
            } else {
                null
            }

            withContext(Dispatchers.Main) {
                onSuccess(responseCode, body)
            }
        }
    }

    // ---------------------------------------------------
    // REQUEST WITHOUT RESPONSE BODY
    // ---------------------------------------------------
    inline fun <reified K> request(
        path: String,
        method: String,
        crossinline onSuccess: (Int) -> Unit,
        data: K? = null,
        securityToken: String? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val connection = prepareConnection(path, method, data, securityToken)
            val responseCode = connection.responseCode

            withContext(Dispatchers.Main) {
                onSuccess(responseCode)
            }
        }
    }

    // ---------------------------------------------------
    // CONNECTION SETUP
    // ---------------------------------------------------
    inline fun <reified K> prepareConnection(
        path: String,
        method: String,
        data: K? = null,
        securityToken: String? = null
    ): HttpURLConnection {
        val url = URL(path)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.setRequestProperty("Content-Type", "application/json")

        if (securityToken != null) {
            connection.setRequestProperty("Authorization", "Bearer $securityToken")
        }

        if (data != null) {
            connection.doOutput = true
            val json = toJSON(data)

            val outputInBytes = json.toByteArray()
            connection.outputStream.write(outputInBytes)
            connection.outputStream.close()
        }

        return connection
    }

    // ---------------------------------------------------
    // READ RESPONSE
    // ---------------------------------------------------
    inline fun <reified T> processData(connection: HttpURLConnection): T? {
        return try {
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val jsonData = reader.readText()
            reader.close()

            if (jsonData.isBlank()) {
                null
            } else {
                parseJSON(jsonData)
            }

        } catch (e: Exception) {
            null
        }
    }

    // ---------------------------------------------------
    // JSON PARSING
    // ---------------------------------------------------
    inline fun <reified T> parseJSON(jsonData: String): T {
        val typeToken = object : TypeToken<T>() {}.type
        return Gson().fromJson(jsonData, typeToken)
    }

    inline fun <reified K> toJSON(data: K): String {
        val typeToken = object : TypeToken<K>() {}.type
        return Gson().toJson(data, typeToken)
    }
}
