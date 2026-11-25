package com.runner.app

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.runner.app.ui.components.SyntaxFamily
import com.runner.app.ui.components.SyntaxPalette
import com.runner.app.ui.components.classifySyntaxFamily
import com.runner.app.ui.components.formatMathFallback
import com.runner.app.ui.components.highlightCodeSyntax
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownStage4Test {

    @Test
    fun classifySyntaxFamily_mapsLanguageAliasesAccurately() {
        assertEquals(SyntaxFamily.DIFF, classifySyntaxFamily("diff"))
        assertEquals(SyntaxFamily.DIFF, classifySyntaxFamily("PATCH"))
        assertEquals(SyntaxFamily.JSON, classifySyntaxFamily("json"))
        assertEquals(SyntaxFamily.JSON, classifySyntaxFamily("jsonc"))
        assertEquals(SyntaxFamily.CSS, classifySyntaxFamily("css"))
        assertEquals(SyntaxFamily.CSS, classifySyntaxFamily("scss"))
        assertEquals(SyntaxFamily.MARKUP, classifySyntaxFamily("xml"))
        assertEquals(SyntaxFamily.MARKUP, classifySyntaxFamily("html"))
        assertEquals(SyntaxFamily.ASM_ARM, classifySyntaxFamily("arm64"))
        assertEquals(SyntaxFamily.ASM_ARM, classifySyntaxFamily("aarch64"))
        assertEquals(SyntaxFamily.ASM_X86, classifySyntaxFamily("x86_64"))
        assertEquals(SyntaxFamily.ASM_X86, classifySyntaxFamily("asm"))
        assertEquals(SyntaxFamily.SQL, classifySyntaxFamily("sql"))
        assertEquals(SyntaxFamily.SQL, classifySyntaxFamily("sqlite"))
        assertEquals(SyntaxFamily.CONFIG, classifySyntaxFamily("yaml"))
        assertEquals(SyntaxFamily.CONFIG, classifySyntaxFamily("toml"))
        assertEquals(SyntaxFamily.CONFIG, classifySyntaxFamily("env"))
        assertEquals(SyntaxFamily.HASH_SCRIPT, classifySyntaxFamily("py"))
        assertEquals(SyntaxFamily.HASH_SCRIPT, classifySyntaxFamily("bash"))
        assertEquals(SyntaxFamily.HASH_SCRIPT, classifySyntaxFamily("dockerfile"))
        assertEquals(SyntaxFamily.C_LIKE, classifySyntaxFamily("c"))
        assertEquals(SyntaxFamily.C_LIKE, classifySyntaxFamily("cpp"))
        assertEquals(SyntaxFamily.C_LIKE, classifySyntaxFamily("kotlin {linenos=true}"))
        assertEquals(SyntaxFamily.C_LIKE, classifySyntaxFamily("rs"))
        assertEquals(SyntaxFamily.C_LIKE, classifySyntaxFamily("ts"))
        assertEquals(SyntaxFamily.PLAIN, classifySyntaxFamily("txt"))
        assertEquals(SyntaxFamily.PLAIN, classifySyntaxFamily("log"))
        assertEquals(SyntaxFamily.GENERIC, classifySyntaxFamily(""))
        assertEquals(SyntaxFamily.GENERIC, classifySyntaxFamily("customlang"))
    }

    @Test
    fun cLikeHighlighting_doesNotTreatPreprocessorOrRustAttributesAsComments() {
        val cppCode = """
            #include <stdio.h>
            // real comment
            int main() { return 0; }
        """.trimIndent()
        val cppHighlighted = highlightCodeSyntax(cppCode, language = "cpp", isDarkTheme = true)

        val includeEnd = "#include <stdio.h>".length
        assertTrue(
            cppHighlighted.spanStyles.any {
                it.start == 0 && it.end == includeEnd &&
                    it.item.color == SyntaxPalette.Dark.preprocessor &&
                    it.item.fontStyle != FontStyle.Italic
            }
        )
        assertFalse(
            cppHighlighted.spanStyles.any {
                it.start < includeEnd && it.item.color == SyntaxPalette.Dark.comment
            }
        )
        val commentStart = cppCode.indexOf("// real comment")
        assertTrue(
            cppHighlighted.spanStyles.any {
                it.start == commentStart &&
                    it.item.color == SyntaxPalette.Dark.comment &&
                    it.item.fontStyle == FontStyle.Italic
            }
        )
        val mainStart = cppCode.indexOf("main")
        assertTrue(
            cppHighlighted.spanStyles.any {
                it.start == mainStart && it.end == mainStart + 4 &&
                    it.item.color == SyntaxPalette.Dark.function
            }
        )

        val rustCode = """
            #[derive(Debug, Clone)]
            pub fn unlock() -> bool { true }
        """.trimIndent()
        val rustHighlighted = highlightCodeSyntax(rustCode, language = "rust", isDarkTheme = true)
        val attrEnd = "#[derive(Debug, Clone)]".length
        assertTrue(
            rustHighlighted.spanStyles.any {
                it.start == 0 && it.end == attrEnd &&
                    it.item.color == SyntaxPalette.Dark.preprocessor
            }
        )
        assertFalse(
            rustHighlighted.spanStyles.any { it.item.color == SyntaxPalette.Dark.comment }
        )
    }

    @Test
    fun arm64CssAndGenericHighlighting_preserveHashImmediatesAndHexColors() {
        val armCode = "mov w0, #0; ret"
        val armHighlighted = highlightCodeSyntax(armCode, language = "arm64", isDarkTheme = true)

        assertFalse(
            armHighlighted.spanStyles.any { it.item.color == SyntaxPalette.Dark.comment }
        )
        assertTrue(
            armHighlighted.spanStyles.any {
                it.start == 0 && it.end == 3 && it.item.color == SyntaxPalette.Dark.keyword
            }
        )
        assertTrue(
            armHighlighted.spanStyles.any {
                it.start == 4 && it.end == 6 && it.item.color == SyntaxPalette.Dark.property
            }
        )
        assertTrue(
            armHighlighted.spanStyles.any {
                it.start == 8 && it.end == 10 && it.item.color == SyntaxPalette.Dark.number
            }
        )
        assertTrue(
            armHighlighted.spanStyles.any {
                it.start == 12 && it.end == 15 && it.item.color == SyntaxPalette.Dark.keyword
            }
        )

        val cssCode = "body { color: #ff5500; margin: 12px; }"
        val cssHighlighted = highlightCodeSyntax(cssCode, language = "css", isDarkTheme = true)
        assertFalse(
            cssHighlighted.spanStyles.any { it.item.color == SyntaxPalette.Dark.comment }
        )
        val hexStart = cssCode.indexOf("#ff5500")
        assertTrue(
            cssHighlighted.spanStyles.any {
                it.start == hexStart && it.end == hexStart + 7 &&
                    it.item.color == SyntaxPalette.Dark.number
            }
        )
        val colorPropStart = cssCode.indexOf("color")
        assertTrue(
            cssHighlighted.spanStyles.any {
                it.start == colorPropStart && it.end == colorPropStart + 5 &&
                    it.item.color == SyntaxPalette.Dark.property
            }
        )

        val genericHighlighted = highlightCodeSyntax(
            code = "mov w0, #0; color: #ff5500",
            language = "",
            isDarkTheme = true
        )
        assertFalse(
            genericHighlighted.spanStyles.any { it.item.color == SyntaxPalette.Dark.comment }
        )
    }

    @Test
    fun pythonConfigSqlMarkupAndJsonHighlighting_applyLanguageSpecificRules() {
        val pyCode = """
            # comment
            if ok:
                run()
            else:
                pass
        """.trimIndent()
        val pyHighlighted = highlightCodeSyntax(pyCode, language = "python", isDarkTheme = true)
        assertTrue(
            pyHighlighted.spanStyles.any {
                it.start == 0 && it.item.color == SyntaxPalette.Dark.comment &&
                    it.item.fontStyle == FontStyle.Italic
            }
        )
        val elseStart = pyCode.indexOf("else")
        assertTrue(
            pyHighlighted.spanStyles.any {
                it.start == elseStart && it.end == elseStart + 4 &&
                    it.item.color == SyntaxPalette.Dark.keyword
            }
        )

        val yamlCode = """
            # config
            model: deepseek
            enabled: true
            retries: 3
        """.trimIndent()
        val yamlHighlighted = highlightCodeSyntax(yamlCode, language = "yaml", isDarkTheme = true)
        val modelStart = yamlCode.indexOf("model")
        assertTrue(
            yamlHighlighted.spanStyles.any {
                it.start == modelStart && it.end == modelStart + 5 &&
                    it.item.color == SyntaxPalette.Dark.property
            }
        )
        val trueStart = yamlCode.indexOf("true")
        assertTrue(
            yamlHighlighted.spanStyles.any {
                it.start == trueStart && it.end == trueStart + 4 &&
                    it.item.color == SyntaxPalette.Dark.keyword
            }
        )

        val sqlCode = "-- fetch users\nSELECT COUNT(id) FROM users WHERE active = 1;"
        val sqlHighlighted = highlightCodeSyntax(sqlCode, language = "sql", isDarkTheme = true)
        assertTrue(
            sqlHighlighted.spanStyles.any {
                it.start == 0 && it.item.color == SyntaxPalette.Dark.comment &&
                    it.item.fontStyle == FontStyle.Italic
            }
        )
        val selectStart = sqlCode.indexOf("SELECT")
        assertTrue(
            sqlHighlighted.spanStyles.any {
                it.start == selectStart && it.end == selectStart + 6 &&
                    it.item.color == SyntaxPalette.Dark.keyword
            }
        )
        val countStart = sqlCode.indexOf("COUNT")
        assertTrue(
            sqlHighlighted.spanStyles.any {
                it.start == countStart && it.end == countStart + 5 &&
                    it.item.color == SyntaxPalette.Dark.function
            }
        )

        val xmlCode = """<!-- banner --><TextView android:text="ok"/>"""
        val xmlHighlighted = highlightCodeSyntax(xmlCode, language = "xml", isDarkTheme = true)
        assertTrue(
            xmlHighlighted.spanStyles.any {
                it.start == 0 && it.item.color == SyntaxPalette.Dark.comment
            }
        )
        val attrStart = xmlCode.indexOf("android:text")
        assertTrue(
            xmlHighlighted.spanStyles.any {
                it.start == attrStart && it.end == attrStart + 12 &&
                    it.item.color == SyntaxPalette.Dark.property
            }
        )

        val jsonCode = """{"effort": "high", "count": 4, "enabled": true}"""
        val jsonHighlighted = highlightCodeSyntax(jsonCode, language = "json", isDarkTheme = true)
        val keyStart = jsonCode.indexOf("\"effort\"")
        val valStart = jsonCode.indexOf("\"high\"")
        assertTrue(
            jsonHighlighted.spanStyles.any {
                it.start == keyStart && it.item.color == SyntaxPalette.Dark.property
            }
        )
        assertTrue(
            jsonHighlighted.spanStyles.any {
                it.start == valStart && it.item.color == SyntaxPalette.Dark.string
            }
        )
    }

    @Test
    fun diffPlainAndThemePalettes_behaveCorrectly() {
        val diffCode = """
            --- a/app.kt
            +++ b/app.kt
            @@ -1,2 +1,2 @@
            -val old = 1
            +val new = 2
             val same = 3
        """.trimIndent()
        val diffHighlighted = highlightCodeSyntax(diffCode, language = "diff", isDarkTheme = true)

        val headerFileStart = diffCode.indexOf("--- a/app.kt")
        assertTrue(
            diffHighlighted.spanStyles.any {
                it.start == headerFileStart &&
                    it.item.color == SyntaxPalette.Dark.comment &&
                    it.item.fontWeight == FontWeight.SemiBold
            }
        )
        val hunkStart = diffCode.indexOf("@@ -1,2 +1,2 @@")
        assertTrue(
            diffHighlighted.spanStyles.any {
                it.start == hunkStart &&
                    it.item.color == SyntaxPalette.Dark.diffHeaderText &&
                    it.item.background == SyntaxPalette.Dark.diffHeaderBg
            }
        )
        val removedStart = diffCode.indexOf("-val old = 1")
        assertTrue(
            diffHighlighted.spanStyles.any {
                it.start == removedStart &&
                    it.item.color == SyntaxPalette.Dark.diffRemovedText &&
                    it.item.background == SyntaxPalette.Dark.diffRemovedBg
            }
        )
        val addedStart = diffCode.indexOf("+val new = 2")
        assertTrue(
            diffHighlighted.spanStyles.any {
                it.start == addedStart &&
                    it.item.color == SyntaxPalette.Dark.diffAddedText &&
                    it.item.background == SyntaxPalette.Dark.diffAddedBg
            }
        )

        val plainHighlighted = highlightCodeSyntax(
            code = "# not comment\nval x = 1\n+not diff",
            language = "txt",
            isDarkTheme = true
        )
        assertTrue(plainHighlighted.spanStyles.isEmpty())

        val darkKt = highlightCodeSyntax("val x = 1", language = "kotlin", isDarkTheme = true)
        val lightKt = highlightCodeSyntax("val x = 1", language = "kotlin", isDarkTheme = false)
        assertNotEquals(SyntaxPalette.Dark.keyword, SyntaxPalette.Light.keyword)
        assertTrue(darkKt.spanStyles.any { it.item.color == SyntaxPalette.Dark.keyword })
        assertTrue(lightKt.spanStyles.any { it.item.color == SyntaxPalette.Light.keyword })
    }

    @Test
    fun formatMathFallback_formatsFractionsRootsGreekScriptsAndAvoidsPrefixBugs() {
        assertEquals("(a+b)/2", formatMathFallback("\\frac{a+b}{2}"))
        assertEquals("d/t", formatMathFallback("\\frac{d}{t}"))
        assertEquals("1/(2x)", formatMathFallback("\\frac{1}{2x}"))
        assertEquals("√(x² + y²)", formatMathFallback("\\sqrt{x^2 + y^2}"))
        assertEquals("√2", formatMathFallback("\\sqrt{2}"))
        assertEquals("∛27", formatMathFallback("\\sqrt[3]{27}"))
        assertEquals("∑ᵢ₌₁ⁿ αᵢ", formatMathFallback("\\sum_{i=1}^{n} \\alpha_i"))
        assertEquals("Скорость = d/t", formatMathFallback("\\text{Скорость} = \\frac{d}{t}"))
        assertEquals("∀ x ∈ ℝ, x² ≥ 0", formatMathFallback("\\forall x \\in \\mathbb{R}, \\; x^2 \\ge 0"))
        assertEquals("a ≤ b ≠ c → ∞", formatMathFallback("a \\leq b \\neq c \\to \\infty"))
        assertEquals("lim_(x → ∞) 1/x = 0", formatMathFallback("\\lim_{x \\to \\infty} \\frac{1}{x} = 0"))
        assertEquals("a + b", formatMathFallback("\\frac{a + b"))
    }
}
