package com.squareup.sqldelight

import com.google.common.truth.Truth.assertThat
import com.squareup.sqldelight.db.SqlDatabaseConnection
import com.squareup.sqldelight.sqlite.jdbc.SqliteJdbcOpenHelper
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SqlDelightDatabaseTest {
  private lateinit var database: SqlDelightDatabase
  private lateinit var connection: SqlDatabaseConnection

  @Before fun setup() {
    val databaseHelper = SqliteJdbcOpenHelper()
    database = object : SqlDelightDatabase(databaseHelper) {}
    connection = databaseHelper.getConnection()
  }

  @After fun teardown() {
    database.close()
  }

  @Test fun `afterTransaction runs after transaction commits`() {
    val counter = AtomicInteger(0)
    database.transaction {
      database.afterTransaction { counter.incrementAndGet() }
      assertThat(counter.get()).isEqualTo(0)
    }

    assertThat(counter.get()).isEqualTo(1)
  }

  @Test fun `afterTransaction does not run after transaction rollbacks`() {
    val counter = AtomicInteger(0)
    database.transaction {
      database.afterTransaction { counter.incrementAndGet() }
      assertThat(counter.get()).isEqualTo(0)
      rollback()
    }

    assertThat(counter.get()).isEqualTo(0)
  }

  @Test fun `afterTransaction runs immediately with no transaction`() {
    val counter = AtomicInteger(0)
    database.afterTransaction { counter.incrementAndGet() }
    assertThat(counter.get()).isEqualTo(1)
  }

  @Test fun `afterTransaction runs after parent transaction commits`() {
    val counter = AtomicInteger(0)
    database.transaction {
      database.afterTransaction { counter.incrementAndGet() }
      assertThat(counter.get()).isEqualTo(0)

      database.transaction {
        database.afterTransaction { counter.incrementAndGet() }
        assertThat(counter.get()).isEqualTo(0)
      }

      assertThat(counter.get()).isEqualTo(0)
    }

    assertThat(counter.get()).isEqualTo(2)
  }

  @Test fun `afterTransaction does not run in nested transaction when parent rolls back`() {
    val counter = AtomicInteger(0)
    database.transaction {
      database.afterTransaction { counter.incrementAndGet() }
      assertThat(counter.get()).isEqualTo(0)

      database.transaction {
        database.afterTransaction { counter.incrementAndGet() }
      }

      rollback()
    }

    assertThat(counter.get()).isEqualTo(0)
  }

  @Test fun `afterTransaction does not run in nested transaction when nested rolls back`() {
    val counter = AtomicInteger(0)
    database.transaction {
      database.afterTransaction { counter.incrementAndGet() }
      assertThat(counter.get()).isEqualTo(0)

      database.transaction {
        database.afterTransaction { counter.incrementAndGet() }
        rollback()
      }

      throw AssertionError()
    }

    assertThat(counter.get()).isEqualTo(0)
  }
}
