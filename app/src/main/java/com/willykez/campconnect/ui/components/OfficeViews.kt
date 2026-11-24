package com.willykez.campconnect.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willykez.campconnect.data.docs.ArchiveEntry
import com.willykez.campconnect.data.docs.DocBlock
import com.willykez.campconnect.data.docs.DocumentKind
import com.willykez.campconnect.data.docs.OfficeReader
import com.willykez.campconnect.data.docs.SheetData
import com.willykez.campconnect.data.model.AttachmentFile
import com.willykez.campconnect.ui.theme.DeepBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

private val PageBorder = Color(0xFFE2E8F0)
private val PageSurface = Color(0xFFFFFFFF)
private val HeaderFill = Color(0xFFEFF6FF)
private val RowNumberFill = Color(0xFFF8FAFC)
private const val CELL_WIDTH = 120
private const val NUMBER_WIDTH = 44

private val WORD_EXTENSIONS = setOf("docx", "odt")
private val SHEET_EXTENSIONS = setOf("xlsx", "ods", "csv", "tsv")

private sealed interface Loaded {
  data object Loading : Loaded
  data class Blocks(val items: List<DocBlock>) : Loaded
  data class Sheets(val items: List<SheetData>) : Loaded
  data class Entries(val items: List<ArchiveEntry>) : Loaded
  data object Fallback : Loaded
}

fun isStructuredOffice(fileName: String): Boolean {
  val ext = DocumentKind.extensionOf(fileName)
  return ext in WORD_EXTENSIONS || ext in SHEET_EXTENSIONS || ext == "zip"
}

@Composable
internal fun OfficePages(
  attachment: AttachmentFile,
  kind: DocumentKind,
  onPageLabel: (String?) -> Unit,
  onOpen: () -> Unit
) {
  val context = LocalContext.current
  val ext = DocumentKind.extensionOf(attachment.fileName)
  if (!isStructuredOffice(attachment.fileName)) {
    ExtractedPages(attachment = attachment, kind = kind, onPageLabel = onPageLabel, onOpen = onOpen)
    return
  }
  val state by produceState<Loaded>(Loaded.Loading, attachment.uriString) {
    value = withContext(Dispatchers.IO) {
      var temp: File? = null
      try {
        val file = File.createTempFile("view", "." + ext.filter { it.isLetterOrDigit() }.ifBlank { "bin" }, context.cacheDir)
        temp = file
        val input = context.contentResolver.openInputStream(Uri.parse(attachment.uriString)) ?: throw IOException("cannot open")
        input.use { src -> file.outputStream().use { dst -> src.copyTo(dst) } }
        when {
          ext in WORD_EXTENSIONS -> OfficeReader.readWord(file, ext).let { if (it.isEmpty()) Loaded.Fallback else Loaded.Blocks(it) }
          ext in SHEET_EXTENSIONS -> OfficeReader.readSheets(file, ext).filter { it.rows.isNotEmpty() }
            .let { if (it.isEmpty()) Loaded.Fallback else Loaded.Sheets(it) }
          else -> Loaded.Entries(OfficeReader.readArchive(file))
        }
      } catch (e: Exception) {
        Loaded.Fallback
      } finally {
        temp?.delete()
      }
    }
  }
  when (val s = state) {
    Loaded.Loading -> LoadingIndicator()
    Loaded.Fallback -> ExtractedPages(attachment = attachment, kind = kind, onPageLabel = onPageLabel, onOpen = onOpen)
    is Loaded.Blocks -> WordPages(s.items, onPageLabel)
    is Loaded.Sheets -> SheetPages(s.items, onPageLabel)
    is Loaded.Entries -> ArchivePages(s.items, onPageLabel)
  }
}

@Composable
private fun WordPages(blocks: List<DocBlock>, onPageLabel: (String?) -> Unit) {
  LaunchedEffect(blocks) { onPageLabel(null) }
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .clip(RoundedCornerShape(18.dp))
      .background(PageSurface)
      .border(1.dp, PageBorder, RoundedCornerShape(18.dp))
      .testTag("word_pages"),
    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    itemsIndexed(blocks) { _, block -> WordBlock(block) }
  }
}

