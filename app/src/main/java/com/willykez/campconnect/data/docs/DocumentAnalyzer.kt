package com.willykez.campconnect.data.docs

import kotlin.math.ceil

/** What the analysis produces for one document. */
data class DocumentAnalysis(
  val summary: String,
  val keyTopics: List<String>,
  val objectives: List<String>,
  val studyTips: List<String>,
  val wordCount: Int,
  val readingMinutes: Int,
  val isSwahili: Boolean
)

/**
 * Turns the text of a document into a summary, key topics, learning objectives and study tips.
 *
 * This is an on-device *extractive* analysis: it picks the most informative sentences and the most
 * important terms from the document itself. It never invents facts, works offline and keeps the
 * document on the phone. It does not paraphrase or answer questions the way a language model would.
 * Works for English and Swahili.
 */
object DocumentAnalyzer {

  fun analyse(text: String): DocumentAnalysis {
    val clean = text.trim()
    val tokens = WORD.findAll(clean).map { it.value.lowercase() }.toList()
    val wordCount = NUMBERED_WORD.findAll(clean).count()
    val swahili = tokens.count { it in SW_STOP } > tokens.count { it in EN_STOP }

    val content = tokens.filter { it.length >= 4 && it !in EN_STOP && it !in SW_STOP }
    val freq = content.groupingBy { it }.eachCount()

    val summary = summarise(clean, freq)
    val topics = topics(tokens, freq)
    return DocumentAnalysis(
      summary = summary,
      keyTopics = topics,
      objectives = objectives(clean, topics, swahili),
      studyTips = studyTips(clean, wordCount, swahili),
      wordCount = wordCount,
      readingMinutes = ceil(wordCount / 200.0).toInt().coerceAtLeast(1),
      isSwahili = swahili
    )
  }

  // ---------- Summary ----------

  private fun summarise(text: String, freq: Map<String, Int>): String {
    val sentences = text.split(Regex("""(?<=[.!?])\s+|\n+"""))
      .map { it.trim().trimStart('•', '-', '*', '–', ' ') }
      .filter { it.length in 35..420 }
      .distinct()

    if (sentences.size < 2) {
      // Slides and notes are mostly short lines, so use the first meaningful ones.
      val lines = text.lines().map { it.trim().trimStart('•', '-', '*', '–', ' ') }.filter { it.length >= 8 }.distinct()
      return lines.take(5).joinToString(". ").take(MAX_SUMMARY).ifBlank { text.take(MAX_SUMMARY) }
    }

    val scored = sentences.mapIndexed { index, sentence ->
      val words = WORD.findAll(sentence).map { it.value.lowercase() }.filter { it in freq }.toList()
      val base = words.sumOf { freq.getValue(it) }.toDouble() / words.size.coerceAtLeast(6)
      index to base * (if (index < 2) 1.15 else 1.0)
    }
    val chosen = scored.sortedByDescending { it.second }.take(4).map { it.first }.sorted()
    var out = chosen.joinToString(" ") { sentences[it] }
    if (out.length > MAX_SUMMARY) {
      out = out.take(MAX_SUMMARY).substringBeforeLast(' ').trimEnd(',', ';', ':') + "…"
    }
    return out
  }

  // ---------- Topics ----------

