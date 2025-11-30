package com.malec.awsm.isa

import java.nio.file.Files
import java.nio.file.Path

class IsaParser {
    fun parse(name: String, content: String): IsaDialect {
        val lines = content.lines().map { it.trimEnd() }
        var section: Section = Section.None
        val fields = mutableMapOf<String, IsaDialect.FieldDefinition>()
        val instructions = mutableMapOf<String, MutableList<IsaDialect.InstructionDefinition>>()
        var currentFieldName: String? = null
        val fieldEntries = mutableMapOf<String, IsaDialect.FieldEntry>()
        var index = 0

        fun commitField() {
            val fieldName = currentFieldName
            if (fieldName != null && fieldEntries.isNotEmpty()) {
                fields[fieldName.lowercase()] = IsaDialect.FieldDefinition(fieldName, fieldEntries.toMap())
                fieldEntries.clear()
            }
        }

        while (index < lines.size) {
            val raw = lines[index++]
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("//")) continue
            when (line.lowercase()) {
                "[fields]" -> {
                    commitField()
                    section = Section.Fields
                    currentFieldName = null
                    continue
                }
                "[instructions]" -> {
                    commitField()
                    section = Section.Instructions
                    continue
                }
            }
            when (section) {
                Section.Fields -> {
                    if (!line.contains(' ')) {
                        commitField()
                        currentFieldName = line
                    } else {
                        val parts = line.split(Regex("\\s+"), limit = 2)
                        val symbol = parts[0]
                        val bits = parts.getOrNull(1)?.replace(" ", "")
                            ?: error("Missing bits for field entry: $line")
                        fieldEntries[symbol.lowercase()] = IsaDialect.FieldEntry(symbol, bits)
                    }
                }
                Section.Instructions -> {
                    val syntax = line
                    val bitPattern = lines.getOrNull(index)?.trim()?.takeIf { it.isNotEmpty() }
                        ?: error("Missing bit pattern after '$syntax'")
                    index++
                    val description = lines.getOrNull(index)?.takeIf { it.isNotBlank() && !it.startsWith("[") }
                    if (description != null) index++
                    val operands = IsaDialectParserRules.extractOperands(syntax)
                    val nameKey = syntax.substringBefore(' ').ifEmpty { syntax }.lowercase()
                    val definition = IsaDialect.InstructionDefinition(
                        name = nameKey,
                        syntax = syntax,
                        bitPattern = bitPattern,
                        operands = operands,
                        description = description
                    )
                    instructions.getOrPut(nameKey) { mutableListOf() }.add(definition)
                }
                Section.None -> error("Specification must begin with [fields] or [instructions]")
            }
        }
        commitField()
        return IsaDialect(name, fields.toMap(), instructions.mapValues { it.value.toList() })
    }

    fun parseFile(path: Path): IsaDialect {
        val content = Files.readString(path)
        val name = path.fileName.toString().substringBefore('.')
        return parse(name, content)
    }

    private enum class Section { Fields, Instructions, None }
}

object IsaDialectParserRules {
    private val operandRegex = Regex("%([a-z])\\(([^)]*)\\)")

    fun extractOperands(syntax: String): List<IsaDialect.InstructionDefinition.OperandDefinition> {
        return operandRegex.findAll(syntax).map {
            val placeholder = it.groupValues[1].first()
            val types = it.groupValues[2].split('|').map { type -> type.trim() }.filter { type -> type.isNotEmpty() }
            IsaDialect.InstructionDefinition.OperandDefinition(placeholder, types)
        }.toList()
    }
}
