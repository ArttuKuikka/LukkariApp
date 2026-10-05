package fi.ArttuKuikka.LukkariApp

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import fi.ArttuKuikka.LukkariApp.ui.theme.LukkariAppTheme

data class AppConfig(
    val url: String,
    val username: String,
    val password: String
)

@Suppress("DEPRECATION")
class SecureStorage(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_lukkari_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveConfig(config: AppConfig) {
        prefs.edit {
            putString("url", config.url)
            putString("username", config.username)
            putString("password", config.password)
        }
    }

    fun getConfig(): AppConfig? {
        val url = prefs.getString("url", null)
        val username = prefs.getString("username", null)
        val password = prefs.getString("password", null)

        if (url.isNullOrEmpty() || username.isNullOrEmpty() || password.isNullOrEmpty()) {
            return null
        }
        return AppConfig(url = url, username = username, password = password)
    }

    fun clearConfig() {
        prefs.edit { clear() }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LukkariAppTheme {
                val context = LocalContext.current
                val secureStorage = remember { SecureStorage(context) }
                var appConfig by remember { mutableStateOf(secureStorage.getConfig()) }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val currentConfig = appConfig
                    if (currentConfig == null) {
                        SetupWizardScreen(
                            defaultUrl = "https://lukkarit.vamk.fi/#/schedule",
                            onConfigSaved = { newConfig ->
                                secureStorage.saveConfig(newConfig)
                                appConfig = newConfig
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        WebViewScreen(
                            config = currentConfig,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SetupWizardScreen(
    defaultUrl: String,
    onConfigSaved: (AppConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    var url by remember { mutableStateOf(defaultUrl) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "LukkariApp",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Please enter your portal details to continue.",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Initial URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(if (passwordVisible) "Hide" else "Show")
                }
            }
        )

        errorMessage?.let { msg ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (url.isBlank() || username.isBlank() || password.isBlank()) {
                    errorMessage = "All fields are required."
                } else {
                    errorMessage = null
                    onConfigSaved(AppConfig(url = url.trim(), username = username.trim(), password = password.trim()))
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save & Continue")
        }
    }
}

@Composable
fun WebViewScreen(
    config: AppConfig,
    modifier: Modifier = Modifier
) {
    var webView: WebView? by remember { mutableStateOf(null) }
    var canGoBack by remember { mutableStateOf(false) }

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        canGoBack = view?.canGoBack() == true
                        view?.let { performAutoLogin(it, config.username, config.password) }
                    }

                    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                        super.doUpdateVisitedHistory(view, url, isReload)
                        canGoBack = view?.canGoBack() == true
                    }
                }
                loadUrl(config.url)
                webView = this
            }
        },
        update = {
            webView = it
        },
        modifier = modifier.fillMaxSize()
    )
}

private fun performAutoLogin(webView: WebView, username: String, password: String) {
    val safeUsername = username.replace("\\", "\\\\").replace("\"", "\\\"")
    val safePassword = password.replace("\\", "\\\\").replace("\"", "\\\"")

    val js = """
        (function() {
            var username = "$safeUsername";
            var password = "$safePassword";
            var maxAttempts = 20;

            // 1. Click initial login button (if present)
            var attempts1 = 0;
            var interval1 = setInterval(function() {
                attempts1++;
                var icon = document.querySelector('.fa-sign-in-alt');
                if (icon) {
                    var button = icon.closest('button') || icon;
                    button.click();
                    clearInterval(interval1);
                } else if (attempts1 >= maxAttempts) {
                    clearInterval(interval1);
                }
            }, 500);

            // 2. Fill login form and submit (if present)
            var attempts2 = 0;
            var interval2 = setInterval(function() {
                attempts2++;
                var userInput = document.getElementById('username');
                var passInput = document.getElementById('password');
                var submitBtn = document.getElementById('submit_button');

                if (userInput && passInput && submitBtn) {
                    userInput.value = username;
                    userInput.dispatchEvent(new Event('input', { bubbles: true }));
                    userInput.dispatchEvent(new Event('change', { bubbles: true }));

                    passInput.value = password;
                    passInput.dispatchEvent(new Event('input', { bubbles: true }));
                    passInput.dispatchEvent(new Event('change', { bubbles: true }));

                    submitBtn.click();
                    clearInterval(interval2);
                } else if (attempts2 >= maxAttempts) {
                    clearInterval(interval2);
                }
            }, 500);

            // 3. Click optional "yesbutton" (if present)
            var attempts3 = 0;
            var interval3 = setInterval(function() {
                attempts3++;
                var yesBtn = document.getElementById('yesbutton');
                if (yesBtn) {
                    yesBtn.click();
                    clearInterval(interval3);
                } else if (attempts3 >= maxAttempts) {
                    clearInterval(interval3);
                }
            }, 500);

            // 4. Focus and scroll to .fc-view-container (if present)
            var attempts4 = 0;
            var interval4 = setInterval(function() {
                attempts4++;
                var calendar = document.querySelector('.fc-view-container');
                if (calendar) {
                    calendar.scrollIntoView({ behavior: 'smooth', block: 'start' });
                    calendar.setAttribute('tabindex', '-1');
                    calendar.focus();
                    clearInterval(interval4);
                } else if (attempts4 >= maxAttempts) {
                    clearInterval(interval4);
                }
            }, 500);
        })();
    """.trimIndent()
    webView.evaluateJavascript(js, null)
}
