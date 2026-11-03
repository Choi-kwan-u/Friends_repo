package level;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LevelJsonParserTest {
    @Test
    void decodesEscapesAndPreservesJsonTypes() throws Exception {
        Map<?, ?> parsed = (Map<?, ?>) parse("{\"name\":\"한글 \\ud83d\\ude80\\n\\t\\b\\f\\r\\/\\\\\\\"\","
                + "\"values\":[null,true,false,12,\"12\"]}");
        assertEquals("한글 \ud83d\ude80\n\t\b\f\r/\\\"", parsed.get("name"));
        List<?> values = (List<?>) parsed.get("values");
        assertNull(values.get(0));
        assertEquals(Boolean.TRUE, values.get(1));
        assertEquals(Boolean.FALSE, values.get(2));
        assertEquals(12, ((LevelJsonParser.NumberValue) values.get(3)).integer("values[3]"));
        assertEquals("12", values.get(4));
    }

    @Test
    void rejectsDuplicateDecodedKeysIncludingNullValues() {
        for (String input : new String[] { "{\"id\":null,\"id\":1}",
                "{\"id\":1,\"\\u0069d\":2}", "{\"x\":{\"id\":1,\"id\":2}}" }) {
            LevelFormatException error = assertThrows(LevelFormatException.class, () -> parse(input));
            assertTrue(error.getMessage().contains("duplicate object key"));
        }
    }

    @Test
    void rejectsNonJsonSyntaxAndMalformedUnicode() {
        String[] invalid = { "", " ", "{}{}", "[1,]", "{\"x\":1,}", "{x:1}", "{'x':1}",
            "// comment\n{}", "[01]", "[+1]", "[-]", "[.1]", "[1.]", "[1e+]", "[NaN]",
            "[Infinity]", "[true false]", "[null] trailing", "[\u00a01]", "{\"x\" 1}",
            "\"bad\ntext\"", "\"\\x\"", "\"\\u00xz\"", "\"\\u123\"", "\"unterminated",
            "\"\\ud800\"", "\"\\udc00\"", "\"\\ud800x\"", "\"\\udc00\\ud800\"" };
        for (String input : invalid) {
            assertThrows(LevelFormatException.class, () -> parse(input), input);
        }
    }

    @Test
    void convertsIntegralNumbersExactlyWithoutTruncationOrOverflow() throws Exception {
        assertEquals(1, number("1.0").integer("n"));
        assertEquals(100, number("1e2").integer("n"));
        assertEquals(Integer.MAX_VALUE, number("2147483647").integer("n"));
        assertEquals(Integer.MIN_VALUE, number("-2147483648").integer("n"));
        for (String input : new String[] { "1.5", "2147483648", "-2147483649", "1e1000000", "1e-1000000" }) {
            LevelFormatException error = assertThrows(LevelFormatException.class,
                    () -> number(input).integer("waves[0].health"));
            assertTrue(error.getMessage().startsWith("waves[0].health:"));
        }
    }

    @Test
    void boundsNestingAndReportsTheFailureLocation() {
        StringBuilder input = new StringBuilder();
        for (int index = 0; index < 10000; index++) input.append('[');
        LevelFormatException depth = assertThrows(LevelFormatException.class, () -> parse(input.toString()));
        assertTrue(depth.getMessage().contains("nesting exceeds"));
        LevelFormatException syntax = assertThrows(LevelFormatException.class,
                () -> parse("{\n  \"waves\": [1,]\n}"));
        assertTrue(syntax.getMessage().contains("waves[1]"));
        assertTrue(syntax.getMessage().contains("line 2"));
    }

    private static Object parse(final String value) throws LevelFormatException {
        return new LevelJsonParser(value).parse();
    }

    private static LevelJsonParser.NumberValue number(final String value) throws LevelFormatException {
        return (LevelJsonParser.NumberValue) parse(value);
    }
}
