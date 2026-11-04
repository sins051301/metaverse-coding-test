import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * 채점 입출력에 필요한 만큼만 다루는 JSON 파서/직렬화기. 유저 코드와 같은 클래스패스에 올라가므로 외부 라이브러리를 쓰지 않는다.
 * 파싱 결과: 배열은 List, 정수는 Long, 실수는 Double, 문자열은 String, true/false는 Boolean, null은 null.
 */
final class JudgeJson {

    private final String text;
    private int position;

    private JudgeJson(String text) {
        this.text = text;
    }

    static Object parse(String text) {
        JudgeJson parser = new JudgeJson(text);
        parser.skipWhitespace();
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (parser.position != text.length()) {
            throw parser.error();
        }
        return value;
    }

    static String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String string) {
            return quote(string);
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value.getClass().isArray()) {
            StringJoiner joiner = new StringJoiner(",", "[", "]");
            for (int index = 0; index < Array.getLength(value); index++) {
                joiner.add(toJson(Array.get(value, index)));
            }
            return joiner.toString();
        }
        throw new IllegalArgumentException("지원하지 않는 반환 타입입니다: " + value.getClass().getTypeName());
    }

    static String quote(String value) {
        StringBuilder builder = new StringBuilder("\"");
        for (char character : value.toCharArray()) {
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format("\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        return builder.append('"').toString();
    }

    private Object readValue() {
        if (position >= text.length()) {
            throw error();
        }
        return switch (text.charAt(position)) {
            case '[' -> readArray();
            case '"' -> readString();
            case 't' -> readLiteral("true", Boolean.TRUE);
            case 'f' -> readLiteral("false", Boolean.FALSE);
            case 'n' -> readLiteral("null", null);
            default -> readNumber();
        };
    }

    private List<Object> readArray() {
        List<Object> elements = new ArrayList<>();
        position++;
        skipWhitespace();
        if (peek() == ']') {
            position++;
            return elements;
        }
        while (true) {
            skipWhitespace();
            elements.add(readValue());
            skipWhitespace();
            char separator = next();
            if (separator == ']') {
                return elements;
            }
            if (separator != ',') {
                throw error();
            }
        }
    }

    private String readString() {
        StringBuilder builder = new StringBuilder();
        position++;
        while (true) {
            char character = next();
            if (character == '"') {
                return builder.toString();
            }
            if (character != '\\') {
                builder.append(character);
                continue;
            }
            char escaped = next();
            switch (escaped) {
                case '"', '\\', '/' -> builder.append(escaped);
                case 'b' -> builder.append('\b');
                case 'f' -> builder.append('\f');
                case 'n' -> builder.append('\n');
                case 'r' -> builder.append('\r');
                case 't' -> builder.append('\t');
                case 'u' -> {
                    builder.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
                    position += 4;
                }
                default -> throw error();
            }
        }
    }

    private Object readLiteral(String literal, Object value) {
        if (!text.startsWith(literal, position)) {
            throw error();
        }
        position += literal.length();
        return value;
    }

    private Object readNumber() {
        int start = position;
        while (position < text.length() && "+-0123456789.eE".indexOf(text.charAt(position)) >= 0) {
            position++;
        }
        String number = text.substring(start, position);
        if (number.isEmpty()) {
            throw error();
        }
        boolean isDecimal = number.contains(".") || number.contains("e") || number.contains("E");
        return isDecimal ? (Object) Double.parseDouble(number) : (Object) Long.parseLong(number);
    }

    private void skipWhitespace() {
        while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
            position++;
        }
    }

    private char peek() {
        if (position >= text.length()) {
            throw error();
        }
        return text.charAt(position);
    }

    private char next() {
        char character = peek();
        position++;
        return character;
    }

    private IllegalArgumentException error() {
        return new IllegalArgumentException("잘못된 입력 JSON입니다 (위치 " + position + ")");
    }

}
