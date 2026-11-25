package com.runner.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Палитра подсветки синтаксиса с контрастом WCAG AA+ отдельно для тёмной и светлой тем.
 */
internal data class SyntaxPalette(
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val number: Color,
    val function: Color,
    val property: Color,
    val preprocessor: Color,
    val diffAddedText: Color,
    val diffAddedBg: Color,
    val diffRemovedText: Color,
    val diffRemovedBg: Color,
    val diffHeaderText: Color,
    val diffHeaderBg: Color
) {
    companion object {
        val Dark = SyntaxPalette(
            keyword = Color(0xFFCF8AE0),
            string = Color(0xFF98D487),
            comment = Color(0xFF8C91A3),
            number = Color(0xFFF2B366),
            function = Color(0xFF72B7F7),
            property = Color(0xFF80CBC4),
            preprocessor = Color(0xFFE08B6B),
            diffAddedText = Color(0xFF7EE081),
            diffAddedBg = Color(0x264CAF50),
            diffRemovedText = Color(0xFFFF8A80),
            diffRemovedBg = Color(0x26E53935),
            diffHeaderText = Color(0xFF72B7F7),
            diffHeaderBg = Color(0x1E64B5F6)
        )

        val Light = SyntaxPalette(
            keyword = Color(0xFF7B2CBF),
            string = Color(0xFF1B7A3D),
            comment = Color(0xFF6B7280),
            number = Color(0xFFB45309),
            function = Color(0xFF1D4ED8),
            property = Color(0xFF0F766E),
            preprocessor = Color(0xFFC2410C),
            diffAddedText = Color(0xFF166534),
            diffAddedBg = Color(0x1F22C55E),
            diffRemovedText = Color(0xFF991B1B),
            diffRemovedBg = Color(0x1FEF4444),
            diffHeaderText = Color(0xFF1D4ED8),
            diffHeaderBg = Color(0x1A2563EB)
        )
    }
}

internal enum class SyntaxFamily {
    DIFF,
    JSON,
    CSS,
    MARKUP,
    ASM_ARM,
    ASM_X86,
    SQL,
    CONFIG,
    HASH_SCRIPT,
    C_LIKE,
    PLAIN,
    GENERIC
}

internal fun classifySyntaxFamily(language: String): SyntaxFamily {
    val norm = language.trim().lowercase().substringBefore(' ').substringBefore('{')
    return when (norm) {
        "diff", "patch" -> SyntaxFamily.DIFF
        "json", "jsonc", "jsonl" -> SyntaxFamily.JSON
        "css", "scss", "sass", "less" -> SyntaxFamily.CSS
        "xml", "html", "htm", "svg", "xhtml", "plist" -> SyntaxFamily.MARKUP
        "arm", "arm64", "aarch64", "thumb", "s" -> SyntaxFamily.ASM_ARM
        "asm", "x86", "x86_64", "nasm", "masm", "gas", "disasm" -> SyntaxFamily.ASM_X86
        "sql", "sqlite", "postgres", "postgresql", "mysql", "tsql", "plsql" -> SyntaxFamily.SQL
        "yaml", "yml", "toml", "ini", "conf", "cfg", "env", "dotenv", "properties" -> SyntaxFamily.CONFIG
        "py", "python", "python3", "sh", "bash", "zsh", "shell", "fish", "console",
        "rb", "ruby", "pl", "perl", "r", "dockerfile", "docker", "makefile", "make", "cmake", "nix" ->
            SyntaxFamily.HASH_SCRIPT
        "c", "h", "cpp", "c++", "cc", "cxx", "hpp", "hh", "objc", "obj-c",
        "kt", "kotlin", "kts", "java", "groovy", "gradle", "scala",
        "js", "javascript", "mjs", "cjs", "ts", "typescript", "jsx", "tsx",
        "rs", "rust", "go", "golang", "swift", "cs", "csharp", "c#", "dart", "php", "zig", "sol", "solidity" ->
            SyntaxFamily.C_LIKE
        "text", "txt", "plain", "plaintext", "log", "logs", "output", "out", "csv", "tsv" ->
            SyntaxFamily.PLAIN
        else -> SyntaxFamily.GENERIC
    }
}

// --- Предкомпилированные токенизаторы по семействам языков ---

