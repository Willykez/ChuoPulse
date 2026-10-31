package com.willykez.campconnect.data.repository

import com.willykez.campconnect.data.model.University
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What an import of courses did. */
data class ImportResult(val added: Int, val skipped: Int)

/**
 * Everything a campus sets up for itself: its programmes, its courses and its rooms. The campus
 * administrator creates these (one by one or by pasting a list). The app manager creates the campuses.
 * Nothing here is shared between universities.
 */
class CampusCatalog {

  private val _universities = MutableStateFlow<List<University>>(emptyList())
  /** Campuses people can choose at sign-up. */
  val universities: StateFlow<List<University>> = _universities.asStateFlow()

  // university -> programme -> course codes
  private val _programmes = MutableStateFlow<Map<String, Map<String, Set<String>>>>(emptyMap())
  val programmes: StateFlow<Map<String, Map<String, Set<String>>>> = _programmes.asStateFlow()

  // university -> course code -> title
  private val _courses = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())
  val courses: StateFlow<Map<String, Map<String, String>>> = _courses.asStateFlow()

  // university -> room names
  private val _rooms = MutableStateFlow<Map<String, List<String>>>(emptyMap())
  val rooms: StateFlow<Map<String, List<String>>> = _rooms.asStateFlow()

  /** Replaces the campus list with what the server returned. */
  fun replaceUniversities(list: List<University>) {
    _universities.value = list
  }

  /** Replaces the programmes of the given campuses with what the server returned. */
  fun replaceProgrammes(byUniversity: Map<String, Map<String, Set<String>>>) {
    _programmes.value = byUniversity
  }

  /** Replaces one campus's courses and rooms with what the server returned. */
  fun replaceCampusLists(universityId: String, courses: Map<String, String>, rooms: List<String>) {
    _courses.value = _courses.value + (universityId to courses)
    _rooms.value = _rooms.value + (universityId to rooms)
  }

  /** Replaces one campus's programmes (name -> course codes) with what the server returned. */
  fun replaceCampusProgrammes(universityId: String, programmes: Map<String, Set<String>>) {
    _programmes.value = _programmes.value + (universityId to programmes)
  }

  fun university(id: String): University? = _universities.value.firstOrNull { it.id == id }

  /** Changes one campus's details in place. Returns false when the campus does not exist. */
  fun updateUniversity(id: String, change: (University) -> University): Boolean {
    if (!hasUniversity(id)) return false
    _universities.value = _universities.value.map { if (it.id == id) change(it) else it }
    return true
  }

  fun universityName(id: String): String = _universities.value.firstOrNull { it.id == id }?.name ?: id
  fun hasUniversity(id: String) = _universities.value.any { it.id == id }

  fun catalogFor(universityId: String): Map<String, Set<String>> = _programmes.value[universityId].orEmpty()
  fun programmesFor(universityId: String): List<String> = catalogFor(universityId).keys.sorted()
  fun coursesFor(universityId: String): Map<String, String> = _courses.value[universityId].orEmpty()
  fun courseTitle(universityId: String, code: String): String = coursesFor(universityId)[code] ?: code
  fun roomsFor(universityId: String): List<String> = _rooms.value[universityId].orEmpty()

  // ---------- Campus administrator ----------

  fun addProgramme(universityId: String, name: String): Boolean {
    val clean = name.trim()
    if (clean.length < 2 || catalogFor(universityId).keys.any { it.equals(clean, ignoreCase = true) }) return false
    _programmes.value = _programmes.value + (universityId to (catalogFor(universityId) + (clean to emptySet())))
    return true
  }

  /** Adds (or renames) a course and, when [programme] is given, puts it in that programme. */
  fun addCourse(universityId: String, code: String, title: String, programme: String?): Boolean {
    val cleanCode = code.trim().uppercase().replace(" ", "")
    val cleanTitle = title.trim()
    if (cleanCode.length < 2 || cleanTitle.length < 2) return false
    _courses.value = _courses.value + (universityId to (coursesFor(universityId) + (cleanCode to cleanTitle)))
    if (!programme.isNullOrBlank()) {
      val name = programme.trim()
      addProgramme(universityId, name)
      val key = catalogFor(universityId).keys.first { it.equals(name, ignoreCase = true) }
      val current = catalogFor(universityId)
      _programmes.value = _programmes.value + (universityId to (current + (key to (current[key].orEmpty() + cleanCode))))
    }
    return true
  }

  /**
   * Paste one course per line: `CODE, Title` or `CODE, Title, Programme`. A comma, semicolon or tab
   * separates the parts. Lines that cannot be read are skipped.
   */
  fun importCourses(universityId: String, text: String): ImportResult {
    var added = 0
    var skipped = 0
    text.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
      val parts = line.split(Regex("[,;\t]")).map { it.trim() }
      val ok = parts.size >= 2 && addCourse(universityId, parts[0], parts[1], parts.getOrNull(2))
      if (ok) added++ else skipped++
    }
    return ImportResult(added, skipped)
  }

  fun addRoom(universityId: String, name: String): Boolean {
    val clean = name.trim()
    if (clean.length < 2 || roomsFor(universityId).any { it.equals(clean, ignoreCase = true) }) return false
    _rooms.value = _rooms.value + (universityId to (roomsFor(universityId) + clean))
    return true
  }

  // ---------- App manager ----------

  fun addUniversity(university: University): Boolean {
    if (_universities.value.any { it.id == university.id || it.shortName.equals(university.shortName, true) }) return false
    _universities.value = _universities.value + university
    return true
  }

  fun removeUniversity(id: String) {
    _universities.value = _universities.value.filterNot { it.id == id }
    clearCampus(id)
  }

  /** Forgets the programmes, courses and rooms of one campus. */
  fun clearCampus(id: String) {
    _programmes.value = _programmes.value - id
    _courses.value = _courses.value - id
    _rooms.value = _rooms.value - id
  }
}
