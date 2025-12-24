package com.antonchuraev.homesearchchecklist.feature.checklist.data.di

import com.antonchuraev.homesearchchecklist.core.common.api.getDatabaseBuilder
import com.antonchuraev.homesearchchecklist.feature.checklist.data.local.ChecklistDao
import com.antonchuraev.homesearchchecklist.feature.checklist.data.local.ChecklistDatabase


internal val database: ChecklistDatabase = ChecklistDatabase.getRoomDatabase(
    getDatabaseBuilder<ChecklistDatabase>("ChecklistDatabase")
)

internal val checklistDao: ChecklistDao by lazy { database.checklistDao() }


