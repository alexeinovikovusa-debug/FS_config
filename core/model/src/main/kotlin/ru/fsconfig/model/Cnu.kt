package ru.fsconfig.model

data class CnuHeaderEntry(
    val key: String,
    val value: String,
    val originalLine: String
)

data class CnuDocument(
    val header: List<CnuHeaderEntry>,
    val rows: List<List<Int>>,
    val originalRowLines: List<String>,
    val newline: String = "\r\n",
    val trailingNewline: Boolean = true
) {
    val metadata: Map<String, String> = header.associate { it.key to it.value }
}

data class CnuParseResult(
    val document: CnuDocument,
    val warnings: List<String>
)

object CnuCodec {
    private val integerKeys = setOf("DeviceType", "DeviceVersion", "NewConfiguration", "LengthConfiguration", "LineCount")

    fun parse(text: String): CnuParseResult {
        val newline = if (text.contains("\r\n")) "\r\n" else "\n"
        val trailingNewline = text.endsWith("\n")
        val lines = text.split("\n").map { it.removeSuffix("\r") }
        val content = if (trailingNewline) lines.dropLast(1) else lines
        require(content.isNotEmpty()) { "CNU-файл пуст" }

        val headerEnd = content.indexOfFirst { it.matches(Regex("^Line1(?:=|\\s).*$")) }
        require(headerEnd >= 0) { "Не найдено начало строк конфигурации Line1" }

        val header = content.take(headerEnd).mapIndexed { index, line ->
            val separator = line.indexOf('=')
            if (separator > 0) {
                CnuHeaderEntry(line.substring(0, separator), line.substring(separator + 1), line)
            } else {
                require(line.isNotBlank()) { "Пустая строка заголовка ${index + 1}" }
                CnuHeaderEntry(line, "", line)
            }
        }
        val dataLines = content.drop(headerEnd)
        val rows = dataLines.mapIndexed { index, line ->
            val separator = line.indexOf('=')
            val name: String
            val payload: String
            if (separator > 0) {
                name = line.substring(0, separator)
                payload = line.substring(separator + 1)
            } else {
                val match = Regex("^(Line\\d+)\\s+(.+)$").matchEntire(line)
                require(match != null) { "Некорректная строка данных ${index + 1}: $line" }
                name = match.groupValues[1]
                payload = match.groupValues[2]
            }
            require(name == "Line${index + 1}") { "Ожидалась Line${index + 1}, получена $name" }
            val values = payload.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
                .map { value -> value.toIntOrNull()?.also { require(it in 0..255) { "Значение вне диапазона 0..255 в $name" } } ?: error("Некорректное число '$value'") }
            require(values.isNotEmpty()) { "Пустая строка данных ${line.substring(0, separator)}" }
            values
        }
        val metadata = header.associate { it.key to it.value }
        val warnings = buildList {
            integerKeys.filter { it in metadata }.forEach { key ->
                require(metadata.getValue(key).toIntOrNull() != null) { "Значение $key не является целым числом" }
            }
            val lineCount = metadata["LineCount"]?.toIntOrNull()
            if (lineCount != null && lineCount != rows.size) add("LineCount=$lineCount, фактически строк конфигурации=${rows.size}")
            val declaredLength = metadata["LengthConfiguration"]?.toLongOrNull()
            val actualLength = rows.sumOf { it.size }
            if (declaredLength != null && declaredLength != actualLength.toLong()) {
                add("LengthConfiguration=$declaredLength, фактических значений=$actualLength; смысл поля не подтверждён")
            }
            if (rows.any { it.size != 32 }) add("Не все строки содержат ровно 32 значения; исходный порядок сохранён")
        }
        return CnuParseResult(CnuDocument(header, rows, dataLines, newline, trailingNewline), warnings)
    }

    fun encode(document: CnuDocument): String {
        val headerLines = document.header.map { it.originalLine }
        val rowLines = document.rows.mapIndexed { index, row ->
            val original = document.originalRowLines.getOrNull(index)
            val originalValues = original?.substringAfter('=', missingDelimiterValue = original)
                ?.trim()?.split(Regex("\\s+"))?.filter(String::isNotEmpty)
                ?.mapNotNull(String::toIntOrNull)
            if (originalValues == row && original != null) original
            else {
                val originalName = original?.substringBefore('=')
                    ?.takeIf { it.startsWith("Line") } ?: "Line${index + 1}"
                "$originalName=${row.joinToString(" ")}"
            }
        }
        val lines = headerLines + rowLines
        return lines.joinToString(document.newline) + if (document.trailingNewline) document.newline else ""
    }
}
