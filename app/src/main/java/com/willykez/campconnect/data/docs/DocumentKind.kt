package com.willykez.campconnect.data.docs

/** What kind of file something is, decided from its extension first and its MIME type second. */
enum class DocumentKind(val label: String) {
  PDF("PDF"),
  IMAGE("Image"),
  WORD("Word document"),
  SLIDES("Presentation"),
  SHEET("Spreadsheet"),
  TEXT("Text file"),
  OTHER("File");

  companion object {
    /** Everything the file pickers offer. Android lists only files of these types. */
    val pickerMimeTypes: Array<String> = arrayOf(
      "application/pdf",
      "image/*",
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      "application/msword",
      "application/vnd.openxmlformats-officedocument.presentationml.presentation",
      "application/vnd.ms-powerpoint",
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      "application/vnd.ms-excel",
      "application/vnd.oasis.opendocument.text",
      "application/vnd.oasis.opendocument.presentation",
      "application/vnd.oasis.opendocument.spreadsheet",
      "application/rtf",
      "text/*"
    )

    /** Old binary Office formats cannot be read without a large library. Saying so beats guessing. */
    val legacyOffice = setOf("doc", "ppt", "xls")

    fun extensionOf(fileName: String): String = fileName.substringAfterLast('.', "").lowercase()

    fun of(fileName: String, mimeType: String? = null): DocumentKind {
      when (extensionOf(fileName)) {
        "pdf" -> return PDF
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif" -> return IMAGE
        "docx", "doc", "odt", "rtf" -> return WORD
        "pptx", "ppt", "odp" -> return SLIDES
        "xlsx", "xls", "ods", "csv", "tsv" -> return SHEET
        "txt", "md", "html", "htm", "json", "xml", "log" -> return TEXT
      }
      val m = mimeType.orEmpty().lowercase()
      return when {
        m == "application/pdf" -> PDF
        m.startsWith("image/") -> IMAGE
        "wordprocessing" in m || "msword" in m || "opendocument.text" in m || m == "application/rtf" -> WORD
        "presentation" in m || "powerpoint" in m -> SLIDES
        "spreadsheet" in m || "excel" in m -> SHEET
        m.startsWith("text/") -> TEXT
        else -> OTHER
      }
    }

    fun isAnalysable(fileName: String, mimeType: String? = null): Boolean =
      of(fileName, mimeType) != OTHER
  }
}
