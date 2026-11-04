SOURCE_FILE=Solution.java
compile() {
  javac -J-Xmx${HEAP_LIMIT_MB}m -encoding UTF-8 -d build Main.java JudgeJson.java Solution.java
}
RUN_COMMAND=(java -Xmx${HEAP_LIMIT_MB}m -Xss64m -XX:+UseSerialGC -XX:-UsePerfData -Dfile.encoding=UTF-8 -cp build Main)
