@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.willykez.campconnect.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.ShortText
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.willykez.campconnect.data.model.QuestionType
import com.willykez.campconnect.data.model.QuizQuestion
import com.willykez.campconnect.ui.components.ActionButton
import com.willykez.campconnect.ui.components.DueDateTimeField
import com.willykez.campconnect.ui.components.PickerField
import com.willykez.campconnect.ui.components.PickerItem
import com.willykez.campconnect.ui.components.PickerSheet
import com.willykez.campconnect.ui.components.SoftIconButton
import com.willykez.campconnect.ui.theme.DangerRed
import com.willykez.campconnect.ui.theme.DeepBlue
import com.willykez.campconnect.ui.theme.SuccessGreen
import com.willykez.campconnect.util.DeadlineTime
import com.willykez.campconnect.viewmodel.CampusViewModel

/**
 * Full-screen quiz editor in the style of a form builder.
 *
 * Two tabs: Questions (the form itself) and Settings (course, time allowed, when it opens and closes).
 * Question cards expand when tapped. Students see the finished quiz as one scrolling form.
 */
@Composable
fun QuizEditorScreen(viewModel: CampusViewModel, onDismiss: () -> Unit) {
  val t = { en: String, sw: String -> viewModel.t(en, sw) }
  val courses = remember { viewModel.schedulableCourses() }
  var course by remember { mutableStateOf(courses.firstOrNull()?.first) }
  var pickingCourse by remember { mutableStateOf(false) }
  var title by remember { mutableStateOf("") }
  var instructions by remember { mutableStateOf("") }
  var duration by remember { mutableStateOf("20") }
  var opens by remember { mutableStateOf(DeadlineTime.format(nextQuarterHour())) }
  var closes by remember { mutableStateOf(DeadlineTime.format(nextQuarterHour() + 2 * 3_600_000L)) }
  val drafts = remember { mutableStateListOf(DraftQuestion(QuestionType.MULTIPLE_CHOICE)) }
  var expanded by remember { mutableIntStateOf(0) }
  var tab by remember { mutableIntStateOf(0) }
  var error by remember { mutableStateOf<String?>(null) }

  fun publish() {
    val questions = drafts.map { d ->
      QuizQuestion(
        id = "", type = d.type, text = d.text,
        options = if (d.type == QuestionType.MULTIPLE_CHOICE) d.options.map { it.trim() } else emptyList(),
        correctOption = if (d.type == QuestionType.MULTIPLE_CHOICE) d.correct else null,
        modelAnswer = d.model.trim(), points = d.points.toIntOrNull() ?: 0
      )
    }
    val e = viewModel.createQuiz(
      course.orEmpty(), title, instructions, duration.toIntOrNull() ?: 0,
      DeadlineTime.parse(opens) ?: 0L, DeadlineTime.parse(closes) ?: 0L, questions
    )
    if (e == null) onDismiss() else error = e
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
  ) {
    BackHandler(onBack = onDismiss)
    Surface(modifier = Modifier.fillMaxSize().testTag("quiz_editor_screen"), color = DeepBlue.copy(alpha = 0.05f).compositeOver(MaterialTheme.colorScheme.background)) {
      Column(Modifier.fillMaxSize()) {
        // Top bar
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
          Column(Modifier.statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
              IconButton(onClick = onDismiss, modifier = Modifier.size(52.dp).testTag("quiz_editor_close")) {
                Icon(Icons.Default.Close, contentDescription = t("Close", "Funga"))
              }
              Column(Modifier.weight(1f)) {
                Text(t("New quiz", "Jaribio jipya"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                  t("${drafts.size} questions \u2022 ${drafts.sumOf { it.points.toIntOrNull() ?: 0 }} points", "Maswali ${drafts.size} \u2022 alama ${drafts.sumOf { it.points.toIntOrNull() ?: 0 }}"),
                  style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              ActionButton(
                label = t("Publish", "Chapisha"), onClick = { publish() }, filled = true,
                modifier = Modifier.padding(end = 8.dp), tag = "publish_quiz_btn"
              )
            }
            TabRow(selectedTabIndex = tab, containerColor = Color.Transparent, contentColor = DeepBlue) {
              Tab(selected = tab == 0, onClick = { tab = 0 }, modifier = Modifier.height(52.dp).testTag("quiz_tab_questions")) {
                Text(t("Questions", "Maswali"), fontWeight = FontWeight.Bold)
              }
              Tab(selected = tab == 1, onClick = { tab = 1 }, modifier = Modifier.height(52.dp).testTag("quiz_tab_settings")) {
                Text(t("Settings", "Mipangilio"), fontWeight = FontWeight.Bold)
              }
            }
          }
        }

        error?.let {
          Text(
            it, color = DangerRed, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth().background(DangerRed.copy(alpha = 0.08f)).padding(12.dp).testTag("quiz_error")
          )
        }

        if (tab == 0) {
          LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            item {
              Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
              ) {
                Column {
                  Box(Modifier.fillMaxWidth().height(10.dp).background(DeepBlue))
                  Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    TextField(
                      value = title, onValueChange = { title = it },
                      placeholder = { Text(t("Untitled quiz", "Jaribio lisilo na jina"), style = MaterialTheme.typography.headlineSmall) },
                      textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                      singleLine = true,
                      colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = DeepBlue),
                      modifier = Modifier.fillMaxWidth().testTag("quiz_title_input")
                    )
                    TextField(
                      value = instructions, onValueChange = { instructions = it },
                      placeholder = { Text(t("Description or instructions (optional)", "Maelezo au maelekezo (si lazima)")) },
                      colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = DeepBlue),
                      modifier = Modifier.fillMaxWidth()
                    )
                  }
                }
              }
            }
            itemsIndexed(drafts) { i, d ->
              QuestionCard(
                number = i + 1,
                draft = d,
                expanded = expanded == i,
                isFirst = i == 0,
                isLast = i == drafts.lastIndex,
                canDelete = drafts.size > 1,
                onExpand = { expanded = i },
                onDuplicate = { drafts.add(i + 1, d.copyOf()); expanded = i + 1 },
                onMoveUp = { if (i > 0) { val x = drafts.removeAt(i); drafts.add(i - 1, x); expanded = i - 1 } },
                onMoveDown = { if (i < drafts.lastIndex) { val x = drafts.removeAt(i); drafts.add(i + 1, x); expanded = i + 1 } },
                onDelete = { drafts.removeAt(i); expanded = (i - 1).coerceAtLeast(0) },
                t = t
              )
            }
            item {
              Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                ActionButton(
                  label = t("Multiple choice", "Chaguo nyingi"), icon = Icons.Default.RadioButtonChecked,
                  onClick = { drafts.add(DraftQuestion(QuestionType.MULTIPLE_CHOICE)); expanded = drafts.lastIndex },
                  filled = true, modifier = Modifier.weight(1f), tag = "add_choice_question_btn"
                )
                ActionButton(
                  label = t("Written answer", "Jibu la kuandika"), icon = Icons.Default.ShortText,
                  onClick = { drafts.add(DraftQuestion(QuestionType.SHORT_ANSWER)); expanded = drafts.lastIndex },
                  modifier = Modifier.weight(1f), tag = "add_written_question_btn"
                )
              }
            }
          }
        } else {
          Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            PickerField(
              label = t("Course", "Kozi"),
              value = courses.firstOrNull { it.first == course }?.let { "${it.first}  ${it.second}" },
              placeholder = t("Choose a course", "Chagua kozi"),
              onClick = { pickingCourse = true }, tag = "quiz_course_field"
            )
            OutlinedTextField(
              value = duration, onValueChange = { duration = it.filter(Char::isDigit).take(3) },
              label = { Text(t("Time allowed once started (minutes)", "Muda baada ya kuanza (dakika)")) },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth().testTag("quiz_duration_input")
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf("10", "20", "30", "45", "60").forEach { m ->
                FilterChip(selected = duration == m, onClick = { duration = m }, label = { Text("$m min") })
              }
            }
            DueDateTimeField(valueText = opens, onValueChange = { opens = it }, label = t("Opens (EAT)", "Inafunguliwa (EAT)"), tag = "quiz_opens_input")
            DueDateTimeField(valueText = closes, onValueChange = { closes = it }, label = t("Closes (EAT)", "Inafungwa (EAT)"), tag = "quiz_closes_input")
            Text(
              t(
                "Students see the questions only after pressing Start. The clock then runs for the time you set, and answers are marked automatically for multiple choice.",
                "Wanafunzi huona maswali baada ya kubonyeza Anza. Saa huanza kuhesabu kwa muda uliouweka, na chaguo nyingi hukaguliwa kiotomatiki."
              ),
              style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }
    if (pickingCourse) {
      PickerSheet(
        title = t("Course", "Kozi"), options = courses.map { PickerItem(it.first, "${it.first}  ${it.second}") },
        selectedId = course, onSelect = { course = it }, onDismiss = { pickingCourse = false }
      )
    }
  }
}

