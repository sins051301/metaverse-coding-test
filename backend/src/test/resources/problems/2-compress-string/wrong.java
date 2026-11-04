// 한 번만 나온 글자에도 1을 붙인다.
class Solution {
    public String solution(String s) {
        StringBuilder builder = new StringBuilder();
        int index = 0;
        while (index < s.length()) {
            int end = index;
            while (end < s.length() && s.charAt(end) == s.charAt(index)) end++;
            builder.append(s.charAt(index)).append(end - index);
            index = end;
        }
        return builder.toString();
    }
}