private val C_LIKE_REGEX = Regex(
    // 1: Comments (// ... or /* ... */) — # НЕ комментарий в C/Rust/Kotlin/JS!
    """(//[^\n]*|/\*[\s\S]*?\*/)""" +
        // 2: Preprocessor / Rust attributes / Annotations (#include, #if, #[derive], @Composable)
        """|(^[ \t]*#[ \t]*(?:include|define|if|ifdef|ifndef|endif|elif|else|undef|pragma|error|warning|line|region|endregion)\b[^\n]*|#!?\[[^\]]*\]|@[A-Za-z_][A-Za-z0-9_]*)""" +
        // 3: Strings ("""...""", "...", '...', `...`)
        """|("{3}[\s\S]*?"{3}|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|`(?:\\.|[^`\\])*`)""" +
        // 4: Numbers (0x..., 0b..., floats, ints)
        """|(\b(?:0[xX][0-9a-fA-F_]+|0[bB][01_]+|\d+(?:_\d+)*(?:\.\d+(?:_\d+)*)?(?:[fFdDlLuU]+)?)\b)""" +
        // 5: Keywords
        """|(\b(?:val|var|fun|fn|func|function|class|struct|enum|interface|trait|impl|object|type|typedef|namespace|module|package|import|export|from|use|mod|pub|public|private|protected|internal|static|final|const|let|mut|ref|extern|inline|constexpr|virtual|override|abstract|sealed|data|companion|suspend|async|await|return|yield|if|else|when|switch|case|default|match|for|while|do|loop|break|continue|try|catch|finally|throw|throws|new|delete|sizeof|typeof|instanceof|as|is|in|out|where|this|self|super|null|nullptr|nil|undefined|true|false|void|int|long|short|char|byte|float|double|bool|boolean|unsigned|signed|size_t|uint8_t|uint16_t|uint32_t|uint64_t|int8_t|int16_t|int32_t|int64_t|auto|defer|go|chan|select|unsafe)\b)""" +
        // 6: Function calls
        """|(\b[A-Za-z_][A-Za-z0-9_]*(?=\s*\())""",
    setOf(RegexOption.MULTILINE)
)

private val HASH_SCRIPT_REGEX = Regex(
    // 1: Comments (# ...)
    """(#[^\n]*)""" +
        // 2: Decorators / env vars (@decorator, $VAR, ${VAR})
        """|(@[A-Za-z_][A-Za-z0-9_.]*|\$\{?[A-Za-z_][A-Za-z0-9_]*\}?)""" +
        // 3: Strings ("""...""", '''...''', "...", '...')
        """|("{3}[\s\S]*?"{3}|'{3}[\s\S]*?'{3}|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')""" +
        // 4: Numbers
        """|(\b(?:0[xX][0-9a-fA-F_]+|\d+(?:\.\d+)?)\b)""" +
        // 5: Keywords & shell/Docker builtins
        """|(\b(?:def|class|import|from|return|if|elif|else|for|while|try|except|finally|with|as|yield|lambda|async|await|pass|break|continue|raise|del|global|nonlocal|assert|in|is|not|and|or|None|True|False|self|true|false|null|echo|export|local|readonly|fi|then|do|done|esac|case|function|sudo|chmod|chown|grep|awk|sed|cd|ls|cat|rm|cp|mv|mkdir|curl|wget|git|pkg|apt|pip|python|python3|adb|pm|am|FROM|RUN|COPY|ADD|ENV|ARG|WORKDIR|EXPOSE|CMD|ENTRYPOINT)\b)""" +
        // 6: Function calls
        """|(\b[A-Za-z_][A-Za-z0-9_]*(?=\s*\())""",
    setOf(RegexOption.MULTILINE)
)

private val CONFIG_REGEX = Regex(
    // 1: Comments (# ... or ; ... at line start in INI)
    """(#[^\n]*|^[ \t]*;[^\n]*)""" +
        // 2: Section headers ([section] in TOML/INI)
        """|(^[ \t]*\[[^\]\n]+\])""" +
        // 3: Strings ("...", '...')
        """|("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')""" +
        // 4: Keys (key: in YAML or key= in TOML/INI/.env)
        """|(^[ \t]*(?:-[ \t]+)?[A-Za-z_][A-Za-z0-9_.-]*(?=\s*[:=]))""" +
        // 5: Booleans & null
        """|(\b(?:true|false|yes|no|null|on|off)\b)""" +
        // 6: Numbers
        """|(\b(?:0[xX][0-9a-fA-F_]+|\d+(?:\.\d+)?)\b)""",
    setOf(RegexOption.MULTILINE)
)

