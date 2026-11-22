package com.novoagentehb.app.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novoagentehb.app.bridges.ReminderScheduler
import com.novoagentehb.app.data.local.GoalDao
import com.novoagentehb.app.data.local.GoalItemEntity
import com.novoagentehb.app.data.local.GoalStatus
import com.novoagentehb.app.data.local.ReminderDao
import com.novoagentehb.app.data.local.ReminderItemEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val goalDao: GoalDao,
    private val reminderDao: ReminderDao,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {

    val goals: StateFlow<List<GoalItemEntity>> = goalDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val reminders: StateFlow<List<ReminderItemEntity>> = reminderDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addGoal(title: String, description: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            goalDao.upsert(GoalItemEntity(title = title.trim(), description = description.trim()))
        }
    }

    fun bumpProgress(goal: GoalItemEntity) {
        viewModelScope.launch {
            val next = (goal.progressPercent + 25).coerceAtMost(100)
            val status = if (next >= 100) GoalStatus.COMPLETED.name else goal.status
            goalDao.update(goal.copy(progressPercent = next, status = status, updatedAt = System.currentTimeMillis()))
        }
    }

    fun addReminderInMinutes(title: String, minutes: Int) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val trigger = System.currentTimeMillis() + minutes.coerceAtLeast(1) * 60_000L
            reminderScheduler.schedule(title.trim(), trigger)
        }
    }

    fun deleteGoal(id: Long) {
        viewModelScope.launch { goalDao.deleteById(id) }
    }
}
