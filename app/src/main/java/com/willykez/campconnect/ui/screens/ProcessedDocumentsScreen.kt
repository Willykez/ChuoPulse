@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
package com.willykez.campconnect.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.willykez.campconnect.ui.theme.DeepBlue
import com.willykez.campconnect.ui.theme.PrimaryLightBlue
import com.willykez.campconnect.ui.theme.SuccessGreen
import com.willykez.campconnect.ui.theme.SuccessGreenLight
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.willykez.campconnect.data.docs.DocumentAnalysisService
import com.willykez.campconnect.data.docs.DocumentKind
import com.willykez.campconnect.data.local.ProcessedDocumentEntity
import com.willykez.campconnect.data.local.ProcessedDocumentRepository
import com.willykez.campconnect.data.model.AttachmentFile
import com.willykez.campconnect.data.model.Role
import com.willykez.campconnect.ui.components.AnalysisState
import com.willykez.campconnect.ui.components.AppBottomSheet
import com.willykez.campconnect.ui.components.ConfirmSheet
import com.willykez.campconnect.ui.components.DocumentViewerDialog
import com.willykez.campconnect.ui.components.FileKindBadge
import com.willykez.campconnect.ui.components.PickerField
import com.willykez.campconnect.ui.components.PickerItem
import com.willykez.campconnect.ui.components.PickerSheet
import com.willykez.campconnect.ui.components.SheetButtons
import com.willykez.campconnect.ui.components.ViewerAnalysis
import com.willykez.campconnect.viewmodel.CampusViewModel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private val Categories = listOf("Notes", "Syllabus", "Assignments", "Exam Revision", "Reference")

/**
 * The person's library of analysed documents. Anything analysed anywhere in the app lands here:
 * it appears at once as "Analysing", then shows its summary, topics, objectives and study tips.
 * Everything is stored on this phone.
 */