  private fun topics(tokens: List<String>, freq: Map<String, Int>): List<String> {
    val bigrams = HashMap<String, Int>()
    for (i in 0 until tokens.size - 1) {
      val a = tokens[i]
      val b = tokens[i + 1]
      if (a in freq && b in freq) bigrams.merge("$a $b", 1, Int::plus)
    }
    val phrases = bigrams.filter { it.value >= 2 }.entries
      .sortedByDescending { it.value }.take(3).map { it.key }
    val words = freq.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key.length })
      .map { it.key }
      .filter { w -> phrases.none { w in it.split(' ') } }
    val picked = (phrases + words).take(MAX_TOPICS)
    return picked.map { p -> p.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } } }
  }

  // ---------- Objectives and tips ----------

  private fun objectives(text: String, topics: List<String>, swahili: Boolean): List<String> {
    val lines = text.lines().map { it.trim() }
    val start = lines.indexOfFirst { Regex("""^(learning\s+)?(objectives?|outcomes?|malengo)\b""", RegexOption.IGNORE_CASE).containsMatchIn(it) }
    if (start >= 0) {
      val written = lines.drop(start + 1).takeWhile { it.isNotBlank() }.take(5)
        .map { it.trimStart('•', '-', '*', '–', ' ', '1', '2', '3', '4', '5', '.', ')') .trim() }
        .filter { it.length in 10..180 }
      if (written.size >= 2) return written
    }
    val t = topics
    val out = ArrayList<String>()
    if (swahili) {
      t.getOrNull(0)?.let { out += "Eleza $it kwa maneno yako mwenyewe" }
      if (t.size >= 2) out += "Eleza uhusiano kati ya ${t[0]} na ${t[1]}"
      t.getOrNull(2)?.let { out += "Tumia $it kwenye mfano halisi" }
      out += "Kumbuka fasili na istilahi muhimu za nyenzo hii"
    } else {
      t.getOrNull(0)?.let { out += "Explain $it in your own words" }
      if (t.size >= 2) out += "Describe how ${t[0]} and ${t[1]} are related"
      t.getOrNull(2)?.let { out += "Apply $it to a worked example" }
      out += "Recall the key definitions and terms in this material"
    }
    return out
  }

  private fun studyTips(text: String, wordCount: Int, swahili: Boolean): List<String> {
    val hasMaths = Regex("""\d""").containsMatchIn(text) && Regex("""[=%+×÷]""").containsMatchIn(text)
    val tips = ArrayList<String>()
    if (swahili) {
      tips += "Soma muhtasari kwanza, kisha pitia hati nzima ukizingatia mada kuu"
      tips += "Funga hati na ueleze kila mada kwa sauti bila kuangalia"
      tips += "Geuza kila mada kuwa swali na ujipime"
      if (hasMaths) tips += "Fanya mifano na fomula hatua kwa hatua"
      if (wordCount > 1500) tips += "Soma kwa vipindi vya dakika 25 na mapumziko ya dakika 5"
    } else {
      tips += "Read the summary first, then skim the full document with these topics in mind"
      tips += "Close the document and explain each key topic aloud from memory"
      tips += "Turn each key topic into a question and test yourself"
      if (hasMaths) tips += "Work through the formulas and examples step by step"
      if (wordCount > 1500) tips += "Study in 25-minute sessions with a 5-minute break"
    }
    return tips.take(4)
  }

  private const val MAX_SUMMARY = 720
  private const val MAX_TOPICS = 6
  private val WORD = Regex("""\p{L}[\p{L}'’-]*""")
  private val NUMBERED_WORD = Regex("""[\p{L}\p{N}]+""")

  private val EN_STOP = setOf(
    "the", "and", "for", "are", "but", "not", "you", "all", "any", "can", "had", "her", "was", "one", "our", "out",
    "has", "his", "how", "its", "may", "new", "now", "old", "see", "two", "way", "who", "did", "get", "she", "use",
    "that", "with", "have", "this", "will", "your", "from", "they", "been", "were", "which", "their", "there",
    "what", "when", "where", "while", "would", "could", "should", "about", "into", "than", "then", "them", "these",
    "those", "such", "also", "more", "most", "some", "other", "only", "over", "each", "both", "because", "between",
    "being", "does", "doing", "done", "just", "like", "make", "many", "much", "must", "very", "using", "used",
    "within", "without", "after", "before", "under", "again", "further", "once", "here", "same", "shall", "upon"
  )

  private val SW_STOP = setOf(
    "na", "ya", "wa", "kwa", "ni", "za", "la", "katika", "hii", "huo", "hiyo", "hizo", "huu", "hao", "yake", "wake",
    "kama", "pia", "lakini", "au", "ili", "kuwa", "kila", "ambayo", "ambao", "ambazo", "cha", "vya", "vyake", "yao",
    "zao", "kutoka", "hadi", "sana", "tu", "bado", "baada", "kabla", "wakati", "hata", "kwamba", "huku", "ndani",
    "juu", "chini", "mara", "sasa", "yote", "wote", "zote", "nini", "gani", "wapi", "lini", "mimi", "wewe", "yeye",
    "sisi", "ninyi", "wao", "alikuwa", "walikuwa", "atakuwa", "watakuwa", "anaweza", "wanaweza"
  )
}
