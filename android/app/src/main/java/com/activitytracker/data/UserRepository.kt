package com.activitytracker.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Local, offline account store. Passwords are salted and stretched with PBKDF2. */
class UserRepository(context: Context) {

  private val db = AppDatabase.get(context)

  sealed class Result {
    data class Success(val user: User) : Result()

    data class Error(val message: String) : Result()
  }

  fun register(username: String, displayName: String, password: String): Result {
    val normalized = username.trim().lowercase()
    val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
    val values =
        ContentValues().apply {
          put("username", normalized)
          put("display_name", displayName.trim())
          put("password_hash", hash(password, salt))
          put("salt", salt.encode())
          put("created_at", System.currentTimeMillis())
        }
    return try {
      val id = db.writableDatabase.insertOrThrow("users", null, values)
      Result.Success(User(id, normalized, displayName.trim()))
    } catch (e: SQLiteConstraintException) {
      Result.Error("An account with this username already exists")
    }
  }

  fun login(username: String, password: String): Result {
    val normalized = username.trim().lowercase()
    db.readableDatabase
        .rawQuery(
            "SELECT id, display_name, password_hash, salt FROM users WHERE username = ?",
            arrayOf(normalized))
        .use { c ->
          if (!c.moveToFirst()) return Result.Error("Invalid username or password")
          val salt = Base64.decode(c.getString(3), Base64.NO_WRAP)
          val expected = c.getString(2).toByteArray()
          val actual = hash(password, salt).toByteArray()
          // Constant-time comparison to avoid leaking hash prefixes through timing.
          return if (MessageDigest.isEqual(expected, actual)) {
            Result.Success(User(c.getLong(0), normalized, c.getString(1)))
          } else {
            Result.Error("Invalid username or password")
          }
        }
  }

  private fun hash(password: String, salt: ByteArray): String {
    val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
    val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    spec.clearPassword()
    return key.encode()
  }

  private fun ByteArray.encode(): String = Base64.encodeToString(this, Base64.NO_WRAP)

  private companion object {
    const val ITERATIONS = 20_000
    const val KEY_LENGTH_BITS = 256
  }
}
