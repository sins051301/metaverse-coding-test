class Solution {
    public String solution(String s) {
        StringBuilder builder = new StringBuilder();
        int index = 0;
        while (index < s.length()) {
            int end = index;
            while (end < s.length() && s.charAt(end) == s.charAt(index)) end++;
            builder.append(s.charAt(index));
            if (end - index > 1) builder.append(end - index);
            index = end;
        }
        return builder.toString();
    }
}
