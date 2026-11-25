SOURCE_FILE=Solution.java
compile() {
  javac -J-Xmx${HEAP_LIMIT_MB}m -J-XX:TieredStopAtLevel=1 -J-XX:+UseSerialGC -J-XX:AOTCache=/judge/javac.aot -encoding UTF-8 -cp /judge/wrapper/wrapper.jar -d build Solution.java
}
RUN_COMMAND=(java -Xmx${HEAP_LIMIT_MB}m -Xss64m -XX:+UseSerialGC -XX:-UsePerfData -XX:AOTCache=/judge/java.aot -Xlog:disable -Xlog:all=warning:stderr -Dfile.encoding=UTF-8 -cp /judge/wrapper/wrapper.jar Main)
