package com.antonchuraev.homesearchchecklist.feature.checklist.di

import com.antonchuraev.homesearchchecklist.core.common.api.getDatabaseBuilder
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistDao
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistFillDao
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistDatabase


internal val database: ChecklistDatabase = ChecklistDatabase.getRoomDatabase(
    getDatabaseBuilder<ChecklistDatabase>("ChecklistDatabase")
)

internal val checklistDao: ChecklistDao by lazy { database.checklistDao() }
internal val checklistFillDao: ChecklistFillDao by lazy { database.checklistFillDao() }


