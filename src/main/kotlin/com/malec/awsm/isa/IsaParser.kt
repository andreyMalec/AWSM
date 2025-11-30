package com.malec.awsm.isa

import com.malec.awsm.Argument
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
        val registerPrefsMap = mutableMapOf<String, String>()
        val specialRegistersMap = mutableMapOf<String, String>()
        val settingsBuilder = SettingsBuilder()
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
                "[settings]" -> {
                    commitField()
                    section = Section.Settings
                    continue
                }
                "[fields]" -> {
                    commitField()
                    section = Section.Fields
                    currentFieldName = null
                    continue
                }
                "[register_prefs]" -> {
                    commitField()
                    section = Section.RegisterPrefs
                    continue
                }
                "[special_registers]" -> {
                    commitField()
                    section = Section.SpecialRegisters
                    continue
                }
                "[instructions]" -> {
                    commitField()
                    section = Section.Instructions
                    continue
                }
            }
            when (section) {
                Section.Settings -> settingsBuilder.consume(line)
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
                Section.RegisterPrefs -> {
                    val (key, value) = line.split('=', limit = 2).map { it.trim() }
                    registerPrefsMap[key.lowercase()] = value.lowercase()
                }
                Section.SpecialRegisters -> {
                    val (key, value) = line.split('=', limit = 2).map { it.trim() }
                    specialRegistersMap[key.lowercase()] = value.lowercase()
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
                        operands = operands,
                        virtualOperands = emptyList(),
                        assertions = emptyList(),
                        bitPattern = bitPattern,
                        description = description
                    )
                    instructions.getOrPut(nameKey) { mutableListOf() }.add(definition)
                }
                Section.None -> error("Specification must begin with [settings], [fields], or [instructions]")
            }
        }
        commitField()
        val registry = fields["register"]?.entries.orEmpty()
        fun resolveRegister(name: String?): Argument.Register? {
            if (name.isNullOrBlank()) return null
            val entry = registry[name.lowercase()] ?: return null
            return Argument.Register(entry.symbol, entry.bits)
        }
        val registerPrefs = IsaDialect.RegisterPreferences(
            lhs = resolveRegister(registerPrefsMap["lhs"]),
            rhs = resolveRegister(registerPrefsMap["rhs"]),
            result = resolveRegister(registerPrefsMap["result"])
        )
        val specialRegisters = IsaDialect.SpecialRegisters(
            zero = resolveRegister(specialRegistersMap["zr"]),
            stackPointer = resolveRegister(specialRegistersMap["sp"]),
            flags = resolveRegister(specialRegistersMap["flags"]),
            immediate = resolveRegister(specialRegistersMap["immediate"]),
            custom = specialRegistersMap
                .filterKeys { it !in setOf("zr", "sp", "flags", "immediate") }
                .mapNotNull { (key, value) ->
                    val register = resolveRegister(value)
                    if (register != null) key to register else null
                }
                .toMap()
        )
        return IsaDialect(
            name = name,
            settings = settingsBuilder.build(),
            fields = fields.toMap(),
            instructions = instructions.mapValues { it.value.toList() },
            registerPreferences = registerPrefs,
            specialRegisters = specialRegisters
        )
    }

    fun parseFile(path: Path): IsaDialect {
        val content = Files.readString(path)
        val name = path.fileName.toString().substringBefore('.')
        return parse(name, content)
    }

    private enum class Section { Settings, Fields, RegisterPrefs, SpecialRegisters, Instructions, None }
}

private class SettingsBuilder {
    private var variant: String? = null
    private var endianness = IsaDialect.Settings.Endianness.BIG
    private val lineComments = linkedSetOf(";", "//")
    private val blockComments = linkedMapOf("/*" to "*/")

    fun consume(line: String) {
        val (key, value) = line.split('=', limit = 2).map { it.trim() }
        when (key.lowercase()) {
            "variant" -> variant = value.trim('"')
            "endianness" -> endianness = if (value.equals("little", true)) IsaDialect.Settings.Endianness.LITTLE else IsaDialect.Settings.Endianness.BIG
            "line_comments" -> {
                lineComments.clear()
                value.split(',').map { it.trim().trim('"') }.filter { it.isNotBlank() }.forEach(lineComments::add)
            }
            "block_comments" -> {
                blockComments.clear()
                value.removePrefix("{").removeSuffix("}").split(',').map { it.trim() }.forEach { entry ->
                    val parts = entry.split(':').map { it.trim().trim('"') }
                    if (parts.size == 2) blockComments[parts[0]] = parts[1]
                }
            }
        }
    }

    fun build(): IsaDialect.Settings = IsaDialect.Settings(
        variant = variant,
        endianness = endianness,
        lineComments = lineComments,
        blockComments = blockComments
    )
}

object IsaDialectParserRules {
    private val operandRegex = Regex("%([a-z])\\(([^)]*)\\)")

    fun extractOperands(syntax: String): List<IsaDialect.InstructionDefinition.OperandDefinition> {
        return operandRegex.findAll(syntax).map {
            val placeholder = it.groupValues[1].first()
            val types = it.groupValues[2].split('|').map { type -> type.trim() }.filter { type -> type.isNotEmpty() }
            IsaDialect.InstructionDefinition.OperandDefinition(
                name = placeholder.toString(),
                placeholder = placeholder,
                size = IsaDialect.InstructionDefinition.OperandDefinition.OperandSize(signed = false, bits = 64),
                fields = types
            )
        }.toList()
    }
}
