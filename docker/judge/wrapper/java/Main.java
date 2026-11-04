import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * 채점용 진입점. stdin의 인자 JSON 배열을 Solution.solution 의 파라미터 타입에 맞게 바꿔 호출하고,
 * {"returnValue":...,"userOutput":"..."} 한 줄을 stdout에 쓴다. 유저의 System.out 출력은 userOutput으로 모은다.
 */
public class Main {

    private static final int USER_OUTPUT_LIMIT_BYTES = 65536;

    public static void main(String[] arguments) throws Throwable {
        List<?> jsonArguments = (List<?>) JudgeJson.parse(new String(System.in.readAllBytes(), StandardCharsets.UTF_8));
        Class<?> solutionClass = Class.forName("Solution");
        Method solutionMethod = findSolutionMethod(solutionClass);
        Class<?>[] parameterTypes = solutionMethod.getParameterTypes();
        Object[] invokeArguments = new Object[parameterTypes.length];
        for (int index = 0; index < parameterTypes.length; index++) {
            invokeArguments[index] = convert(jsonArguments.get(index), parameterTypes[index]);
        }
        Constructor<?> constructor = solutionClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object solution = constructor.newInstance();

        PrintStream originalOut = System.out;
        LimitedOutputStream userOutput = new LimitedOutputStream();
        System.setOut(new PrintStream(userOutput, true, StandardCharsets.UTF_8));
        Object returnValue;
        try {
            returnValue = solutionMethod.invoke(solution, invokeArguments);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        } finally {
            System.out.flush();
            System.setOut(originalOut);
        }

        String result = "{\"returnValue\":" + JudgeJson.toJson(returnValue)
                + ",\"userOutput\":" + JudgeJson.quote(userOutput.toString(StandardCharsets.UTF_8)) + "}";
        originalOut.write(result.getBytes(StandardCharsets.UTF_8));
        originalOut.flush();
    }

    private static Method findSolutionMethod(Class<?> solutionClass) {
        List<Method> candidates = Arrays.stream(solutionClass.getDeclaredMethods())
                .filter(method -> method.getName().equals("solution"))
                .toList();
        if (candidates.size() != 1) {
            throw new IllegalStateException("Solution 클래스에 solution 메서드가 정확히 하나 있어야 합니다. 현재 " + candidates.size() + "개");
        }
        Method method = candidates.getFirst();
        method.setAccessible(true);
        return method;
    }

    private static Object convert(Object json, Class<?> type) {
        if (type == int.class) {
            return Math.toIntExact((Long) json);
        }
        if (type == long.class) {
            return (Long) json;
        }
        if (type == double.class) {
            return ((Number) json).doubleValue();
        }
        if (type == boolean.class) {
            return (Boolean) json;
        }
        if (type == String.class) {
            return (String) json;
        }
        if (type.isArray()) {
            List<?> elements = (List<?>) json;
            Object array = Array.newInstance(type.getComponentType(), elements.size());
            for (int index = 0; index < elements.size(); index++) {
                Array.set(array, index, convert(elements.get(index), type.getComponentType()));
            }
            return array;
        }
        throw new IllegalArgumentException("지원하지 않는 파라미터 타입입니다: " + type.getTypeName());
    }

    private static class LimitedOutputStream extends ByteArrayOutputStream {

        @Override
        public synchronized void write(int value) {
            if (count < USER_OUTPUT_LIMIT_BYTES) {
                super.write(value);
            }
        }

        @Override
        public synchronized void write(byte[] bytes, int offset, int length) {
            super.write(bytes, offset, Math.min(length, Math.max(0, USER_OUTPUT_LIMIT_BYTES - count)));
        }

    }

}
