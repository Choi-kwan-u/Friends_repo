package level;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Strict JSON syntax for the level reader; no external parser dependency. */
final class LevelJsonParser {
    // A valid v1 file needs seven container levels. Bound invalid nesting too.
    private static final int MAX_NESTING = 16;
    private final String input;
    private int position;

    LevelJsonParser(final String input) {
        this.input = input;
    }

    Object parse() throws LevelFormatException {
        Object result = value("$", 0);
        whitespace();
        if (position != input.length()) {
            throw error("$", "unexpected content after the JSON value");
        }
        return result;
    }

    private Object value(final String path, final int depth) throws LevelFormatException {
        whitespace();
        if (position == input.length()) throw error(path, "expected a JSON value");
        char first = input.charAt(position);
        if (first == '{' || first == '[') {
            if (depth >= MAX_NESTING) throw error(path, "JSON nesting exceeds " + MAX_NESTING);
            return first == '{' ? object(path, depth + 1) : array(path, depth + 1);
        }
        if (first == '"') return string(path);
        if (first == 't') return literal("true", Boolean.TRUE, path);
        if (first == 'f') return literal("false", Boolean.FALSE, path);
        if (first == 'n') return literal("null", null, path);
        if (first == '-' || digit(first)) return number(path);
        throw error(path, "expected a JSON value");
    }

    private Map<String, Object> object(final String path, final int depth)
            throws LevelFormatException {
        position++;
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        whitespace();
        if (take('}')) return result;
        while (true) {
            whitespace();
            if (position == input.length() || input.charAt(position) != '"') {
                throw error(path, "expected a quoted object key");
            }
            String key = string(path);
            String field = "$".equals(path) ? key : path + "." + key;
            if (result.containsKey(key)) throw error(field, "duplicate object key");
            whitespace();
            expect(':', field);
            result.put(key, value(field, depth));
            whitespace();
            if (take('}')) return result;
            expect(',', path);
        }
    }

    private List<Object> array(final String path, final int depth) throws LevelFormatException {
        position++;
        List<Object> result = new ArrayList<Object>();
        whitespace();
        if (take(']')) return result;
        while (true) {
            result.add(value(path + "[" + result.size() + "]", depth));
            whitespace();
            if (take(']')) return result;
            expect(',', path);
        }
    }

    private String string(final String path) throws LevelFormatException {
        expect('"', path);
        StringBuilder result = new StringBuilder();
        while (position < input.length()) {
            char character = input.charAt(position++);
            if (character == '"') {
                String text = result.toString();
                LevelValidator.validateUnicode(text, path);
                return text;
            }
            if (character < 0x20) throw error(path, "unescaped control character in string");
            if (character != '\\') {
                result.append(character);
                continue;
            }
            if (position == input.length()) throw error(path, "unfinished string escape");
            char escape = input.charAt(position++);
            switch (escape) {
            case '"': case '\\': case '/': result.append(escape); break;
            case 'b': result.append('\b'); break;
            case 'f': result.append('\f'); break;
            case 'n': result.append('\n'); break;
            case 'r': result.append('\r'); break;
            case 't': result.append('\t'); break;
            case 'u': result.append(unicode(path)); break;
            default: throw error(path, "unknown string escape");
            }
        }
        throw error(path, "unterminated string");
    }

    private char unicode(final String path) throws LevelFormatException {
        int result = 0;
        for (int index = 0; index < 4; index++) {
            if (position == input.length()) throw error(path, "incomplete Unicode escape");
            char character = input.charAt(position++);
            int hex;
            if (character >= '0' && character <= '9') hex = character - '0';
            else if (character >= 'a' && character <= 'f') hex = character - 'a' + 10;
            else if (character >= 'A' && character <= 'F') hex = character - 'A' + 10;
            else throw error(path, "invalid Unicode escape");
            result = result * 16 + hex;
        }
        return (char) result;
    }

    private NumberValue number(final String path) throws LevelFormatException {
        int start = position;
        take('-');
        if (take('0')) {
            if (position < input.length() && digit(input.charAt(position))) {
                throw error(path, "leading zero in number");
            }
        } else {
            digits(path);
        }
        if (take('.')) digits(path);
        if (take('e') || take('E')) {
            if (!take('+')) take('-');
            digits(path);
        }
        return new NumberValue(input.substring(start, position));
    }

    private void digits(final String path) throws LevelFormatException {
        int start = position;
        while (position < input.length() && digit(input.charAt(position))) position++;
        if (position == start) throw error(path, "expected a digit");
    }

    private Object literal(final String literal, final Object value, final String path)
            throws LevelFormatException {
        if (!input.startsWith(literal, position)) throw error(path, "invalid JSON literal");
        position += literal.length();
        return value;
    }

    private void whitespace() {
        while (position < input.length()) {
            char character = input.charAt(position);
            if (character != ' ' && character != '\t' && character != '\r' && character != '\n') return;
            position++;
        }
    }

    private boolean take(final char character) {
        if (position < input.length() && input.charAt(position) == character) {
            position++;
            return true;
        }
        return false;
    }

    private void expect(final char character, final String path) throws LevelFormatException {
        if (!take(character)) throw error(path, "expected '" + character + "'");
    }

    private LevelFormatException error(final String path, final String message) {
        int line = 1;
        int column = 1;
        for (int index = 0; index < position; index++) {
            if (input.charAt(index) == '\n') { line++; column = 1; }
            else column++;
        }
        return new LevelFormatException(path + ": " + message + " (line " + line
                + ", column " + column + ")");
    }

    private static boolean digit(final char value) { return value >= '0' && value <= '9'; }

    /** Keep JSON numbers distinct from strings and avoid floating-point rounding. */
    static final class NumberValue {
        private final String text;

        NumberValue(final String text) { this.text = text; }

        int integer(final String path) throws LevelFormatException {
            try {
                BigDecimal value = new BigDecimal(text);
                if (value.compareTo(BigDecimal.valueOf(Integer.MIN_VALUE)) < 0
                        || value.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
                    throw new ArithmeticException("outside the signed 32-bit range");
                }
                return value.intValueExact();
            } catch (NumberFormatException failure) {
                throw new LevelFormatException(path + ": number is outside the supported range", failure);
            } catch (ArithmeticException failure) {
                throw new LevelFormatException(path + ": expected an exact signed 32-bit integer", failure);
            }
        }
    }
}