private val JSON_REGEX = Regex(
    // 1: Comments (JSONC only: // ... or /* ... */)
    """(//[^\n]*|/\*[\s\S]*?\*/)""" +
        // 2: Property keys ("key":)
        """|("(?:\\.|[^"\\])*"(?=\s*:))""" +
        // 3: String values
        """|("(?:\\.|[^"\\])*")""" +
        // 4: Numbers
        """|(-?\b\d+(?:\.\d+)?(?:[eE][+-]?\d+)?\b)""" +
        // 5: Booleans & null
        """|(\b(?:true|false|null)\b)"""
)

private val CSS_REGEX = Regex(
    // 1: Comments (/* ... */ or // ... in SCSS/LESS) — # НЕ комментарий в CSS!
    """(/\*[\s\S]*?\*/|//[^\n]*)""" +
        // 2: At-rules (@media, @import, @keyframes)
        """|(@[A-Za-z-]+)""" +
        // 3: Strings
        """|("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')""" +
        // 4: Hex colors (#fff, #ff5500, #112233aa) and numbers+units
        """|(#[0-9a-fA-F]{3,8}\b|\b\d+(?:\.\d+)?(?:px|dp|sp|em|rem|vh|vw|%|s|ms|deg|fr)?\b)""" +
        // 5: CSS properties (color:, display:)
        """|(\b[a-zA-Z-]+(?=\s*:))""" +
        // 6: CSS functions (rgb(, var(, calc()
        """|(\b[a-zA-Z-]+(?=\s*\())"""
)

private val MARKUP_REGEX = Regex(
    // 1: XML/HTML comments (<!-- ... -->)
    """(<!--[\s\S]*?-->)""" +
        // 2: Strings ("..." or '...')
        """|("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')""" +
        // 3: Tag names (</tag, <tag, <?xml, <!DOCTYPE, />, >, ?>)
        """|(</?[!?]?[A-Za-z_][\w:.-]*|[/?]?>)""" +
        // 4: Attribute names (android:layout_width=)
        """|(\b[A-Za-z_][\w:.-]*(?=\s*=))"""
)

private val ASM_ARM_REGEX = Regex(
    // 1: Comments (// ... or /* ... */ — в ARM64 #0 это immediate, а «;» разделитель инструкций!)
    """(//[^\n]*|/\*[\s\S]*?\*/)""" +
        // 2: Strings
        """|("(?:\\.|[^"\\])*")""" +
        // 3: Immediates (#0, #0x10, #-4) & hex/dec numbers
        """|(#-?(?:0[xX][0-9a-fA-F]+|\d+)|\b0[xX][0-9a-fA-F]+\b|\b\d+\b)""" +
        // 4: Labels (symbol:)
        """|(^[ \t]*[A-Za-z_.$][\w.$]*:)""" +
        // 5: ARM/AArch64 instructions
        """|(\b(?:mov|movz|movk|movn|mvn|ldr|ldrb|ldrh|ldrsw|ldp|str|strb|strh|stp|add|adds|sub|subs|mul|madd|udiv|sdiv|and|ands|orr|eor|bic|lsl|lsr|asr|cmp|cmn|tst|csel|cset|cbz|cbnz|tbz|tbnz|b|bl|blr|br|ret|nop|adr|adrp|svc|brk|mrs|msr|push|pop|bx|blx|beq|bne|bgt|blt|bge|ble)\b)""" +
        // 6: ARM/AArch64 registers (x0..x30, w0..w30, sp, lr, fp, xzr, wzr, pc, v0..v31, r0..r15)
        """|(\b(?:[xwqdsv](?:[12]?\d|30|31)|r(?:1[0-5]|\d)|sp|lr|fp|xzr|wzr|pc)\b)""",
    setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
)

private val ASM_X86_REGEX = Regex(
    // 1: Comments (; ... or // ... or /* ... */ — # всё равно НЕ комментарий)
    """(;[^\n]*|//[^\n]*|/\*[\s\S]*?\*/)""" +
        // 2: Strings
        """|("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')""" +
        // 3: Hex/dec numbers & immediates
        """|(\$?-?(?:0[xX][0-9a-fA-F]+|\b\d+[hH]?\b))""" +
        // 4: Labels
        """|(^[ \t]*[A-Za-z_.$][\w.$]*:)""" +
        // 5: x86/x64 instructions
        """|(\b(?:mov|movzx|movsx|lea|push|pop|add|sub|imul|idiv|mul|div|inc|dec|and|or|xor|not|neg|shl|shr|sar|cmp|test|jmp|je|jne|jz|jnz|jg|jge|jl|jle|ja|jb|call|ret|retn|nop|int|syscall|leave|enter|xchg|cmove|cmovne)\b)""" +
        // 6: x86/x64 registers
        """|(\b(?:[er]?(?:ax|bx|cx|dx|si|di|bp|sp|ip)|[abcd][hl]|r(?:[89]|1[0-5])[dwb]?|xmm\d{1,2})\b)""",
    setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
)