@Composable
fun ProcessedDocumentsScreen(
  viewModel: CampusViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val repository = remember(context) { ProcessedDocumentRepository(context) }
  val service = remember(context) { DocumentAnalysisService.get(context) }
  val documentsFlow = remember(repository) { repository.observeDocuments() }
  val documents by documentsFlow.collectAsState(initial = emptyList())
  val user by viewModel.currentUser.collectAsState()
  val catalog by viewModel.catalogCourses.collectAsState()
  val scope = rememberCoroutineScope()
  val clipboard = LocalClipboardManager.current

  var query by remember { mutableStateOf("") }
  var selectedCourse by remember { mutableStateOf("ALL") }
  var selectedCategory by remember { mutableStateOf("All Types") }
  var chosenFiles by remember { mutableStateOf<List<Uri>>(emptyList()) }
  var detailId by remember { mutableStateOf<String?>(null) }
  var readingId by remember { mutableStateOf<String?>(null) }
  var confirmDelete by remember { mutableStateOf<ProcessedDocumentEntity?>(null) }

  val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
    if (uris.isNotEmpty()) {
      val usable = uris.filter { uri ->
        val (name, mime) = service.describe(uri)
        DocumentKind.isAnalysable(name, mime)
      }
      if (usable.size < uris.size) {
        Toast.makeText(context, viewModel.t("Some files were skipped: that type can't be analysed.", "Faili zingine zimerukwa: aina hiyo haiwezi kuchambuliwa."), Toast.LENGTH_LONG).show()
      }
      chosenFiles = usable
    }
  }
  val addDocuments = { picker.launch(DocumentKind.pickerMimeTypes) }

  // Filters come from what is actually in the library, never from a fixed list.
  val courseFilters = listOf("ALL") + documents.map { it.courseCode }.distinct().sorted()
  val categoryFilters = listOf("All Types") + documents.map { it.category }.distinct().sorted()
  val course = selectedCourse.takeIf { it in courseFilters } ?: "ALL"
  val category = selectedCategory.takeIf { it in categoryFilters } ?: "All Types"
  val filtered = documents.filter { doc ->
    val textMatch = query.isBlank() || listOf(doc.title, doc.fileName, doc.extractedSummary, doc.keyTopics, doc.courseCode, doc.courseTitle, doc.tags)
      .any { it.contains(query.trim(), ignoreCase = true) }
    textMatch && (course == "ALL" || doc.courseCode.equals(course, true)) &&
      (category == "All Types" || doc.category.equals(category, true))
  }
  val analysing = documents.count { it.extractionStatus == DocumentAnalysisService.STATUS_PROCESSING }

  Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
    Spacer(Modifier.height(8.dp))
    // Header: AI badge, title, and where the analysis happens
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Box(
        Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(Brush.linearGradient(listOf(DeepBlue, PrimaryLightBlue))),
        contentAlignment = Alignment.Center
      ) { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(21.dp)) }
      Spacer(Modifier.width(10.dp))
      Column(Modifier.weight(1f)) {
        Text(viewModel.t("AI Summaries", "Muhtasari wa AI"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
          when {
            analysing > 0 -> viewModel.t("${documents.size} documents \u2022 $analysing analysing", "Hati ${documents.size} \u2022 $analysing zinachambuliwa")
            else -> viewModel.t("${documents.size} documents stored on this device", "Hati ${documents.size} zimehifadhiwa kwenye simu hii")
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Surface(shape = RoundedCornerShape(8.dp), color = SuccessGreenLight) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.CloudDone, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
          Spacer(Modifier.width(4.dp))
          Text(viewModel.t("On-device", "Kwenye simu"), style = MaterialTheme.typography.labelSmall, color = SuccessGreen, fontWeight = FontWeight.Bold)
        }
      }
    }
    Spacer(Modifier.height(12.dp))
    // Hero: the main action, always one tap away
    Card(
      shape = RoundedCornerShape(18.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
    ) {
      Column(Modifier.padding(16.dp)) {
        Text(
          viewModel.t("Turn any document into a study summary", "Geuza hati yoyote kuwa muhtasari wa kusoma"),
          style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
          viewModel.t(
            "PDF, Word, slides, sheets and text. Key topics, objectives and study tips are found on your phone; nothing is uploaded.",
            "PDF, Word, slaidi, majedwali na maandishi. Mada kuu, malengo na vidokezo hupatikana kwenye simu yako; hakuna kinachopakiwa."
          ),
          style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Button(
          onClick = { addDocuments() },
          colors = ButtonDefaults.buttonColors(containerColor = DeepBlue),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("analyse_document_btn")
        ) {
          Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
          Spacer(Modifier.width(8.dp))
          Text(viewModel.t("Analyse a document", "Changanua hati"), fontWeight = FontWeight.Bold)
        }
      }
    }
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
      value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth().testTag("summaries_search"),
      singleLine = true, shape = RoundedCornerShape(16.dp),
      placeholder = { Text(viewModel.t("Search summaries, topics or course codes", "Tafuta muhtasari, mada au msimbo wa kozi")) },
      leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
      trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Text("\u00D7", style = MaterialTheme.typography.titleLarge) } }
    )
    if (documents.isNotEmpty()) {
      Spacer(Modifier.height(8.dp))
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(courseFilters) { code ->
          FilterChip(selected = course == code, onClick = { selectedCourse = code }, label = { Text(code) })
        }
      }
      if (categoryFilters.size > 2) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(categoryFilters) { name ->
            FilterChip(selected = category == name, onClick = { selectedCategory = name }, label = { Text(name) })
          }
        }
      }
    }
    Spacer(Modifier.height(8.dp))

    if (filtered.isEmpty()) {
      Column(
        Modifier.fillMaxWidth().weight(1f).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(
          if (documents.isEmpty()) viewModel.t("Analyse your first document", "Changanua hati yako ya kwanza")
          else viewModel.t("No summaries match these filters", "Hakuna muhtasari unaolingana"),
          fontWeight = FontWeight.SemiBold
        )
        Text(
          if (documents.isEmpty()) viewModel.t(
            "Pick a PDF, photo, Word, PowerPoint, Excel or text file. You get a summary, key topics and study tips, saved here for offline use.",
            "Chagua PDF, picha, Word, PowerPoint, Excel au faili la maandishi. Utapata muhtasari, mada kuu na vidokezo vya kusoma."
          ) else viewModel.t("Clear the search or choose another filter.", "Futa utafutaji au chagua kichujio kingine."),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          style = MaterialTheme.typography.bodySmall
        )
        if (documents.isEmpty()) {
          Spacer(Modifier.height(14.dp))
          Button(onClick = { addDocuments() }, shape = RoundedCornerShape(14.dp), modifier = Modifier.testTag("analyse_first_document_btn")) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(viewModel.t("Analyse a document", "Changanua hati"))
          }
        }
      }
    } else {
      LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(filtered, key = { it.id }) { document ->
          DocumentCard(
            document = document,
            onTopic = { query = it },
            onDelete = { confirmDelete = document },
            onDetails = { detailId = document.id },
            onRead = { readingId = document.id },
            onRetry = { service.retry(document) },
            t = { en, sw -> viewModel.t(en, sw) }
          )
        }
        item { Spacer(Modifier.height(16.dp)) }
      }
    }
  }

  // ---------- Choose the course for the files just picked ----------
  if (chosenFiles.isNotEmpty()) {
    val titles = catalog[user.universityId].orEmpty()
    val codes = when (user.role) {
      Role.STUDENT -> user.enrolledCourses
      Role.LECTURER -> user.teachesCourses
      else -> titles.keys
    }
    val generalLabel = viewModel.t("General (no course)", "Jumla (bila kozi)")
    val options = codes.sorted().map { it to (titles[it] ?: it) } + ("GENERAL" to generalLabel)
    var chosenCourse by remember(chosenFiles) { mutableStateOf(options.firstOrNull()?.first) }
    var chosenCategory by remember(chosenFiles) { mutableStateOf("Notes") }
    var picking by remember { mutableStateOf(false) }
    val files = remember(chosenFiles) { chosenFiles.map { it to service.describe(it) } }

    AppBottomSheet(
      onDismissRequest = { chosenFiles = emptyList() },
      title = viewModel.t("Analyse documents", "Changanua hati"),
      subtitle = viewModel.t("The analysis happens on your phone. Your files are not uploaded.", "Uchambuzi unafanyika kwenye simu yako. Faili hazipakiwi."),
      tag = "analyse_sheet",
      footer = {
        SheetButtons(
          dismissLabel = viewModel.t("Cancel", "Ghairi"),
          onDismiss = { chosenFiles = emptyList() },
          confirmLabel = viewModel.t(
            if (files.size == 1) "Analyse" else "Analyse ${files.size} files",
            if (files.size == 1) "Changanua" else "Changanua faili ${files.size}"
          ),
          onConfirm = {
            val picked = options.firstOrNull { it.first == chosenCourse } ?: options.last()
            files.forEach { (uri, info) ->
              service.enqueue(
                DocumentAnalysisService.Request(
                  uri = uri, fileName = info.first, mimeType = info.second,
                  courseCode = picked.first, courseTitle = picked.second,
                  university = user.universityId, uploaderName = user.fullName, category = chosenCategory
                )
              )
            }
            chosenFiles = emptyList()
            Toast.makeText(context, viewModel.t("Analysing\u2026 it will appear in your list.", "Inachambua\u2026 itaonekana kwenye orodha yako."), Toast.LENGTH_SHORT).show()
          },
          enabled = chosenCourse != null,
          confirmIcon = Icons.Default.AutoAwesome,
          confirmTag = "start_analysis_btn"
        )
      }
    ) {
      files.forEach { (_, info) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
          FileKindBadge(DocumentKind.of(info.first, info.second), size = 40.dp)
          Spacer(Modifier.width(12.dp))
          Column(Modifier.weight(1f)) {
            Text(info.first, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(DocumentKind.of(info.first, info.second).label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
      Spacer(Modifier.height(10.dp))
      PickerField(
        label = viewModel.t("Course", "Kozi"),
        value = options.firstOrNull { it.first == chosenCourse }?.let { if (it.first == "GENERAL") it.second else "${it.first}  ${it.second}" },
        placeholder = viewModel.t("Choose a course", "Chagua kozi"),
        onClick = { picking = true },
        tag = "analyse_course_field"
      )
      Spacer(Modifier.height(12.dp))
      Text(viewModel.t("Type", "Aina"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(6.dp))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Categories.forEach { name ->
          FilterChip(selected = chosenCategory == name, onClick = { chosenCategory = name }, label = { Text(name) })
        }
      }
      Spacer(Modifier.height(8.dp))
      if (picking) {
        PickerSheet(
          title = viewModel.t("Course", "Kozi"),
          options = options.map { PickerItem(it.first, if (it.first == "GENERAL") it.second else "${it.first}  ${it.second}") },
          selectedId = chosenCourse,
          onSelect = { chosenCourse = it },
          onDismiss = { picking = false }
        )
      }
    }
  }

  // ---------- Full analysis ----------
  documents.firstOrNull { it.id == detailId }?.let { doc ->
    val kind = DocumentKind.of(doc.fileName, doc.mimeType)
    AppBottomSheet(
      onDismissRequest = { detailId = null },
      title = doc.title,
      subtitle = "${doc.courseCode} \u2022 ${doc.courseTitle}",
      tag = "analysis_sheet",
      footer = {
        OutlinedButton(
          onClick = { clipboard.setText(AnnotatedString(analysisText(doc))); Toast.makeText(context, viewModel.t("Summary copied", "Muhtasari umenakiliwa"), Toast.LENGTH_SHORT).show() },
          shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f).height(48.dp)
        ) { Icon(Icons.Default.ContentCopy, contentDescription = viewModel.t("Copy", "Nakili"), modifier = Modifier.size(18.dp)) }
        OutlinedButton(
          onClick = {
            context.startActivity(
              Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, doc.title).putExtra(Intent.EXTRA_TEXT, analysisText(doc)),
                viewModel.t("Share analysis", "Shiriki uchambuzi")
              )
            )
          },
          shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f).height(48.dp)
        ) { Icon(Icons.Default.IosShare, contentDescription = viewModel.t("Share", "Shiriki"), modifier = Modifier.size(18.dp)) }
        Button(
          onClick = { detailId = null; readingId = doc.id },
          shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(2.4f).height(48.dp).testTag("analysis_read_btn")
        ) {
          Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text(viewModel.t("Read document", "Soma hati"), fontWeight = FontWeight.SemiBold)
        }
      }
    ) {
      Text(
        "${kind.label} \u2022 ${formatSize(doc.fileSize)} \u2022 ${doc.pageCount} pages \u2022 ${doc.wordCount} words \u2022 ${doc.readingTimeMinutes} min read",
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Spacer(Modifier.height(14.dp))
      SectionTitle(viewModel.t("Summary", "Muhtasari"))
      Text(doc.extractedSummary, style = MaterialTheme.typography.bodyMedium)
      if (doc.keyTopics.isNotBlank()) {
        Spacer(Modifier.height(16.dp))
        SectionTitle(viewModel.t("Key topics", "Mada kuu"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          doc.keyTopics.split(',').map(String::trim).filter(String::isNotBlank).forEach { topic ->
            AssistChip(onClick = { detailId = null; query = topic }, label = { Text(topic) })
          }
        }
      }
      if (doc.learningObjectives.isNotBlank()) {
        Spacer(Modifier.height(16.dp))
        SectionTitle(viewModel.t("Learning objectives", "Malengo ya kujifunza"))
        doc.learningObjectives.lines().filter(String::isNotBlank).forEach {
          Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp).padding(top = 2.dp))
            Spacer(Modifier.width(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium)
          }
        }
      }
      if (doc.studyTips.isNotBlank()) {
        Spacer(Modifier.height(16.dp))
        SectionTitle(viewModel.t("Study tips", "Vidokezo vya kusoma"))
        doc.studyTips.lines().filter(String::isNotBlank).forEach {
          Text("\u2022  $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
      }
      Spacer(Modifier.height(16.dp))
      Text(
        viewModel.t(
          "Made on this phone. The summary picks the most important sentences from your document; it does not rewrite or add facts.",
          "Imetengenezwa kwenye simu hii. Muhtasari huchagua sentensi muhimu zaidi za hati yako; hauongezi ukweli mpya."
        ),
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Spacer(Modifier.height(8.dp))
    }
  }

  // ---------- Read (in-app viewer) ----------
  documents.firstOrNull { it.id == readingId }?.let { doc ->
    val kind = DocumentKind.of(doc.fileName, doc.mimeType)
    DocumentViewerDialog(
      attachment = AttachmentFile(
        uriString = doc.sourceUri,
        fileName = doc.fileName,
        fileSize = formatSize(doc.fileSize),
        mimeType = doc.mimeType,
        isImage = kind == DocumentKind.IMAGE,
        previewText = if (doc.sourceUri == null) analysisText(doc) else null
      ),
      authorOrUploaderName = doc.uploaderName,
      courseCode = doc.courseCode,
      onDismiss = { readingId = null },
      analysis = ViewerAnalysis(
        state = when (doc.extractionStatus) {
          DocumentAnalysisService.STATUS_COMPLETED -> AnalysisState.DONE
          DocumentAnalysisService.STATUS_PROCESSING -> AnalysisState.RUNNING
          else -> AnalysisState.FAILED
        },
        onAnalyse = { service.retry(doc) },
        onViewResult = { readingId = null; detailId = doc.id }
      ).takeIf { doc.sourceUri != null }
    )
  }

  confirmDelete?.let { doc ->
    ConfirmSheet(
      title = viewModel.t("Delete this summary?", "Futa muhtasari huu?"),
      message = viewModel.t(
        "\"${doc.title}\" and its copy of the file will be removed from this phone. The original file on your phone and anything shared with your campus are not touched.",
        "\"${doc.title}\" na nakala yake vitaondolewa kwenye simu hii. Faili asili na kilichoshirikiwa na chuo chako havitaguswa."
      ),
      confirmLabel = viewModel.t("Delete", "Futa"),
      dismissLabel = viewModel.t("Cancel", "Ghairi"),
      onDismiss = { confirmDelete = null },
      onConfirm = {
        scope.launch {
          service.delete(doc)
          confirmDelete = null
          Toast.makeText(context, viewModel.t("Summary deleted", "Muhtasari umefutwa"), Toast.LENGTH_SHORT).show()
        }
      },
      confirmTag = "confirm_delete_summary_btn"
    )
  }
}

@Composable
private fun DocumentCard(
  document: ProcessedDocumentEntity,
  onTopic: (String) -> Unit,
  onDelete: () -> Unit,
  onDetails: () -> Unit,
  onRead: () -> Unit,
  onRetry: () -> Unit,
  t: (String, String) -> String
) {
  val kind = DocumentKind.of(document.fileName, document.mimeType)
  val processing = document.extractionStatus == DocumentAnalysisService.STATUS_PROCESSING
  val failed = document.extractionStatus == DocumentAnalysisService.STATUS_FAILED
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(18.dp),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier
      .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
      .testTag("document_card_${document.id}")
  ) {
    Column(Modifier.fillMaxWidth().padding(14.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        FileKindBadge(kind)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
          Text(document.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
          Text(
            if (document.courseCode == "GENERAL") t("General", "Jumla") else "${document.courseCode} \u2022 ${document.courseTitle}",
            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis
          )
        }
        IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_summary_${document.id}")) {
          Icon(Icons.Default.DeleteOutline, contentDescription = t("Delete summary", "Futa muhtasari"), tint = MaterialTheme.colorScheme.error)
        }
      }
      Spacer(Modifier.height(10.dp))

      when {
        processing -> Column(
          Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            Text(t("Analysing\u2026 you can leave this screen.", "Inachambua\u2026 unaweza kuondoka kwenye skrini hii."), style = MaterialTheme.typography.bodyMedium)
          }
          Spacer(Modifier.height(10.dp))
          LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        failed -> Column(
          Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.errorContainer).padding(12.dp)
        ) {
          Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(document.extractedSummary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
          }
          TextButton(onClick = onRetry, modifier = Modifier.testTag("retry_${document.id}")) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(t("Retry", "Jaribu tena"))
          }
        }
        else -> {
          Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DeepBlue.copy(alpha = 0.06f))
              .border(1.dp, DeepBlue.copy(alpha = 0.12f), RoundedCornerShape(12.dp)).padding(12.dp)
          ) {
            Text(t("SUMMARY PREVIEW", "MUHTASARI MFUPI"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(document.extractedSummary, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
          }
          Spacer(Modifier.height(8.dp))
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            document.keyTopics.split(',').map(String::trim).filter(String::isNotBlank).take(5).forEach { topic ->
              AssistChip(onClick = { onTopic(topic) }, label = { Text(topic, style = MaterialTheme.typography.labelSmall) })
            }
          }
          Spacer(Modifier.height(6.dp))
          Text(
            "${formatSize(document.fileSize)}  \u2022  ${document.pageCount} pages  \u2022  ${document.wordCount} words  \u2022  ${document.readingTimeMinutes} min read",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
      Text(
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(document.timestamp)),
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Spacer(Modifier.height(4.dp))
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onDetails, enabled = !processing && !failed, modifier = Modifier.testTag("view_analysis_${document.id}")) {
          Text(t("View full analysis", "Angalia uchambuzi kamili"))
        }
        Spacer(Modifier.weight(1f))
        FilledTonalButton(
          onClick = onRead,
          enabled = !processing,
          shape = RoundedCornerShape(12.dp),
          contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
          modifier = Modifier.testTag("read_${document.id}")
        ) {
          Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text(t("Read", "Soma"))
        }
      }
    }
  }
}

@Composable
private fun SectionTitle(text: String) {
  Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
  Spacer(Modifier.height(4.dp))
}

private fun analysisText(doc: ProcessedDocumentEntity): String = buildString {
  append(doc.title).append("\n\n").append(doc.extractedSummary)
  if (doc.keyTopics.isNotBlank()) append("\n\nKey topics: ").append(doc.keyTopics)
  if (doc.learningObjectives.isNotBlank()) append("\n\nLearning objectives:\n").append(doc.learningObjectives)
  if (doc.studyTips.isNotBlank()) append("\n\nStudy tips:\n").append(doc.studyTips)
}

private fun formatSize(bytes: Long): String = when {
  bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
  bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
  bytes > 0 -> "$bytes B"
  else -> "\u2013"
}
