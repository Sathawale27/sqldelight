/*
 * Copyright (C) 2018 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.sqldelight

import com.squareup.sqldelight.SqlDelightDatabase.Transaction
import com.squareup.sqldelight.db.SqlDatabaseHelper

/**
 * A transaction-aware [SupportSQLiteOpenHelper] wrapper which can queue work to be performed after
 * the active [Transaction] ends with [afterTransaction].
 */
abstract class SqlDelightDatabase(
  internal val helper: SqlDatabaseHelper
) {
  private val transactions = ThreadLocal<Transaction>()
  private val actions = ArrayList<() -> Unit>()

  fun afterTransaction(function: () -> Unit) {
    val transaction = transactions.get()
    if (transaction != null) {
      actions.add(function)
    } else {
      function()
    }
  }

  fun close() {
    helper.close()
  }

  fun transaction(body: Transaction.() -> Unit) {
    val parent = transactions.get()
    val transaction = Transaction()

    if (parent == null) {
      helper.getConnection().beginTransaction()
    }

    try {
      transactions.set(transaction)
      transaction.body()
      transaction.successful = true
    } catch(e: RollbackException) {
      if (parent != null) throw e
    } finally {
      transactions.set(parent)
      if (parent == null) {
        if (!transaction.successful || !transaction.childrenSuccessful) {
          helper.getConnection().rollbackTransaction()
        } else {
          helper.getConnection().commitTransaction()
          while (actions.isNotEmpty()) actions.removeAt(0).invoke()
        }
      } else {
        parent.childrenSuccessful = transaction.successful && transaction.childrenSuccessful
      }
    }
  }

  class Transaction {
    internal var successful = false
    internal var childrenSuccessful = true

    fun rollback(): Nothing = throw RollbackException()
  }

  private class RollbackException: RuntimeException()
}