package com.victor_olivier.projet_androide

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.victor_olivier.projet_androide.data.api.Api
import com.victor_olivier.projet_androide.data.api.ApiRoutes
import com.victor_olivier.projet_androide.data.model.AuthRequest
import com.victor_olivier.projet_androide.data.model.AuthResponse
import com.victor_olivier.projet_androide.data.model.HouseSummary
import com.victor_olivier.projet_androide.data.storage.TokenStore

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etLogin = findViewById<EditText>(R.id.etLogin)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnDoLogin = findViewById<Button>(R.id.btnDoLogin)
        val tvGoToRegister = findViewById<TextView>(R.id.tvGoToRegister)

        tvGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }

        val api = Api()
        val tokenStore = TokenStore(this)

        btnDoLogin.setOnClickListener {
            val login = etLogin.text.toString().trim()
            val password = etPassword.text.toString()

            if (login.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Remplis tous les champs", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnDoLogin.isEnabled = false

            api.post<AuthRequest, AuthResponse>(
                ApiRoutes.AUTH,
                AuthRequest(login, password),
                onSuccess = { code, body ->
                    Log.d("API", "AUTH code=$code body=$body")
                    btnDoLogin.isEnabled = true

                    if (code in 200..299 && body?.token?.isNotBlank() == true) {
                        val token = body.token
                        tokenStore.saveToken(token)

                        // Récupère houseId puis va sur DevicesActivity
                        loadHousesAndGo(token)

                    } else {
                        val msg = when (code) {
                            401 -> "Identifiants invalides"
                            400 -> "Requête invalide"
                            else -> "Erreur connexion ($code)"
                        }
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    private fun loadHousesAndGo(token: String) {
        Api().get<List<HouseSummary>>(
            ApiRoutes.HOUSES,
            onSuccess = { code, houses ->
                Log.d("API", "HOUSES code=$code houses=$houses")

                if (code == 200 && !houses.isNullOrEmpty()) {
                    val selectedHouseId =
                        houses.firstOrNull { it.owner }?.houseId ?: houses.first().houseId

                    val intent = Intent(this, DevicesActivity::class.java)
                    intent.putExtra("houseId", selectedHouseId)
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this, "Impossible de charger les maisons ($code)", Toast.LENGTH_SHORT).show()
                }
            },
            securityToken = token
        )
    }
}
