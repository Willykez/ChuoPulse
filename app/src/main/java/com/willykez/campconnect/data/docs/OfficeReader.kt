package com.willykez.campconnect.data.docs

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.util.zip.ZipFile

sealed interface DocBlock {
  data class Heading(val level: Int, val text: String) : DocBlock
  data class Paragraph(val text: String) : DocBlock
  data class ListItem(val text: String, val level: Int) : DocBlock
  data class Table(val rows: List<List<String>>) : DocBlock
}

data class SheetData(val name: String, val rows: List<List<String>>, val truncated: Boolean = false) {
  val columnCount: Int get() = rows.maxOfOrNull { it.size } ?: 0
}

data class ArchiveEntry(val name: String, val size: Long, val isDirectory: Boolean)

object OfficeReader {
  const val MAX_ROWS = 2000
  const val MAX_COLS = 50
  private const val MAX_SHEETS = 30
  private const val MAX_BLOCKS = 6000
  private const val MAX_ENTRIES = 500

  private val BUILTIN_DATE_FORMATS = (14..22).toSet() + (27..36) + (45..47) + (50..58)

  fun readWord(file: File, ext: String, newParser: () -> XmlPullParser = { Xml.newPullParser() }): List<DocBlock> = when (ext) {
    "docx" -> ZipFile(file).use { zip -> zip.stream("word/document.xml") { docx(it, newParser()) } }
    "odt" -> ZipFile(file).use { zip -> zip.stream("content.xml") { odt(it, newParser()) } }
    else -> emptyList()
  }

  fun readSheets(file: File, ext: String, newParser: () -> XmlPullParser = { Xml.newPullParser() }): List<SheetData> = when (ext) {
    "xlsx" -> ZipFile(file).use { xlsx(it, newParser) }
    "ods" -> ZipFile(file).use { zip -> zip.stream("content.xml") { ods(it, newParser()) } }
    "csv", "tsv" -> listOf(delimited(file.readText(), ext))
    else -> emptyList()
  }

  fun readArchive(file: File): List<ArchiveEntry> = ZipFile(file).use { zip ->
    zip.entries().asSequence().take(MAX_ENTRIES).map { ArchiveEntry(it.name, it.size, it.isDirectory) }.toList()
  }

  private fun <T> ZipFile.stream(name: String, block: (InputStream) -> T): T {
    val entry = getEntry(name) ?: throw IllegalStateException("missing $name")
    return getInputStream(entry).use(block)
  }

  private fun XmlPullParser.attr(name: String): String? {
    for (i in 0 until attributeCount) if (getAttributeName(i) == name) return getAttributeValue(i)
    return null
  }

  private fun docx(input: InputStream, p: XmlPullParser): List<DocBlock> {
    p.setInput(input, null)
    val blocks = mutableListOf<DocBlock>()
    var para: StringBuilder? = null
    var style = ""
    var inList = false
    var level = 0
    var tableDepth = 0
    var rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    var cell = StringBuilder()
    var event = p.eventType
    while (event != XmlPullParser.END_DOCUMENT && blocks.size < MAX_BLOCKS) {
      if (event == XmlPullParser.START_TAG) {
        when (p.name) {
          "w:tbl" -> { tableDepth++; if (tableDepth == 1) rows = mutableListOf() }
          "w:tr" -> if (tableDepth == 1) row = mutableListOf()
          "w:tc" -> if (tableDepth == 1) cell = StringBuilder()
          "w:p" -> { para = StringBuilder(); style = ""; inList = false; level = 0 }
          "w:pStyle" -> style = p.attr("w:val").orEmpty()
          "w:numPr" -> inList = true
          "w:ilvl" -> level = p.attr("w:val")?.toIntOrNull() ?: 0
          "w:t" -> para?.append(p.nextText())
          "w:tab" -> para?.append('\t')
          "w:br" -> if (p.attr("w:type") != "page") para?.append('\n')
        }
      } else if (event == XmlPullParser.END_TAG) {
        when (p.name) {
          "w:p" -> {
            val text = para?.toString()?.trim().orEmpty()
            para = null
            if (text.isNotEmpty()) {
              if (tableDepth > 0) {
                if (cell.isNotEmpty()) cell.append('\n')
                cell.append(text)
              } else {
                val heading = headingLevel(style)
                val styleList = style.lowercase().startsWith("list")
                val styleLevel = if (styleList) (style.lastOrNull()?.digitToIntOrNull()?.minus(1) ?: 0).coerceAtLeast(0) else 0
                blocks += when {
                  heading > 0 -> DocBlock.Heading(heading, text)
                  inList -> DocBlock.ListItem(text, level)
                  styleList -> DocBlock.ListItem(text, styleLevel)
                  else -> DocBlock.Paragraph(text)
                }
              }
            }
          }
          "w:tc" -> if (tableDepth == 1) row.add(cell.toString().trim())
          "w:tr" -> if (tableDepth == 1 && row.isNotEmpty()) rows.add(row)
          "w:tbl" -> {
            tableDepth--
            if (tableDepth == 0 && rows.isNotEmpty()) blocks += DocBlock.Table(rows)
          }
        }
      }
      event = p.next()
    }
    return blocks
  }

