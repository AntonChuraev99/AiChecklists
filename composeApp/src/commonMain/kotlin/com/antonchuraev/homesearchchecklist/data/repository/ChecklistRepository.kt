package com.antonchuraev.homesearchchecklist.data.repository

import com.antonchuraev.homesearchchecklist.domain.model.Checklist
import kotlinx.coroutines.flow.Flow


class ChecklistRepository() {

    val checklists: Flow<List<Checklist>> = TODO()


    fun addChecklist(checklist: Checklist){
        TODO()
    }

    fun deleteChecklist(checklist: Checklist){
        TODO()
    }
}