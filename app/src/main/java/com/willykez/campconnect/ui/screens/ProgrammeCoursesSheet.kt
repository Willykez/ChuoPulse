package com.willykez.campconnect.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.willykez.campconnect.ui.components.AppBottomSheet
import com.willykez.campconnect.ui.components.SheetButtons
import com.willykez.campconnect.ui.theme.DeepBlue
import com.willykez.campconnect.ui.theme.WarmAmber
import com.willykez.campconnect.viewmodel.CampusViewModel

/**
 * Choose, by hand, which courses belong to a programme. Students of the programme see exactly the
 * ticked courses. "Not in a programme" lists the courses that were imported without one.
 */
@Composable
fun ProgrammeCoursesSheet(
  viewModel: CampusViewModel,
  programmes: List<String>,
  courses: List<Pair<String, String>>,
  catalog: Map<String, Set<String>>,
  initialProgramme: String?,
  onDismiss: () -> Unit
) {
  var programme by remember { mutableStateOf(initialProgramme?.takeIf { it in programmes } ?: programmes.firstOrNull().orEmpty()) }
  var selected by remember(programme) { mutableStateOf(catalog[programme].orEmpty()) }
  var filter by remember { mutableStateOf(0) } // 0 all, 1 not in any programme, 2 ticked
  var query by remember { mutableStateOf("") }

  val inAnyProgramme = catalog.values.flatten().toSet()
  val shown = courses.filter { (code, title) ->
    (query.isBlank() || code.contains(query, true) || title.contains(query, true)) &&
      when (filter) { 1 -> code !in inAnyProgramme; 2 -> code in selected; else -> true }
  }
  val unassignedCount = courses.count { it.first !in inAnyProgramme }

  AppBottomSheet(
    onDismissRequest = onDismiss,
    title = viewModel.t("Courses of a programme", "Kozi za programu"),
    subtitle = viewModel.t("Students of the programme see the ticked courses", "Wanafunzi wa programu wataona kozi zilizotiwa tiki"),
    tag = "programme_courses_sheet",
    footer = {
      SheetButtons(
        viewModel.t("Cancel", "Ghairi"), onDismiss, viewModel.t("Save (${selected.size})", "Hifadhi (${selected.size})"),
        { if (viewModel.setProgrammeCourses(programme, selected)) onDismiss() },
        enabled = programme.isNotBlank(), confirmTag = "programme_courses_save"
      )
    }
  ) {
    Spacer(Modifier.height(8.dp))
    Text(viewModel.t("Programme", "Programu"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
      items(programmes, key = { it }) { p ->
        FilterChip(selected = p == programme, onClick = { programme = p }, label = { Text(p) }, modifier = Modifier.testTag("pc_programme_$p"))
      }
    }

    SheetField(query, { query = it }, viewModel.t("Search courses", "Tafuta kozi"), "pc_search")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
      FilterChip(selected = filter == 0, onClick = { filter = 0 }, label = { Text(viewModel.t("All", "Zote")) })
      FilterChip(
        selected = filter == 1, onClick = { filter = 1 }, modifier = Modifier.testTag("pc_filter_unassigned"),
        label = { Text(viewModel.t("Not in a programme ($unassignedCount)", "Hazina programu ($unassignedCount)")) }
      )
      FilterChip(selected = filter == 2, onClick = { filter = 2 }, label = { Text(viewModel.t("Ticked", "Zilizotiwa tiki")) })
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
      Text(
        viewModel.t("${selected.size} of ${courses.size} ticked", "${selected.size} kati ya ${courses.size} zimetiwa tiki"),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)
      )
      TextButton(onClick = { selected = selected + shown.map { it.first } }, modifier = Modifier.testTag("pc_select_shown")) {
        Text(viewModel.t("Tick shown", "Tia tiki zilizoonyeshwa"))
      }
      TextButton(onClick = { selected = selected - shown.map { it.first }.toSet() }) { Text(viewModel.t("Untick", "Ondoa tiki")) }
    }

    if (courses.isEmpty()) Text(viewModel.t("Add courses first.", "Ongeza kozi kwanza."), color = MaterialTheme.colorScheme.onSurfaceVariant)
    shown.forEach { (code, title) ->
      val others = catalog.entries.filter { it.key != programme && code in it.value }.map { it.key }
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { selected = if (code in selected) selected - code else selected + code }.padding(vertical = 2.dp).testTag("pc_course_$code")
      ) {
        Checkbox(checked = code in selected, onCheckedChange = { on -> selected = if (on) selected + code else selected - code })
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
          Row {
            Text(code, fontWeight = FontWeight.Bold, color = DeepBlue)
            Spacer(Modifier.width(8.dp))
            Text(title, maxLines = 1, modifier = Modifier.weight(1f))
          }
          if (code !in inAnyProgramme) Text(viewModel.t("Not in any programme yet", "Haipo kwenye programu yoyote bado"), style = MaterialTheme.typography.labelSmall, color = WarmAmber)
          else if (others.isNotEmpty()) Text(viewModel.t("Also in: ${others.joinToString(", ")}", "Pia katika: ${others.joinToString(", ")}"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
    Spacer(Modifier.height(12.dp))
  }
}
