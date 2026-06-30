package com.mdaopay.paymaster

import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

data class AuthUser(
    val id: String,
    val email: String,
    val passwordHash: String,
    val passwordSalt: String,
    val createdAt: Long,
)

class AuthRepository(private val dataSource: DataSource) {
    private val log = LoggerFactory.getLogger(AuthRepository::class.java)

    fun findById(id: String): AuthUser? {
        val sql = "SELECT id, email, password_hash, password_salt, created_at FROM auth_users WHERE id = ?"
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, id)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) return rowToUser(rs)
                }
            }
        }
        return null
    }

    fun findByEmail(email: String): AuthUser? {
        val sql = "SELECT id, email, password_hash, password_salt, created_at FROM auth_users WHERE LOWER(email) = LOWER(?)"
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, email)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) return rowToUser(rs)
                }
            }
        }
        return null
    }

    fun create(email: String, passwordHash: String, passwordSalt: String): AuthUser {
        val sql = "INSERT INTO auth_users (id, email, password_hash, password_salt, created_at) VALUES (?, ?, ?, ?, ?) RETURNING id, created_at"
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                val id = UUID.randomUUID().toString()
                stmt.setString(1, id)
                stmt.setString(2, email.lowercase().trim())
                stmt.setString(3, passwordHash)
                stmt.setString(4, passwordSalt)
                val now = Timestamp.from(Instant.now())
                stmt.setTimestamp(5, now)
                stmt.executeQuery().use { rs ->
                    rs.next()
                    return AuthUser(
                        id = rs.getString("id"),
                        email = email.lowercase().trim(),
                        passwordHash = passwordHash,
                        passwordSalt = passwordSalt,
                        createdAt = rs.getTimestamp("created_at").time,
                    )
                }
            }
        }
    }

    fun storeRefreshToken(jti: String, userId: String, expiresAt: Instant) {
        val sql = "INSERT INTO refresh_tokens (jti, user_id, expires_at, created_at) VALUES (?, ?, ?, ?)"
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, jti)
                stmt.setString(2, userId)
                stmt.setTimestamp(3, Timestamp.from(expiresAt))
                stmt.setTimestamp(4, Timestamp.from(Instant.now()))
                stmt.executeUpdate()
            }
        }
    }

    fun findRefreshToken(jti: String): Pair<String, Instant>? {
        val sql = "SELECT user_id, expires_at FROM refresh_tokens WHERE jti = ?"
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, jti)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        return Pair(rs.getString("user_id"), rs.getTimestamp("expires_at").toInstant())
                    }
                }
            }
        }
        return null
    }

    fun deleteRefreshToken(jti: String) {
        val sql = "DELETE FROM refresh_tokens WHERE jti = ?"
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, jti)
                stmt.executeUpdate()
            }
        }
    }

    fun deleteRefreshTokensForUser(userId: String) {
        val sql = "DELETE FROM refresh_tokens WHERE user_id = ?"
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, userId)
                stmt.executeUpdate()
            }
        }
    }

    private fun rowToUser(rs: java.sql.ResultSet) = AuthUser(
        id = rs.getString("id"),
        email = rs.getString("email"),
        passwordHash = rs.getString("password_hash"),
        passwordSalt = rs.getString("password_salt"),
        createdAt = rs.getTimestamp("created_at").time,
    )
}