@Composable
private fun WordBlock(block: DocBlock) {
  when (block) {
    is DocBlock.Heading -> Text(
      text = block.text,
      style = when (block.level) {
        1 -> MaterialTheme.typography.headlineSmall
        2 -> MaterialTheme.typography.titleLarge
        else -> MaterialTheme.typography.titleMedium
      },
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.padding(top = 6.dp)
    )
    is DocBlock.Paragraph -> SelectionContainer {
      Text(
        text = block.text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        lineHeight = 26.sp
      )
    }
    is DocBlock.ListItem -> Row(modifier = Modifier.padding(start = (block.level * 18).dp)) {
      Text(text = "•", style = MaterialTheme.typography.bodyLarge, color = DeepBlue, modifier = Modifier.width(18.dp))
      SelectionContainer(modifier = Modifier.weight(1f)) {
        Text(
          text = block.text,
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurface,
          lineHeight = 24.sp
        )
      }
    }
    is DocBlock.Table -> Column(
      modifier = Modifier
        .horizontalScroll(rememberScrollState())
        .border(1.dp, PageBorder)
    ) {
      val columns = block.rows.maxOfOrNull { it.size } ?: 0
      block.rows.forEachIndexed { r, row -> GridRow(cells = row, columns = columns, header = r == 0, leading = null) }
    }
  }
}

@Composable
private fun GridRow(cells: List<String>, columns: Int, header: Boolean, leading: String?) {
  Row(modifier = Modifier.height(IntrinsicSize.Min)) {
    if (leading != null) {
      Box(
        modifier = Modifier
          .width(NUMBER_WIDTH.dp)
          .fillMaxHeight()
          .border(0.5.dp, PageBorder)
          .background(RowNumberFill)
          .padding(horizontal = 6.dp, vertical = 8.dp)
      ) {
        Text(text = leading, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    for (c in 0 until columns) {
      Box(
        modifier = Modifier
          .width(CELL_WIDTH.dp)
          .fillMaxHeight()
          .border(0.5.dp, PageBorder)
          .background(if (header) HeaderFill else PageSurface)
          .padding(8.dp)
      ) {
        Text(
          text = cells.getOrElse(c) { "" },
          style = MaterialTheme.typography.bodySmall,
          fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 6,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
private fun SheetPages(sheets: List<SheetData>, onPageLabel: (String?) -> Unit) {
  var selected by remember { mutableStateOf(0) }
  val index = selected.coerceIn(0, sheets.lastIndex)
  val sheet = sheets[index]
  LaunchedEffect(index, sheets.size) {
    onPageLabel(if (sheets.size > 1) "Sheet ${index + 1} / ${sheets.size}" else null)
  }
  Column(modifier = Modifier.fillMaxSize()) {
    if (sheets.size > 1) {
      Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        sheets.forEachIndexed { i, s ->
          val on = i == index
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(50))
              .background(if (on) DeepBlue else PageSurface)
              .border(1.dp, if (on) DeepBlue else PageBorder, RoundedCornerShape(50))
              .clickable { selected = i }
              .padding(horizontal = 14.dp, vertical = 8.dp)
              .testTag("sheet_tab_$i")
          ) {
            Text(
              text = s.name,
              style = MaterialTheme.typography.labelLarge,
              color = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
              maxLines = 1
            )
          }
        }
      }
    }
    val columns = sheet.columnCount
    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(PageSurface)
        .border(1.dp, PageBorder, RoundedCornerShape(14.dp))
        .horizontalScroll(rememberScrollState())
    ) {
      LazyColumn(
        modifier = Modifier.width((columns * CELL_WIDTH + NUMBER_WIDTH).dp).fillMaxHeight().testTag("sheet_grid"),
        contentPadding = PaddingValues(bottom = 96.dp)
      ) {
        itemsIndexed(sheet.rows) { r, row ->
          GridRow(cells = row, columns = columns, header = r == 0, leading = (r + 1).toString())
        }
        if (sheet.truncated) {
          item {
            Text(
              text = "Showing the first ${OfficeReader.MAX_ROWS} rows. Open the file in another app to see everything.",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(12.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ArchivePages(entries: List<ArchiveEntry>, onPageLabel: (String?) -> Unit) {
  LaunchedEffect(entries) { onPageLabel(null) }
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .clip(RoundedCornerShape(18.dp))
      .background(PageSurface)
      .border(1.dp, PageBorder, RoundedCornerShape(18.dp))
      .testTag("archive_pages"),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    item {
      Text(
        text = "This archive holds ${entries.size} item${if (entries.size == 1) "" else "s"}. Extract it with a file manager to open them.",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    itemsIndexed(entries) { _, e ->
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
          text = e.name,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f)
        )
        if (!e.isDirectory && e.size >= 0) {
          Text(text = sizeLabel(e.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
  }
}

private fun sizeLabel(bytes: Long): String = when {
  bytes < 1024 -> "$bytes B"
  bytes < 1024 * 1024 -> "${bytes / 1024} KB"
  else -> "%.1f MB".format(bytes / 1048576.0)
}