private fun Color.compositeOver(background: Color): Color {
  val a = alpha
  return Color(
    red = red * a + background.red * (1 - a),
    green = green * a + background.green * (1 - a),
    blue = blue * a + background.blue * (1 - a),
    alpha = 1f
  )
}

/** A question card. Tap to expand and edit; collapsed it shows a one-line summary. */
@Composable
private fun QuestionCard(
  number: Int,
  draft: DraftQuestion,
  expanded: Boolean,
  isFirst: Boolean,
  isLast: Boolean,
  canDelete: Boolean,
  onExpand: () -> Unit,
  onDuplicate: () -> Unit,
  onMoveUp: () -> Unit,
  onMoveDown: () -> Unit,
  onDelete: () -> Unit,
  t: (String, String) -> String
) {
  val isChoice = draft.type == QuestionType.MULTIPLE_CHOICE
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = if (expanded) 4.dp else 1.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, if (expanded) DeepBlue.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
      .testTag("question_card_$number")
  ) {
    Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
      Box(Modifier.width(6.dp).fillMaxHeight().background(if (expanded) DeepBlue else Color.Transparent))
      Column(Modifier.weight(1f)) {
        // Summary row, always visible. Tapping it expands the card.
        Row(
          Modifier.fillMaxWidth().clickable { onExpand() }.padding(horizontal = 14.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(Modifier.weight(1f)) {
            Text(
              "$number. " + draft.text.ifBlank { t("Untitled question", "Swali lisilo na jina") },
              style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
              maxLines = if (expanded) 3 else 1, overflow = TextOverflow.Ellipsis,
              color = if (draft.text.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            Text(
              (if (isChoice) t("Multiple choice \u2022 ${draft.options.size} options", "Chaguo nyingi \u2022 chaguo ${draft.options.size}") else t("Written answer", "Jibu la kuandika")) +
                " \u2022 " + t("${draft.points} pt", "alama ${draft.points}"),
              style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (expanded) {
          Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
            // Type dropdown
            var menuOpen by remember { mutableStateOf(false) }
            Box {
              Surface(
                onClick = { menuOpen = true },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("question_type_$number")
              ) {
                Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                  Icon(if (isChoice) Icons.Default.RadioButtonChecked else Icons.Default.ShortText, contentDescription = null, tint = DeepBlue)
                  Spacer(Modifier.width(10.dp))
                  Text(
                    if (isChoice) t("Multiple choice", "Chaguo nyingi") else t("Written answer", "Jibu la kuandika"),
                    modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold
                  )
                  Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
              }
              DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                  text = { Text(t("Multiple choice", "Chaguo nyingi")) },
                  leadingIcon = { Icon(Icons.Default.RadioButtonChecked, contentDescription = null) },
                  onClick = { draft.type = QuestionType.MULTIPLE_CHOICE; menuOpen = false }
                )
                DropdownMenuItem(
                  text = { Text(t("Written answer", "Jibu la kuandika")) },
                  leadingIcon = { Icon(Icons.Default.ShortText, contentDescription = null) },
                  onClick = { draft.type = QuestionType.SHORT_ANSWER; menuOpen = false }
                )
              }
            }
            Spacer(Modifier.height(10.dp))
            TextField(
              value = draft.text, onValueChange = { draft.text = it },
              placeholder = { Text(t("Question", "Swali")) },
              colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                focusedIndicatorColor = DeepBlue
              ),
              modifier = Modifier.fillMaxWidth().testTag("question_text_$number")
            )
            Spacer(Modifier.height(10.dp))

            if (isChoice) {
              Text(
                t("Tap an option to mark it as the correct answer.", "Gusa chaguo kuliweka kama jibu sahihi."),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(Modifier.height(6.dp))
              draft.options.forEachIndexed { i, option ->
                val correct = draft.correct == i
                Row(
                  Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (correct) SuccessGreen.copy(alpha = 0.10f) else Color.Transparent)
                    .border(1.dp, if (correct) SuccessGreen.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  IconButton(onClick = { draft.correct = i }, modifier = Modifier.size(52.dp).testTag("question_${number}_correct_$i")) {
                    Icon(
                      if (correct) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                      contentDescription = if (correct) t("Correct answer", "Jibu sahihi") else t("Mark as correct", "Weka kama sahihi"),
                      tint = if (correct) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  TextField(
                    value = option, onValueChange = { draft.options[i] = it }, singleLine = true,
                    placeholder = { Text(t("Option ${i + 1}", "Chaguo ${i + 1}")) },
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                    modifier = Modifier.weight(1f).testTag("question_${number}_option_$i")
                  )
                  if (draft.options.size > 2) {
                    IconButton(
                      onClick = {
                        draft.options.removeAt(i)
                        draft.correct = if (draft.correct == i) 0 else if (draft.correct > i) draft.correct - 1 else draft.correct
                      },
                      modifier = Modifier.size(52.dp)
                    ) { Icon(Icons.Default.Close, contentDescription = t("Remove option", "Ondoa chaguo")) }
                  }
                }
              }
              if (draft.options.size < 6) {
                ActionButton(
                  label = t("Add option", "Ongeza chaguo"), icon = Icons.Default.Add,
                  onClick = { draft.options.add("") },
                  modifier = Modifier.padding(top = 6.dp).testTag("question_${number}_add_option")
                )
              }
            } else {
              OutlinedTextField(
                value = draft.model, onValueChange = { draft.model = it },
                label = { Text(t("What a good answer contains (only you see this)", "Jibu zuri lina nini (wewe tu unaona)")) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
              )
            }

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
              value = draft.points, onValueChange = { draft.points = it.filter(Char::isDigit).take(3) },
              label = { Text(t("Points", "Alama")) }, singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.width(130.dp)
            )

            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(Modifier.height(10.dp))
            // Toolbar: duplicate, move, delete
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
              SoftIconButton(Icons.Default.ContentCopy, t("Duplicate", "Nakili"), onDuplicate, tag = "question_${number}_duplicate")
              SoftIconButton(Icons.Default.ArrowUpward, t("Move up", "Sogeza juu"), { if (!isFirst) onMoveUp() }, tag = "question_${number}_up")
              SoftIconButton(Icons.Default.ArrowDownward, t("Move down", "Sogeza chini"), { if (!isLast) onMoveDown() }, tag = "question_${number}_down")
              Spacer(Modifier.weight(1f))
              if (canDelete) {
                SoftIconButton(Icons.Default.Delete, t("Delete question", "Futa swali"), onDelete, color = DangerRed, tag = "question_${number}_delete")
              }
            }
          }
        }
      }
    }
  }
}
