package com.mdaopay.paymaster

import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

class NicknameRepository(private val dataSource: DataSource) {
    private val log = LoggerFactory.getLogger(NicknameRepository::class.java)

    fun findByNickname(nickname: String): NicknameEntry? {
        val sql = "SELECT nickname, address, created_at FROM nicknames WHERE LOWER(nickname) = LOWER(?)"
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, nickname)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        return NicknameEntry(
                            nickname = rs.getString("nickname"),
                            address = rs.getString("address"),
                            registeredAt = rs.getTimestamp("created_at").time,
                        )
                    }
                }
            }
        }
        return null
    }

    fun findByAddress(address: String): NicknameEntry? {
        val sql = "SELECT nickname, address, created_at FROM nicknames WHERE LOWER(address) = LOWER(?)"
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, address)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        return NicknameEntry(
                            nickname = rs.getString("nickname"),
                            address = rs.getString("address"),
                            registeredAt = rs.getTimestamp("created_at").time,
                        )
                    }
                }
            }
        }
        return null
    }

    fun insert(nickname: String, address: String): Boolean {
        val sql = """
            INSERT INTO nicknames (nickname, address, created_at, updated_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (LOWER(nickname)) DO NOTHING
        """.trimIndent()
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, nickname.lowercase())
                stmt.setString(2, address.lowercase())
                val now = Timestamp.from(Instant.now())
                stmt.setTimestamp(3, now)
                stmt.setTimestamp(4, now)
                return stmt.executeUpdate() > 0
            }
        }
    }

    fun nicknameExists(nickname: String): Boolean {
        val sql = "SELECT 1 FROM nicknames WHERE LOWER(nickname) = LOWER(?)"
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, nickname)
                stmt.executeQuery().use { rs ->
                    return rs.next()
                }
            }
        }
    }

    fun addressHasNickname(address: String): Boolean {
        val sql = "SELECT 1 FROM nicknames WHERE LOWER(address) = LOWER(?)"
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, address)
                stmt.executeQuery().use { rs ->
                    return rs.next()
                }
            }
        }
    }

    fun count(): Int {
        val sql = "SELECT COUNT(*) FROM nicknames"
        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.executeQuery().use { rs ->
                    if (rs.next()) return rs.getInt(1)
                }
            }
        }
        return 0
    }
}