private val SQL_REGEX = Regex(
    // 1: SQL comments (-- ... or /* ... */)
    """(--[^\n]*|/\*[\s\S]*?\*/)""" +
        // 2: Strings
        """|('(?:''|\\.|[^'\\])*'|"(?:\\.|[^"\\])*")""" +
        // 3: Numbers
        """|(\b\d+(?:\.\d+)?\b)""" +
        // 4: SQL keywords
        """|(\b(?:SELECT|DISTINCT|FROM|WHERE|JOIN|INNER|LEFT|RIGHT|OUTER|FULL|CROSS|ON|AS|INSERT|INTO|VALUES|UPDATE|SET|DELETE|CREATE|TABLE|INDEX|VIEW|DROP|ALTER|ADD|COLUMN|PRIMARY|KEY|FOREIGN|REFERENCES|UNIQUE|DEFAULT|CHECK|CONSTRAINT|GROUP|ORDER|BY|ASC|DESC|LIMIT|OFFSET|HAVING|UNION|ALL|CASE|WHEN|THEN|ELSE|END|AND|OR|NOT|IN|EXISTS|BETWEEN|LIKE|ILIKE|IS|NULL|TRUE|FALSE|BEGIN|COMMIT|ROLLBACK|EXPLAIN|ANALYZE|PRAGMA|INTEGER|TEXT|REAL|BLOB|VARCHAR|BOOLEAN|TIMESTAMP)\b)""" +
        // 5: Functions (COUNT(, SUM(, COALESCE()
        """|(\b[A-Za-z_][A-Za-z0-9_]*(?=\s*\())""",
    setOf(RegexOption.IGNORE_CASE)
)

private val GENERIC_REGEX = Regex(
    // 1: Safe comments: // ... or /* ... */ (НЕ режет #include / #0 / #ff5500 в непомеченных блоках!)
    """(//[^\n]*|/\*[\s\S]*?\*/)""" +
        // 2: Strings
        """|("{3}[\s\S]*?"{3}|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')""" +
        // 3: Numbers
        """|(\b(?:0[xX][0-9a-fA-F]+|\d+(?:\.\d+)?(?:[fFLuU]|px|dp|sp)?)\b)""" +
        // 4: Common cross-language keywords
        """|(\b(?:val|var|fun|fn|func|function|class|struct|interface|object|import|package|return|if|else|when|switch|case|for|while|do|try|catch|finally|throw|new|public|private|protected|internal|override|const|let|def|elif|except|with|async|await|null|true|false|None|True|False|SELECT|FROM|WHERE|INSERT|UPDATE|DELETE)\b)""" +
        // 5: Function calls
        """|(\b[A-Za-z_][A-Za-z0-9_]*(?=\s*\())"""
)

/**
 * Языковая подсветка синтаксиса для блоков кода.
 * Поддерживает C-подобные языки, Python/Shell, YAML/TOML/INI, JSON, CSS, XML/HTML,
 * ARM64/x86 ассемблер, SQL, `diff`/`patch` и раздельные палитры для светлой и тёмной тем.
 */
