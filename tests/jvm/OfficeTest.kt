import com.willykez.campconnect.data.docs.*
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import java.io.File

val newParser: () -> XmlPullParser = { KXmlParser() }
var pass = 0; var fail = 0
fun check(n: String, ok: Boolean) { if (ok) pass++ else { fail++; println("FAIL $n") } }

fun main() {
  // ---------- docx
  val w = OfficeReader.readWord(File("t.docx"), "docx", newParser)
  check("docx title is heading 1", w[0] == DocBlock.Heading(1, "Timetable Notes"))
  check("docx heading 1", w[1] == DocBlock.Heading(1, "Semester One"))
  check("docx paragraph", w[2] == DocBlock.Paragraph("Classes start on Monday."))
  check("docx bullet list items", w[3] is DocBlock.ListItem && (w[3] as DocBlock.ListItem).text == "Bring your ID card" && w[4] is DocBlock.ListItem)
  val tbl = w.filterIsInstance<DocBlock.Table>().single()
  check("docx table 2x3", tbl.rows == listOf(listOf("Day", "Time", "Venue"), listOf("Monday", "08:00", "LH1")))
  check("docx order preserved (table before footer)", w.indexOf(tbl) < w.indexOfFirst { it is DocBlock.Paragraph && (it as DocBlock.Paragraph).text == "Footer line" })
  check("docx table text not duplicated as paragraphs", w.none { it is DocBlock.Paragraph && (it as DocBlock.Paragraph).text == "Monday" })

  // ---------- xlsx
  val x = OfficeReader.readSheets(File("t.xlsx"), "xlsx", newParser)
  check("xlsx visible sheets only, in order", x.map { it.name } == listOf("Courses", "Rooms"))
  val c = x[0].rows
  check("xlsx header row", c[0] == listOf("Code", "Title", "Credits", "Start"))
  check("xlsx text+number+date", c[1] == listOf("CS210", "Databases", "3", "2026-02-02"))
  check("xlsx empty cell keeps column alignment + decimal + datetime", c[2] == listOf("CS220", "", "2.5", "2026-02-03 09:30"))
  check("xlsx boolean in column E", c[3].getOrNull(4) == "TRUE" && c[3].take(4).all { it.isEmpty() })
  check("xlsx formula result/blank handled without crash", c[4].isNotEmpty())
  check("xlsx unicode", c[5][1] == "Ünïcode ✓")
  check("xlsx second sheet", x[1].rows == listOf(listOf("Room", "Seats"), listOf("LH1", "120")))

  // ---------- ods (with 100000 repeated empty rows and 1024 repeated empty cols: must stay small)
  val o = OfficeReader.readSheets(File("t.ods"), "ods", newParser)
  check("ods sheet name + rows", o.size == 1 && o[0].name == "Rooms" && o[0].rows == listOf(listOf("Room", "Seats"), listOf("LH1", "120")))
  check("ods huge empty repeat does not explode", o[0].rows.size <= 3 && !o[0].truncated)

  // ---------- odt
  val t = OfficeReader.readWord(File("t.odt"), "odt", newParser)
  check("odt heading", t[0] == DocBlock.Heading(1, "Intro"))
  check("odt paragraph + list item", t[1] == DocBlock.Paragraph("Hello world") && t[2] is DocBlock.ListItem && (t[2] as DocBlock.ListItem).text == "first item")

  // ---------- csv / tsv
  val csv = OfficeReader.readSheets(File("t.csv"), "csv", newParser)[0]
  check("csv quoted comma + escaped quote + empty trailing", csv.rows == listOf(listOf("Code", "Title", "Notes"), listOf("CS210", "Databases, advanced", "say \"hi\""), listOf("CS220", "Networks", "")))
  check("csv semicolon auto-detected", OfficeReader.readSheets(File("semi.csv"), "csv", newParser)[0].rows == listOf(listOf("a", "b", "c"), listOf("1", "2", "3")))
  check("tsv", OfficeReader.readSheets(File("t.tsv"), "tsv", newParser)[0].rows == listOf(listOf("a", "b"), listOf("1", "2")))
  val big = (1..2500).joinToString("\n") { "r$it,x" }
  val capped = OfficeReader.delimited(big, "csv")
  check("csv row cap + truncated flag", capped.rows.size == OfficeReader.MAX_ROWS && capped.truncated)
  check("csv empty file", OfficeReader.delimited("", "csv").rows.isEmpty())

  // ---------- archive + bad input
  val z = OfficeReader.readArchive(File("t.zip"))
  check("zip listing", z.map { it.name } == listOf("a.txt", "dir/b.txt") && z[1].size == 10L)
  check("not-a-zip throws (caller shows fallback)", runCatching { OfficeReader.readWord(File("t.csv"), "docx", newParser) }.isFailure)
  check("unknown ext -> empty", OfficeReader.readSheets(File("t.csv"), "pdf", newParser).isEmpty())
  println("OfficeReader: $pass passed, $fail failed"); System.exit(if (fail == 0) 0 else 1)
}
