package com.antonchuraev.homesearchchecklist.feature.checklist.data.db

import androidx.room3.withWriteTransaction

/**
 * Runs a block inside a single Room write transaction spanning every table of
 * [ChecklistDatabase].
 *
 * Why this exists: a few operations write to BOTH the `checklists` and `checklist_fills`
 * tables and MUST be atomic, otherwise an observer of one table (e.g. MainScreenViewModel
 * collecting `repository.checklists`, which triggers a sync push) can observe an intermediate
 * state between the two writes. The drag-to-reorder path is the motivating case: it persists a
 * reordered fill AND its template, and a push firing in the gap uploaded the stale half and
 * stamped it with a fresh `updatedAt`, which the real-time listener echo then merged back over
 * the just-made local change (a cross-device data-loss-shaped bug — old order resurrected).
 *
 * Room defers its InvalidationTracker notifications until a transaction commits, so wrapping
 * both writes in one transaction collapses them into a SINGLE post-commit emission carrying the
 * final state — the push can never see the half-written intermediate.
 *
 * Abstracted behind an interface so [com.antonchuraev.homesearchchecklist.feature.checklist
 * .data.repository.ChecklistRepositoryImpl] stays unit-testable without a real Room database
 * (tests provide a pass-through runner that just invokes the block).
 */
fun interface ChecklistTransactionRunner {
    // Non-generic (returns Unit): a Kotlin `fun interface` may not declare a method with type
    // parameters, and every cross-table write that needs this (reorder) is a Unit-returning
    // command. Keeping it a SAM lets tests pass a `{ block -> block() }` pass-through lambda.
    suspend fun withTransaction(block: suspend () -> Unit)
}

/**
 * Production [ChecklistTransactionRunner] backed by Room's writer-connection transaction.
 *
 * [androidx.room3.withWriteTransaction] acquires the write connection and runs [block] in an
 * IMMEDIATE transaction; suspend DAO calls made inside join it via the coroutine's
 * TransactionElement. Available on all KMP targets (Android, iOS, wasmJs/OPFS) in Room 3.
 */
internal class RoomChecklistTransactionRunner(
    private val database: ChecklistDatabase,
) : ChecklistTransactionRunner {
    override suspend fun withTransaction(block: suspend () -> Unit) {
        database.withWriteTransaction { block() }
    }
}
