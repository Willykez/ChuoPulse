package com.willykez.campconnect.data.repository

import com.willykez.campconnect.data.model.University
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ImportResult(val added: Int, val skipped: Int)

class CampusCatalog {

  private val _universities = MutableStateFlow<List<University>>(emptyList())
  val universities: StateFlow<List<University>> = _universities.asStateFlow()

  private val _programmes = MutableStateFlow<Map<String, Map<String, Set<String>>>>(emptyMap())
  val programmes: StateFlow<Map<String, Map<String, Set<String>>>> = _programmes.asStateFlow()

  private val _courses = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())
  val courses: StateFlow<Map<String, Map<String, String>>> = _courses.asStateFlow()

  private val _rooms = MutableStateFlow<Map<String, List<String>>>(emptyMap())
  val rooms: StateFlow<Map<String, List<String>>> = _rooms.asStateFlow()

  fun replaceUniversities(list: List<University>) {
    _universities.value = list
  }

  fun replaceProgrammes(byUniversity: Map<String, Map<String, Set<String>>>) {
    _programmes.value = byUniversity
  }

  fun replaceCampusLists(universityId: String, courses: Map<String, String>, rooms: List<String>) {
    _courses.value = _courses.value + (universityId to courses)
    _rooms.value = _rooms.value + (universityId to rooms)
  }

  fun replaceCampusProgrammes(universityId: String, programmes: Map<String, Set<String>>) {
    _programmes.value = _programmes.value + (universityId to programmes)
  }

  fun university(id: String): University? = _universities.value.firstOrNull { it.id == id }

  fun catalogFor(universityId: String): Map<String, Set<String>> = _programmes.value[universityId].orEmpty()
  fun programmesFor(universityId: String): List<String> = catalogFor(universityId).keys.sorted()
  fun coursesFor(universityId: String): Map<String, String> = _courses.value[universityId].orEmpty()
  fun roomsFor(universityId: String): List<String> = _rooms.value[universityId].orEmpty()

  fun addProgramme(universityId: String, name: String): Boolean {
    val clean = name.trim()
    if (clean.length < 2 || catalogFor(universityId).keys.any { it.equals(clean, ignoreCase = true) }) return false
    _programmes.value = _programmes.value + (universityId to (catalogFor(universityId) + (clean to emptySet())))
    return true
  }

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

  fun setProgrammeCourses(universityId: String, programme: String, codes: Set<String>): Boolean {
    val current = catalogFor(universityId)
    val key = current.keys.firstOrNull { it.equals(programme.trim(), ignoreCase = true) } ?: return false
    val valid = codes.map { it.trim().uppercase() }.filter { it in coursesFor(universityId) }.toSet()
    _programmes.value = _programmes.value + (universityId to (current + (key to valid)))
    return true
  }

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

}
