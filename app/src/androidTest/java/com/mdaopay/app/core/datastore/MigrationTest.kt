package com.mdaopay.app.core.datastore

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import java.io.IOException

class MigrationTest {

    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun `migrate from version 1 to latest`() {
        helper.createDatabase(DB_NAME, 1).apply {
            execSQL("INSERT OR IGNORE INTO TxQueueEntity " +
                "(idempotencyKey, recipientAddress, weiAmount, nickname, displayAmount, createdAt, retryCount, lastError) " +
                "VALUES ('test_key', '0xabc', '1000', 'alice', '1.0', 1000, 0, NULL)")
            close()
        }
        helper.runMigrationsAndValidate(DB_NAME, 1, true)
    }

    companion object {
        private const val DB_NAME = "migration_test.db"
    }
}
