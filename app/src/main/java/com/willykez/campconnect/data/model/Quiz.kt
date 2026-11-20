package com.willykez.campconnect.data.model

enum class QuestionType { MULTIPLE_CHOICE, SHORT_ANSWER }

data class QuizQuestion(
  val id: String,
  val type: QuestionType,
  val text: String,
  val options: List<String> = emptyList(),
  val correctOption: Int? = null,
  val modelAnswer: String = "",
  val points: Int = 1
)

data class Quiz(
  val id: String,
  val courseCode: String,
  val courseTitle: String,
  val lecturerId: String,
  val lecturerName: String,
  val title: String,
  val instructions: String = "",
  val durationMinutes: Int,
  val opensAt: Long,
  val closesAt: Long,
  val questions: List<QuizQuestion>
) {
  val totalPoints: Int get() = questions.sumOf { it.points }

  fun withoutAnswers(): Quiz = copy(questions = questions.map { it.copy(correctOption = null, modelAnswer = "") })
}

enum class AttemptStatus { IN_PROGRESS, SUBMITTED, AUTO_SUBMITTED }

data class QuizAttempt(
  val id: String,
  val quizId: String,
  val studentId: String,
  val studentName: String,
  val studentNumber: String,
  val startedAt: Long,
  val deadlineAt: Long,
  val status: AttemptStatus = AttemptStatus.IN_PROGRESS,
  val answers: Map<String, String> = emptyMap(),
  val submittedAt: Long? = null,
  val awarded: Map<String, Int> = emptyMap(),
  val marked: Boolean = false
)

data class QuizScore(
  val choicePoints: Int,
  val choiceTotal: Int,
  val writtenPoints: Int,
  val writtenTotal: Int,
  val complete: Boolean
) {
  val points: Int get() = choicePoints + writtenPoints
  val total: Int get() = choiceTotal + writtenTotal
}

enum class QuizError {
  NOT_ALLOWED, UNKNOWN_COURSE, TITLE_REQUIRED, NO_QUESTIONS, BAD_QUESTION, BAD_DURATION, BAD_WINDOW,
  NOT_FOUND, NOT_OPEN_YET, CLOSED, ALREADY_SUBMITTED, TIME_UP
}

data class StartResult(val attempt: QuizAttempt? = null, val error: QuizError? = null)
