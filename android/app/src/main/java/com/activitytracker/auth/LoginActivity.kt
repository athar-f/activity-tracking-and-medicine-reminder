package com.activitytracker.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.activitytracker.MainActivity
import com.activitytracker.R
import com.activitytracker.data.UserRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.concurrent.Executors

/**
 * Native (Kotlin) login / registration screen. It is the launcher activity; React Native
 * is only started once a user is authenticated, and reads the user through AuthModule.
 */
class LoginActivity : AppCompatActivity() {

  private val io = Executors.newSingleThreadExecutor()
  private lateinit var sessionManager: SessionManager
  private lateinit var userRepository: UserRepository

  private lateinit var nameLayout: TextInputLayout
  private lateinit var usernameLayout: TextInputLayout
  private lateinit var passwordLayout: TextInputLayout
  private lateinit var nameInput: TextInputEditText
  private lateinit var usernameInput: TextInputEditText
  private lateinit var passwordInput: TextInputEditText
  private lateinit var submitButton: MaterialButton
  private lateinit var toggleButton: MaterialButton
  private lateinit var progress: LinearProgressIndicator

  private var registerMode = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    sessionManager = SessionManager(this)
    if (sessionManager.isLoggedIn) {
      // Already signed in (launcher or notification tap): bring the existing RN activity forward.
      openApp(clearTask = false)
      return
    }

    userRepository = UserRepository(this)
    setContentView(R.layout.activity_login)
    applyInsets()
    bindViews()
    registerMode = savedInstanceState?.getBoolean(STATE_REGISTER_MODE) ?: false
    renderMode()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putBoolean(STATE_REGISTER_MODE, registerMode)
  }

  override fun onDestroy() {
    io.shutdown()
    super.onDestroy()
  }

  private fun applyInsets() {
    val root = findViewById<View>(R.id.login_root)
    ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
      val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
      v.updatePadding(top = bars.top, bottom = bars.bottom)
      insets
    }
  }

  private fun bindViews() {
    nameLayout = findViewById(R.id.name_layout)
    usernameLayout = findViewById(R.id.username_layout)
    passwordLayout = findViewById(R.id.password_layout)
    nameInput = findViewById(R.id.name_input)
    usernameInput = findViewById(R.id.username_input)
    passwordInput = findViewById(R.id.password_input)
    submitButton = findViewById(R.id.submit_button)
    toggleButton = findViewById(R.id.toggle_mode_button)
    progress = findViewById(R.id.progress)

    submitButton.setOnClickListener { submit() }
    toggleButton.setOnClickListener {
      registerMode = !registerMode
      renderMode()
    }
    passwordInput.setOnEditorActionListener { _, actionId, _ ->
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        submit()
        true
      } else false
    }
  }

  private fun renderMode() {
    nameLayout.visibility = if (registerMode) View.VISIBLE else View.GONE
    submitButton.setText(if (registerMode) R.string.login_create_account else R.string.login_sign_in)
    toggleButton.setText(if (registerMode) R.string.login_have_account else R.string.login_need_account)
    clearErrors()
  }

  private fun clearErrors() {
    nameLayout.error = null
    usernameLayout.error = null
    passwordLayout.error = null
  }

  private fun submit() {
    clearErrors()
    val name = nameInput.text?.toString()?.trim().orEmpty()
    val username = usernameInput.text?.toString()?.trim().orEmpty()
    val password = passwordInput.text?.toString().orEmpty()

    var valid = true
    if (registerMode && name.isEmpty()) {
      nameLayout.error = getString(R.string.login_error_name)
      valid = false
    }
    if (!USERNAME_PATTERN.matches(username)) {
      usernameLayout.error = getString(R.string.login_error_username)
      valid = false
    }
    if (password.length < MIN_PASSWORD_LENGTH) {
      passwordLayout.error = getString(R.string.login_error_password)
      valid = false
    }
    if (!valid) return

    setLoading(true)
    io.execute {
      val result =
          if (registerMode) userRepository.register(username, name, password)
          else userRepository.login(username, password)
      runOnUiThread {
        setLoading(false)
        when (result) {
          is UserRepository.Result.Success -> {
            sessionManager.save(result.user)
            openApp(clearTask = true)
          }
          is UserRepository.Result.Error -> passwordLayout.error = result.message
        }
      }
    }
  }

  private fun setLoading(loading: Boolean) {
    progress.visibility = if (loading) View.VISIBLE else View.INVISIBLE
    submitButton.isEnabled = !loading
    toggleButton.isEnabled = !loading
  }

  private fun openApp(clearTask: Boolean) {
    val intent = Intent(this, MainActivity::class.java)
    if (clearTask) {
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
    startActivity(intent)
    finish()
  }

  private companion object {
    const val STATE_REGISTER_MODE = "register_mode"
    const val MIN_PASSWORD_LENGTH = 8
    val USERNAME_PATTERN = Regex("^[A-Za-z0-9_.]{3,30}$")
  }
}
