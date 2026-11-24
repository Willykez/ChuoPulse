package com.willykez.campconnect.viewmodel

import android.content.Context
import android.net.Uri
import com.willykez.campconnect.data.docs.CatalogContext
import com.willykez.campconnect.data.docs.LecturerRef
import com.willykez.campconnect.data.docs.PSession
import com.willykez.campconnect.data.docs.TimetableAiClient
import com.willykez.campconnect.data.docs.TimetableLogic
import com.willykez.campconnect.data.docs.TimetableProposal
import com.willykez.campconnect.data.docs.TimetableSource
import com.willykez.campconnect.data.repository.CampusRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AssistantPhase {
  data object Idle : AssistantPhase
  data class Working(val label: String) : AssistantPhase
  data class Review(val proposal: TimetableProposal, val fileName: String) : AssistantPhase
  data class Done(val report: CampusRepository.TimetableApplyReport) : AssistantPhase
  data class Failed(val message: String) : AssistantPhase
}

class TimetableAssistantModel(
  private val repository: CampusRepository,
  private val scope: CoroutineScope
) {
  private val _phase = MutableStateFlow<AssistantPhase>(AssistantPhase.Idle)
  val phase: StateFlow<AssistantPhase> = _phase.asStateFlow()

  private var catalog: CatalogContext? = null
  private var fileName = ""

  fun reset() {
    catalog = null
    _phase.value = AssistantPhase.Idle
  }

  fun lecturers(): List<LecturerRef> = catalog?.lecturers.orEmpty()

  fun start(context: Context, uri: Uri, mimeType: String?) {
    val appContext = context.applicationContext
    val ctx = repository.timetableContext()
    catalog = ctx
    scope.launch(Dispatchers.IO) {
      val name = TimetableSource.displayName(appContext, uri)
      fileName = name
      _phase.value = AssistantPhase.Working("Reading $name")
      val text = try {
        TimetableSource.textOf(appContext, uri, name, mimeType)
      } catch (e: Exception) {
        _phase.value = AssistantPhase.Failed("That file could not be read. Try a PDF, Word, Excel or CSV file.")
        return@launch
      }
      if (text.isBlank()) {
        _phase.value = AssistantPhase.Failed("No text was found in that file.")
        return@launch
      }
      _phase.value = AssistantPhase.Working("The AI is reading the timetable")
      when (val out = TimetableAiClient.propose(text, name, TimetableLogic.contextJson(ctx))) {
        is TimetableAiClient.Outcome.Unavailable -> _phase.value = AssistantPhase.Failed(TimetableAiClient.explain(out.reason))
        is TimetableAiClient.Outcome.Success -> {
          val proposal = TimetableLogic.parse(out.proposal, ctx)
          _phase.value = if (proposal.isEmpty) {
            AssistantPhase.Failed("The AI found no courses or sessions in that file.")
          } else AssistantPhase.Review(proposal, name)
        }
      }
    }
  }

  private fun change(update: (TimetableProposal) -> TimetableProposal) {
    val current = _phase.value as? AssistantPhase.Review ?: return
    val ctx = catalog ?: return
    _phase.value = current.copy(proposal = TimetableLogic.validate(update(current.proposal), ctx))
  }

  fun toggleProgramme(name: String) = change { p ->
    p.copy(programmes = p.programmes.map { if (it.name == name) it.copy(include = !it.include) else it })
  }

  fun toggleCourse(code: String) = change { p ->
    p.copy(courses = p.courses.map { if (it.code == code) it.copy(include = !it.include) else it })
  }

  fun toggleAssignment(index: Int) = change { p ->
    p.copy(assignments = p.assignments.mapIndexed { i, a -> if (i == index) a.copy(include = !a.include) else a })
  }

  fun toggleSession(id: Int) = change { p ->
    p.copy(sessions = p.sessions.map { if (it.id == id) it.copy(include = !it.include) else it })
  }

  fun editSession(id: Int, edit: (PSession) -> PSession) = change { p ->
    p.copy(sessions = p.sessions.map { if (it.id == id) edit(it) else it })
  }

  fun apply() {
    val current = _phase.value as? AssistantPhase.Review ?: return
    _phase.value = AssistantPhase.Working("Adding ${current.proposal.itemsToApply} items to the timetable")
    scope.launch(Dispatchers.IO) {
      _phase.value = AssistantPhase.Done(repository.applyTimetable(current.proposal))
    }
  }
}