  private fun headingLevel(style: String): Int {
    val s = style.lowercase().replace(" ", "")
    return when {
      s == "title" -> 1
      s == "subtitle" -> 2
      s.startsWith("heading") -> s.removePrefix("heading").toIntOrNull()?.coerceIn(1, 6) ?: 2
      else -> 0
    }
  }

  private fun odt(input: InputStream, p: XmlPullParser): List<DocBlock> {
    p.setInput(input, null)
    val blocks = mutableListOf<DocBlock>()
    var text: StringBuilder? = null
    var heading = 0
    var listDepth = 0
    var tableDepth = 0
    var rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    var cell = StringBuilder()
    var event = p.eventType
    while (event != XmlPullParser.END_DOCUMENT && blocks.size < MAX_BLOCKS) {
      if (event == XmlPullParser.START_TAG) {
        when (p.name) {
          "table:table" -> { tableDepth++; if (tableDepth == 1) rows = mutableListOf() }
          "table:table-row" -> if (tableDepth == 1) row = mutableListOf()
          "table:table-cell" -> if (tableDepth == 1) cell = StringBuilder()
          "text:list" -> listDepth++
          "text:h" -> { text = StringBuilder(); heading = p.attr("text:outline-level")?.toIntOrNull() ?: 1 }
          "text:p" -> { text = StringBuilder(); heading = 0 }
          "text:tab" -> text?.append('\t')
          "text:line-break" -> text?.append('\n')
          "text:s" -> text?.append(' ')
        }
      } else if (event == XmlPullParser.TEXT) {
        text?.append(p.text)
      } else if (event == XmlPullParser.END_TAG) {
        when (p.name) {
          "text:h", "text:p" -> {
            val t = text?.toString()?.trim().orEmpty()
            text = null
            if (t.isNotEmpty()) {
              if (tableDepth > 0) {
                if (cell.isNotEmpty()) cell.append('\n')
                cell.append(t)
              } else blocks += when {
                heading > 0 -> DocBlock.Heading(heading.coerceIn(1, 6), t)
                listDepth > 0 -> DocBlock.ListItem(t, listDepth - 1)
                else -> DocBlock.Paragraph(t)
              }
            }
          }
          "text:list" -> listDepth--
          "table:table-cell" -> if (tableDepth == 1) row.add(cell.toString().trim())
          "table:table-row" -> if (tableDepth == 1 && row.isNotEmpty()) rows.add(row)
          "table:table" -> {
            tableDepth--
            if (tableDepth == 0 && rows.isNotEmpty()) blocks += DocBlock.Table(rows)
          }
        }
      }
      event = p.next()
    }
    return blocks
  }

