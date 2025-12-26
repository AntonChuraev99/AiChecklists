package com.antonchuraev.homesearchchecklist.feature.checklist.di

import com.antonchuraev.homesearchchecklist.core.common.api.getDatabaseBuilder
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistDao
import com.antonchuraev.homesearchchecklist.feature.checklist.data.db.ChecklistDatabase


internal val database: ChecklistDatabase = ChecklistDatabase.getRoomDatabase(
    getDatabaseBuilder<ChecklistDatabase>("ChecklistDatabase")
)

internal val checklistDao: ChecklistDao by lazy { database.checklistDao() }