internal fun highlightCodeSyntax(
    code: String,
    language: String = "",
    isDarkTheme: Boolean = true,
    palette: SyntaxPalette = if (isDarkTheme) SyntaxPalette.Dark else SyntaxPalette.Light
): AnnotatedString {
    if (code.isEmpty()) return AnnotatedString("")
    val family = classifySyntaxFamily(language)

    if (family == SyntaxFamily.PLAIN) {
        return AnnotatedString(code)
    }

    if (family == SyntaxFamily.DIFF) {
        return highlightDiff(code, palette)
    }

    return buildAnnotatedString {
        append(code)
        when (family) {
            SyntaxFamily.C_LIKE -> applyMatches(C_LIKE_REGEX, code) { match, start, end ->
                when {
                    match.groups[1] != null -> addStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic), start, end)
                    match.groups[2] != null -> addStyle(SpanStyle(color = palette.preprocessor), start, end)
                    match.groups[3] != null -> addStyle(SpanStyle(color = palette.string), start, end)
                    match.groups[4] != null -> addStyle(SpanStyle(color = palette.number), start, end)
                    match.groups[5] != null -> addStyle(SpanStyle(color = palette.keyword, fontWeight = FontWeight.SemiBold), start, end)
                    match.groups[6] != null -> addStyle(SpanStyle(color = palette.function), start, end)
                }
            }

            SyntaxFamily.HASH_SCRIPT -> applyMatches(HASH_SCRIPT_REGEX, code) { match, start, end ->
                when {
                    match.groups[1] != null -> addStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic), start, end)
                    match.groups[2] != null -> addStyle(SpanStyle(color = palette.preprocessor), start, end)
                    match.groups[3] != null -> addStyle(SpanStyle(color = palette.string), start, end)
                    match.groups[4] != null -> addStyle(SpanStyle(color = palette.number), start, end)
                    match.groups[5] != null -> addStyle(SpanStyle(color = palette.keyword, fontWeight = FontWeight.SemiBold), start, end)
                    match.groups[6] != null -> addStyle(SpanStyle(color = palette.function), start, end)
                }
            }

            SyntaxFamily.CONFIG -> applyMatches(CONFIG_REGEX, code) { match, start, end ->
                when {
                    match.groups[1] != null -> addStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic), start, end)
                    match.groups[2] != null -> addStyle(SpanStyle(color = palette.preprocessor, fontWeight = FontWeight.SemiBold), start, end)
                    match.groups[3] != null -> addStyle(SpanStyle(color = palette.string), start, end)
                    match.groups[4] != null -> {
                        var keyStart = start
                        while (keyStart < end && (code[keyStart] == ' ' || code[keyStart] == '\t' || code[keyStart] == '-')) {
                            keyStart++
                        }
                        if (keyStart < end) {
                            addStyle(SpanStyle(color = palette.property, fontWeight = FontWeight.Medium), keyStart, end)
                        }
                    }
                    match.groups[5] != null -> addStyle(SpanStyle(color = palette.keyword, fontWeight = FontWeight.SemiBold), start, end)
                    match.groups[6] != null -> addStyle(SpanStyle(color = palette.number), start, end)
                }
            }

            SyntaxFamily.JSON -> applyMatches(JSON_REGEX, code) { match, start, end ->
                when {
                    match.groups[1] != null -> addStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic), start, end)
                    match.groups[2] != null -> addStyle(SpanStyle(color = palette.property, fontWeight = FontWeight.Medium), start, end)
                    match.groups[3] != null -> addStyle(SpanStyle(color = palette.string), start, end)
                    match.groups[4] != null -> addStyle(SpanStyle(color = palette.number), start, end)
                    match.groups[5] != null -> addStyle(SpanStyle(color = palette.keyword, fontWeight = FontWeight.SemiBold), start, end)
                }
            }

            SyntaxFamily.CSS -> applyMatches(CSS_REGEX, code) { match, start, end ->
                when {
                    match.groups[1] != null -> addStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic), start, end)
                    match.groups[2] != null -> addStyle(SpanStyle(color = palette.preprocessor, fontWeight = FontWeight.SemiBold), start, end)
                    match.groups[3] != null -> addStyle(SpanStyle(color = palette.string), start, end)
                    match.groups[4] != null -> addStyle(SpanStyle(color = palette.number), start, end)
                    match.groups[5] != null -> addStyle(SpanStyle(color = palette.property), start, end)
                    match.groups[6] != null -> addStyle(SpanStyle(color = palette.function), start, end)
                }
            }

            SyntaxFamily.MARKUP -> applyMatches(MARKUP_REGEX, code) { match, start, end ->
                when {
                    match.groups[1] != null -> addStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic), start, end)
                    match.groups[2] != null -> addStyle(SpanStyle(color = palette.string), start, end)
                    match.groups[3] != null -> addStyle(SpanStyle(color = palette.keyword, fontWeight = FontWeight.SemiBold), start, end)
                    match.groups[4] != null -> addStyle(SpanStyle(color = palette.property), start, end)
                }
            }

            SyntaxFamily.ASM_ARM -> applyMatches(ASM_ARM_REGEX, code) { match, start, end ->
                when {
                    match.groups[1] != null -> addStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic), start, end)
                    match.groups[2] != null -> addStyle(SpanStyle(color = palette.string), start, end)
                    match.groups[3] != null -> addStyle(SpanStyle(color = palette.number), start, end)
                    match.groups[4] != null -> addStyle(SpanStyle(color = palette.function, fontWeight = FontWeight.SemiBold), start, end)
                    match.groups[5] != null -> addStyle(SpanStyle(color = palette.keyword, fontWeight = FontWeight.SemiBold), start, end)
                    match.groups[6] != null -> addStyle(SpanStyle(color = palette.property), start, end)
                }
            }

            SyntaxFamily.ASM_X86 -> applyMatches(ASM_X86_REGEX, code) { match, start, end ->
                when {
                    match.groups[1] != null -> addStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic), start, end)
                    match.groups[2] != null -> addStyle(SpanStyle(color = palette.string), start, end)
                    match.groups[3] != null -> addStyle(SpanStyle(color = palette.number), start, end)
                    match.groups[4] != null -> addStyle(SpanStyle(color = palette.function, fontWeight = FontWeight.SemiBold), start, end)
                    match.groups[5] != null -> addStyle(SpanStyle(color = palette.keyword, fontWeight = FontWeight.SemiBold), start, end)
                    match.groups[6] != null -> addStyle(SpanStyle(color = palette.property), start, end)
                }
            }

            SyntaxFamily.SQL -> applyMatches(SQL_REGEX, code) { match, start, end ->
                when {
                    match.groups[1] != null -> addStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic), start, end)
                    match.groups[2] != null -> addStyle(SpanStyle(color = palette.string), start, end)
                    match.groups[3] != null -> addStyle(SpanStyle(color = palette.number), start, end)
                    match.groups[4] != null -> addStyle(SpanStyle(color = palette.keyword, fontWeight = FontWeight.SemiBold), start, end)
                    match.groups[5] != null -> addStyle(SpanStyle(color = palette.function), start, end)
                }
            }

            SyntaxFamily.GENERIC -> applyMatches(GENERIC_REGEX, code) { match, start, end ->
                when {
                    match.groups[1] != null -> addStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic), start, end)
                    match.groups[2] != null -> addStyle(SpanStyle(color = palette.string), start, end)
                    match.groups[3] != null -> addStyle(SpanStyle(color = palette.number), start, end)
                    match.groups[4] != null -> addStyle(SpanStyle(color = palette.keyword, fontWeight = FontWeight.SemiBold), start, end)
                    match.groups[5] != null -> addStyle(SpanStyle(color = palette.function), start, end)
                }
            }

            SyntaxFamily.DIFF, SyntaxFamily.PLAIN -> Unit
        }
    }
}