  private fun xlsx(zip: ZipFile, newParser: () -> XmlPullParser): List<SheetData> {
    val shared = zip.getEntry("xl/sharedStrings.xml")?.let { e -> zip.getInputStream(e).use { sharedStrings(it, newParser()) } } ?: emptyList()
    val dateStyles = zip.getEntry("xl/styles.xml")?.let { e -> zip.getInputStream(e).use { dateStyles(it, newParser()) } } ?: emptySet()
    val rels = zip.getEntry("xl/_rels/workbook.xml.rels")?.let { e -> zip.getInputStream(e).use { relationships(it, newParser()) } } ?: emptyMap()
    val declared = zip.getEntry("xl/workbook.xml")?.let { e -> zip.getInputStream(e).use { workbookSheets(it, newParser()) } } ?: emptyList()
    val targets = if (declared.isNotEmpty()) declared.mapNotNull { (name, rid) ->
      rels[rid]?.let { t -> name to (if (t.startsWith("/")) t.removePrefix("/") else "xl/$t") }
    } else zip.entries().asSequence().map { it.name }
      .filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }.sorted().mapIndexed { i, n -> "Sheet ${i + 1}" to n }.toList()
    return targets.take(MAX_SHEETS).mapNotNull { (name, path) ->
      val entry = zip.getEntry(path) ?: return@mapNotNull null
      zip.getInputStream(entry).use { worksheet(name, it, newParser(), shared, dateStyles) }
    }
  }

  private fun sharedStrings(input: InputStream, p: XmlPullParser): List<String> {
    p.setInput(input, null)
    val out = mutableListOf<String>()
    var sb: StringBuilder? = null
    var phonetic = 0
    var inT = false
    var event = p.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
      when (event) {
        XmlPullParser.START_TAG -> when (p.name) {
          "si" -> sb = StringBuilder()
          "rPh" -> phonetic++
          "t" -> inT = true
        }
        XmlPullParser.TEXT -> if (inT && phonetic == 0) sb?.append(p.text)
        XmlPullParser.END_TAG -> when (p.name) {
          "t" -> inT = false
          "rPh" -> phonetic--
          "si" -> { out += sb?.toString().orEmpty(); sb = null }
        }
      }
      event = p.next()
    }
    return out
  }

  private fun dateStyles(input: InputStream, p: XmlPullParser): Set<Int> {
    p.setInput(input, null)
    val custom = mutableMapOf<Int, String>()
    val xfs = mutableListOf<Int>()
    var inCellXfs = false
    var event = p.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
      if (event == XmlPullParser.START_TAG) {
        when (p.name) {
          "numFmt" -> {
            val id = p.attr("numFmtId")?.toIntOrNull()
            val code = p.attr("formatCode")
            if (id != null && code != null) custom[id] = code
          }
          "cellXfs" -> inCellXfs = true
          "xf" -> if (inCellXfs) xfs += p.attr("numFmtId")?.toIntOrNull() ?: 0
        }
      } else if (event == XmlPullParser.END_TAG && p.name == "cellXfs") inCellXfs = false
      event = p.next()
    }
    return xfs.indices.filter { i -> val id = xfs[i]; id in BUILTIN_DATE_FORMATS || custom[id]?.let(::looksLikeDate) == true }.toSet()
  }

  private fun looksLikeDate(code: String): Boolean {
    val plain = code.replace(Regex("\"[^\"]*\""), "").replace(Regex("\\[[^\\]]*\\]"), "").replace(Regex("\\\\."), "").lowercase()
    return plain.any { it == 'y' || it == 'd' } || (plain.contains('h') && plain.contains(':'))
  }

  private fun relationships(input: InputStream, p: XmlPullParser): Map<String, String> {
    p.setInput(input, null)
    val out = mutableMapOf<String, String>()
    var event = p.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
      if (event == XmlPullParser.START_TAG && p.name == "Relationship") {
        val id = p.attr("Id"); val target = p.attr("Target")
        if (id != null && target != null) out[id] = target
      }
      event = p.next()
    }
    return out
  }

  private fun workbookSheets(input: InputStream, p: XmlPullParser): List<Pair<String, String>> {
    p.setInput(input, null)
    val out = mutableListOf<Pair<String, String>>()
    var event = p.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
      if (event == XmlPullParser.START_TAG && p.name == "sheet" && p.attr("state") != "hidden") {
        val name = p.attr("name"); val rid = p.attr("r:id")
        if (name != null && rid != null) out += name to rid
      }
      event = p.next()
    }
    return out
  }

  private fun worksheet(name: String, input: InputStream, p: XmlPullParser, shared: List<String>, dateStyles: Set<Int>): SheetData {
    p.setInput(input, null)
    val rows = mutableListOf<MutableList<String>>()
    var truncated = false
    var currentRow = -1
    var col = 0
    var type = ""
    var style = 0
    var value = StringBuilder()
    var inline = StringBuilder()
    var inV = false
    var inT = false
    var event = p.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
      when (event) {
        XmlPullParser.START_TAG -> when (p.name) {
          "row" -> {
            val r = (p.attr("r")?.toIntOrNull() ?: (currentRow + 2)) - 1
            if (r >= MAX_ROWS) { truncated = true; break }
            while (rows.size <= r) rows.add(mutableListOf())
            currentRow = r
            col = 0
          }
          "c" -> {
            col = p.attr("r")?.let(::columnOf) ?: col
            type = p.attr("t").orEmpty()
            style = p.attr("s")?.toIntOrNull() ?: 0
            value = StringBuilder(); inline = StringBuilder()
          }
          "v" -> inV = true
          "t" -> inT = true
        }
        XmlPullParser.TEXT -> {
          if (inV) value.append(p.text)
          if (inT) inline.append(p.text)
        }
        XmlPullParser.END_TAG -> when (p.name) {
          "v" -> inV = false
          "t" -> inT = false
          "c" -> if (currentRow >= 0 && col < MAX_COLS) {
            val text = cellText(type, value.toString(), inline.toString(), style in dateStyles, shared)
            val r = rows[currentRow]
            while (r.size < col) r.add("")
            if (r.size == col) r.add(text) else r[col] = text
            col++
          } else col++
        }
      }
      if (truncated) break
      event = p.next()
    }
    val trimmed = rows.dropLastWhile { r -> r.all { it.isBlank() } }
    return SheetData(name, trimmed, truncated)
  }

  private fun columnOf(ref: String): Int {
    var n = 0
    for (ch in ref) {
      if (ch in 'A'..'Z') n = n * 26 + (ch - 'A' + 1) else if (ch in 'a'..'z') n = n * 26 + (ch - 'a' + 1) else break
    }
    return (n - 1).coerceAtLeast(0)
  }

  private fun cellText(type: String, raw: String, inline: String, isDate: Boolean, shared: List<String>): String = when (type) {
    "s" -> raw.trim().toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
    "inlineStr" -> inline
    "str" -> raw
    "b" -> if (raw.trim() == "1") "TRUE" else "FALSE"
    "e" -> raw
    else -> {
      val v = raw.trim()
      when {
        v.isEmpty() -> inline
        isDate -> excelDate(v) ?: number(v)
        else -> number(v)
      }
    }
  }

  private fun number(v: String): String = try {
    if (v.contains('.') || v.contains('E') || v.contains('e')) BigDecimal(v).round(MathContext(12)).stripTrailingZeros().toPlainString() else v
  } catch (e: Exception) { v }

  private fun excelDate(v: String): String? = try {
    val serial = v.toDouble()
    val days = serial.toLong()
    val date = LocalDate.of(1899, 12, 30).plusDays(days)
    val minutes = Math.round((serial - days) * 24 * 60).toInt()
    if (minutes == 0) date.toString() else "%s %02d:%02d".format(date, minutes / 60, minutes % 60)
  } catch (e: Exception) { null }

  private fun ods(input: InputStream, p: XmlPullParser): List<SheetData> {
    p.setInput(input, null)
    val sheets = mutableListOf<SheetData>()
    var name = ""
    var rows = mutableListOf<MutableList<String>>()
    var row = mutableListOf<String>()
    var cell = StringBuilder()
    var inCell = false
    var rowRepeat = 1
    var colRepeat = 1
    var truncated = false
    var event = p.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
      when (event) {
        XmlPullParser.START_TAG -> when (p.name) {
          "table:table" -> { name = p.attr("table:name").orEmpty(); rows = mutableListOf(); truncated = false }
          "table:table-row" -> { row = mutableListOf(); rowRepeat = p.attr("table:number-rows-repeated")?.toIntOrNull() ?: 1 }
          "table:table-cell", "table:covered-table-cell" -> { cell = StringBuilder(); inCell = true; colRepeat = p.attr("table:number-columns-repeated")?.toIntOrNull() ?: 1 }
          "text:p" -> if (inCell && cell.isNotEmpty()) cell.append('\n')
        }
        XmlPullParser.TEXT -> if (inCell) cell.append(p.text)
        XmlPullParser.END_TAG -> when (p.name) {
          "table:table-cell", "table:covered-table-cell" -> {
            inCell = false
            val text = cell.toString().trim()
            val times = if (text.isEmpty()) minOf(colRepeat, 1) else minOf(colRepeat, MAX_COLS)
            repeat(times) { if (row.size < MAX_COLS) row.add(text) }
          }
          "table:table-row" -> {
            val empty = row.all { it.isBlank() }
            val times = if (empty) 1 else minOf(rowRepeat, 200)
            repeat(times) { if (rows.size < MAX_ROWS) rows.add(row.toMutableList()) else truncated = true }
          }
          "table:table" -> {
            val trimmed = rows.dropLastWhile { r -> r.all { it.isBlank() } }
            if (trimmed.isNotEmpty()) sheets += SheetData(name.ifBlank { "Sheet ${sheets.size + 1}" }, trimmed, truncated)
          }
        }
      }
      event = p.next()
    }
    return sheets.take(MAX_SHEETS)
  }

  fun delimited(text: String, ext: String): SheetData {
    val first = text.lineSequence().firstOrNull().orEmpty()
    val delimiter = if (ext == "tsv") '\t' else listOf(',', ';', '\t').maxByOrNull { d -> first.count { it == d } }?.takeIf { d -> first.contains(d) } ?: ','
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    var truncated = false
    var i = 0
    fun endField() { if (row.size < MAX_COLS) row.add(field.toString().trim()); field.setLength(0) }
    fun endRow() {
      endField()
      if (row.any { it.isNotEmpty() }) { if (rows.size < MAX_ROWS) rows.add(row) else truncated = true }
      row = mutableListOf()
    }
    while (i < text.length && !truncated) {
      val c = text[i]
      when {
        quoted && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
        c == '"' -> quoted = !quoted
        !quoted && c == delimiter -> endField()
        !quoted && c == '\n' -> endRow()
        !quoted && c == '\r' -> {}
        else -> field.append(c)
      }
      i++
    }
    if (field.isNotEmpty() || row.isNotEmpty()) endRow()
    return SheetData(if (ext == "tsv") "TSV" else "CSV", rows, truncated)
  }
}
