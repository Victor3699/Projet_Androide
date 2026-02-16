package com.victor_olivier.projet_androide

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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

    private lateinit var webInitSession: WebView
    private var hasContinuedAfterInit = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etLogin = findViewById<EditText>(R.id.etLogin)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnDoLogin = findViewById<Button>(R.id.btnDoLogin)
        val tvGoToRegister = findViewById<TextView>(R.id.tvGoToRegister)

        webInitSession = findViewById(R.id.webInitSession)

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

                        initBrowserSessionAndContinue(token)
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

    private fun initBrowserSessionAndContinue(token: String) {
        hasContinuedAfterInit = false

        val settings = webInitSession.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        webInitSession.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                continueToHousesOnce(token)
            }
        }

        Handler(Looper.getMainLooper()).postDelayed({
            continueToHousesOnce(token)
        }, 800)

        webInitSession.loadUrl(ApiRoutes.BASE)
    }

    private fun continueToHousesOnce(token: String) {
        if (hasContinuedAfterInit) return
        hasContinuedAfterInit = true
        loadHousesAndGo(token)
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