private inline fun applyMatches(
    regex: Regex,
    code: String,
    action: (MatchResult, Int, Int) -> Unit
) {
    regex.findAll(code).forEach { match ->
        action(match, match.range.first, match.range.last + 1)
    }
}

private fun highlightDiff(code: String, palette: SyntaxPalette): AnnotatedString {
    return buildAnnotatedString {
        append(code)
        var lineStart = 0
        val len = code.length
        while (lineStart <= len) {
            val lineEnd = code.indexOf('\n', lineStart).let { if (it < 0) len else it }
            if (lineEnd > lineStart) {
                val line = code.substring(lineStart, lineEnd)
                val style = when {
                    line.startsWith("+++") || line.startsWith("---") ||
                        line.startsWith("diff ") || line.startsWith("index ") ->
                        SpanStyle(color = palette.comment, fontWeight = FontWeight.SemiBold)
                    line.startsWith("@@") ->
                        SpanStyle(
                            color = palette.diffHeaderText,
                            background = palette.diffHeaderBg,
                            fontWeight = FontWeight.SemiBold
                        )
                    line.startsWith("+") ->
                        SpanStyle(
                            color = palette.diffAddedText,
                            background = palette.diffAddedBg
                        )
                    line.startsWith("-") ->
                        SpanStyle(
                            color = palette.diffRemovedText,
                            background = palette.diffRemovedBg
                        )
                    else -> null
                }
                if (style != null) {
                    addStyle(style, lineStart, lineEnd)
                }
            }
            if (lineEnd >= len) break
            lineStart = lineEnd + 1
        }
    }
}
