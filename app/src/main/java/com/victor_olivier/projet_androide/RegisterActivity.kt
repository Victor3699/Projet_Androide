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
import com.victor_olivier.projet_androide.data.model.RegisterRequest

class RegisterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val etLogin = findViewById<EditText>(R.id.etLogin)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnDoRegister = findViewById<Button>(R.id.btnDoRegister)
        val tvGoToLogin = findViewById<TextView>(R.id.tvGoToLogin)

        tvGoToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        val api = Api()

        btnDoRegister.setOnClickListener {
            val login = etLogin.text.toString().trim()
            val password = etPassword.text.toString()

            if (login.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Remplis tous les champs", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnDoRegister.isEnabled = false

            // register => body vide => on utilise post<K>(..., onSuccess(Int))
            api.post<RegisterRequest>(
                ApiRoutes.REGISTER,
                RegisterRequest(login, password),
                onSuccess = { code ->
                    btnDoRegister.isEnabled = true
                    Log.d("API", "REGISTER code=$code")

                    if (code in 200..299) {
                        Toast.makeText(this, "Compte créé ✅ Tu peux te connecter.", Toast.LENGTH_SHORT).show()

                        // Après inscription => on va au Login
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    } else {
                        val msg = when (code) {
                            409 -> "Login déjà utilisé"
                            400 -> "Requête invalide"
                            else -> "Erreur inscription ($code)"
                        }
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}
