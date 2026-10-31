package com.willykez.campconnect.data.model

enum class QuestionType { MULTIPLE_CHOICE, SHORT_ANSWER }

/** One question of a quiz. */
data class QuizQuestion(
  val id: String,
  val type: QuestionType,
  val text: String,
  val options: List<String> = emptyList(),
  /** Position in [options] of the right answer. Students never receive this. */
  val correctOption: Int? = null,
  /** What a good written answer contains, to guide marking. Students never receive this. */
  val modelAnswer: String = "",
  val points: Int = 1
)

/** A timed online quiz a lecturer sets for a course. */
data class Quiz(
  val id: String,
  val courseCode: String,
  val courseTitle: String,
  val lecturerId: String,
  val lecturerName: String,
  val title: String,
  val instructions: String = "",
  /** How long a student has once they press Start. */
  val durationMinutes: Int,
  /** Students can start from this moment... */
  val opensAt: Long,
  /** ...until this moment. Nobody can start after it, and no attempt runs past it. */
  val closesAt: Long,
  val questions: List<QuizQuestion>
) {
  val totalPoints: Int get() = questions.sumOf { it.points }

  /** The copy students receive: the questions without the answers. */
  fun withoutAnswers(): Quiz = copy(questions = questions.map { it.copy(correctOption = null, modelAnswer = "") })
}

enum class AttemptStatus { IN_PROGRESS, SUBMITTED, AUTO_SUBMITTED }

/** One student's go at a quiz. */
data class QuizAttempt(
  val id: String,
  val quizId: String,
  val studentId: String,
  val studentName: String,
  val studentNumber: String,
  val startedAt: Long,
  /** The moment the timer reaches zero: the start plus the duration, but never after the quiz closes. */
  val deadlineAt: Long,
  val status: AttemptStatus = AttemptStatus.IN_PROGRESS,
  /** question id -> the chosen option number (as text) or the written answer. */
  val answers: Map<String, String> = emptyMap(),
  val submittedAt: Long? = null,
  /** question id -> points the lecturer gave a written answer. */
  val awarded: Map<String, Int> = emptyMap(),
  val marked: Boolean = false
)

/** What an attempt scored. Multiple choice is marked at once; written answers wait for the lecturer. */
data class QuizScore(
  val choicePoints: Int,
  val choiceTotal: Int,
  val writtenPoints: Int,
  val writtenTotal: Int,
  /** True when there are no written answers to mark, or the lecturer has marked them. */
  val complete: Boolean
) {
  val points: Int get() = choicePoints + writtenPoints
  val total: Int get() = choiceTotal + writtenTotal
}

enum class QuizError {
  NOT_ALLOWED, UNKNOWN_COURSE, TITLE_REQUIRED, NO_QUESTIONS, BAD_QUESTION, BAD_DURATION, BAD_WINDOW,
  NOT_FOUND, NOT_OPEN_YET, CLOSED, ALREADY_SUBMITTED, TIME_UP
}

/** Result of pressing Start: either the attempt to show, or the reason there is none. */
data class StartResult(val attempt: QuizAttempt? = null, val error: QuizError? = null)
