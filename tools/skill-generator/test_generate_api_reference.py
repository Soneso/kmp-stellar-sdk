#!/usr/bin/env python3
"""Exercise API reference parsing with isolated Kotlin source fixtures."""

import os
from pathlib import Path
import sys
import tempfile
import textwrap
import unittest

sys.path.insert(0, os.path.dirname(__file__))
import generate_api_reference as generator


class ClassParsing(unittest.TestCase):
    """Check visibility, complete headers and the members attached to each class."""

    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="skill-generator-")
        self.addCleanup(self.directory.cleanup)
        self.path = Path(self.directory.name) / "Fixture.kt"

    def parse(self, source):
        self.path.write_text(textwrap.dedent(source), encoding="utf-8")
        classes, functions = generator.parse_kotlin_file(self.path)
        self.assertEqual(functions, [])
        return {info.name: info for info in classes}

    def test_non_public_enclosures(self):
        """Exclude descendants and their members at every depth and restore sibling scope."""
        for visibility in ("internal", "private", "protected"):
            for declaration in ("class Hidden", "interface Hidden", "object Hidden",
                                "enum class Hidden", "data class Hidden(val id: Int)",
                                "sealed class Hidden"):
                with self.subTest(visibility=visibility, declaration=declaration):
                    enum_values = "VALUE;" if declaration.startswith("enum") else ""
                    classes = self.parse(f"""
                        class Public {{
                            {visibility} {declaration} {{
                                {enum_values}
                                class Child {{
                                    val secret: String = "secret"
                                    class Grandchild {{
                                        fun hidden(): Int = 1
                                    }}
                                }}
                                object NestedObject {{
                                    val secret: Int = 1
                                }}
                            }}
                            class Visible {{
                                val value: Int = 2
                            }}
                        }}
                        class Sibling
                    """)
                    self.assertEqual(list(classes), ["Public", "Public.Visible", "Sibling"])
                    self.assertEqual(classes["Public"].properties, [])
                    self.assertEqual(classes["Public"].methods, [])
                    self.assertEqual(classes["Public.Visible"].properties, ["val value: Int"])

    def test_non_public_top_level_with_public_sibling(self):
        """A non-public outer does not expose nested types or hide its public neighbours."""
        for visibility in ("internal", "private"):
            with self.subTest(visibility=visibility):
                classes = self.parse(f"""
                    class Before
                    {visibility} sealed class Hidden {{
                        data class Child(val id: Int) : Hidden()
                        data object Singleton : Hidden()
                    }}
                    public class After {{
                        class Nested
                        fun visible(): Int = 1
                    }}
                """)
                self.assertEqual(list(classes), ["Before", "After", "After.Nested"])
                self.assertEqual(classes["After"].methods, ["fun visible(): Int"])

    def test_non_public_body_char_literals(self):
        """Quotes and braces in char literals preserve enclosure and sibling scope."""
        for visibility in ("internal", "private"):
            for literal in ("'\"'", "'{'", "'}'"):
                with self.subTest(visibility=visibility, literal=literal):
                    classes = self.parse(f"""
                        {visibility} object Hidden {{
                            fun delimiter(): Char {{ return {literal} }}
                            class Child {{
                                class Grandchild
                            }}
                        }}
                        public class PublicApi {{
                            fun run(): Int = 1
                        }}
                    """)
                    self.assertEqual(list(classes), ["PublicApi"])
                    self.assertEqual(classes["PublicApi"].methods, ["fun run(): Int"])

    def test_non_public_wrapped_header(self):
        """Visibility propagates through an enclosure with a wrapped constructor and bases."""
        classes = self.parse("""
            internal class Hidden(
                val id: Int,
            ) : First,
                Second {
                class Child {
                    class Grandchild
                }
            }
            class Visible
        """)
        self.assertEqual(list(classes), ["Visible"])

    def check_wrapped_types(self, supertypes):
        declarations = (("class Sample", ""), ("interface Sample", "interface"),
                        ("object Sample", "object"), ("enum class Sample", "enum"),
                        ("data class Sample(val id: Int)", "data"),
                        ("sealed class Sample", "sealed"))
        for declaration, kind in declarations:
            for nested in (False, True):
                with self.subTest(declaration=declaration, nested=nested):
                    enum_values = "VALUE;" if kind == "enum" else ""
                    source = f"""{declaration} : {supertypes} {{
                        {enum_values}
                        val detail: String = "detail"
                        fun run(): Int = 1
                        class Child
                    }}
                    class Sibling
                    """
                    if nested:
                        source = "class Outer {\n" + source + "\n}"
                    classes = self.parse(source)
                    prefix = "Outer." if nested else ""
                    self.assertEqual(list(classes), (["Outer"] if nested else []) +
                                     [prefix + "Sample", prefix + "Sample.Child", prefix + "Sibling"])
                    info = classes[prefix + "Sample"]
                    self.assertEqual(info.kind, kind)
                    self.assertEqual(info.parent, "First")
                    self.assertEqual(info.interfaces, ["Second", "Third"])
                    self.assertEqual(info.properties, (["val id: Int"] if kind == "data" else []) +
                                     ["val detail: String"])
                    self.assertEqual(info.methods, ["fun run(): Int"])
                    if kind == "enum":
                        self.assertEqual(info.enum_values, ["VALUE"])

    def test_wrapped_supertypes(self):
        """Continue inheritance after a comma and retain each declaration's body."""
        self.check_wrapped_types("First,\nSecond,\nThird")

    def test_supertypes_after_colon(self):
        """A newline after the colon still starts an inheritance list."""
        self.check_wrapped_types("\nFirst, Second, Third")

    def test_supertypes_with_leading_commas(self):
        """Line breaks before separators do not terminate the inheritance list."""
        self.check_wrapped_types("First\n, Second\n, Third\n")

    def test_wrapped_constructor_and_supertypes(self):
        """Preserve balanced parameters, all bases, body members and secondary constructors."""
        for visibility in ("", "private", "internal", "protected"):
            for trailing_comma in ("", ","):
                with self.subTest(visibility=visibility, trailing_comma=trailing_comma):
                    classes = self.parse(f"""
                        class Sample<T>
                        {visibility} constructor
                        (
                            val id: Int,
                            val items: Map<String, List<T>> = emptyMap(){trailing_comma}
                        )
                        :
                            Base
                            (
                                id,
                            ),
                            First<Map<String, List<T>>>,
                            Second
                        {{
                            val detail: String = "detail"
                            constructor(message: String, cause: Throwable? = null) : this(0)
                            class Child
                        }}
                        class Sibling
                    """)
                    self.assertEqual(list(classes), ["Sample", "Sample.Child", "Sibling"])
                    info = classes["Sample"]
                    self.assertEqual(info.type_params, "<T>")
                    self.assertEqual(info.constructor_public, not visibility)
                    self.assertEqual(generator.clean_method_params(info.constructor_params),
                                     "val id: Int, val items: Map<String, List<T>> = emptyMap()")
                    self.assertEqual(info.parent, "Base")
                    self.assertEqual(info.interfaces, ["First<Map<String, List<T>>>", "Second"])
                    self.assertEqual(info.properties,
                                     ["val id: Int", "val items: Map<String, List<T>>", "val detail: String"])
                    self.assertEqual(info.methods, ["constructor(message: String, cause: Throwable? = null)"])

    def test_bodyless_wrapped_supertypes(self):
        """Stop bodyless headers before siblings, enclosing braces, semicolons or EOF."""
        for ending in ("", "\nclass Sibling", ";\nclass Sibling"):
            for nested in (False, True):
                with self.subTest(ending=ending, nested=nested):
                    source = f"class Sample :\n    First,\n    Second{ending}"
                    if nested:
                        source = "class Outer {\n" + source + "\n}"
                    classes = self.parse(source)
                    prefix = "Outer." if nested else ""
                    expected = (["Outer"] if nested else []) + [prefix + "Sample"]
                    if ending:
                        expected.append(prefix + "Sibling")
                    self.assertEqual(list(classes), expected)
                    self.assertEqual(classes[prefix + "Sample"].parent, "First")
                    self.assertEqual(classes[prefix + "Sample"].interfaces, ["Second"])
                    self.assertEqual(classes[prefix + "Sample"].properties, [])
                    self.assertEqual(classes[prefix + "Sample"].methods, [])


if __name__ == "__main__":
    unittest.main(verbosity=2)
