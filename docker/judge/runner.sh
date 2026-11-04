#!/bin/bash
# 컨테이너는 root로 시작하고, 유저 코드만 sandbox(uid 1001)로 내려서 실행한다.
# 결과는 root 전용 디렉터리(/tmp/result, 700)에 모았다가 마지막에 tar로 stdout에 내보낸다.
# 호스트 폴더를 쓰기 가능하게 연결하면 Docker Desktop이 권한을 무시해 유저 코드가 결과를 조작할 수 있기 때문이다.
set -u

INPUT_DIR=/judge/in
RESULT_DIR=/tmp/result
WORK_DIR=/tmp/work
COMPILE_TIMEOUT=${COMPILE_TIMEOUT:-10}
RUN_TIMEOUT=${RUN_TIMEOUT:-3}
STDERR_LIMIT_BYTES=65536
export MEMORY_LIMIT_MB=${MEMORY_LIMIT_MB:-265}
# 런타임(JVM/V8) 자체가 힙 밖에서 쓰는 메모리. 힙을 컨테이너 한도보다 이만큼 낮춰야 커널 강제 종료 대신 언어 차원의 메모리 에러로 끝난다.
export HEAP_LIMIT_MB=$((MEMORY_LIMIT_MB - 55))

source /judge/language.sh

# 원래 stdout(fd 3)은 결과 tar 전송에만 쓰고, 그 외 출력은 버린다. 유저 코드에는 fd 3을 넘기지 않는다.
exec 3>&1 1>/dev/null

oom_kill_count() {
  awk '$1 == "oom_kill" { print $2 }' /sys/fs/cgroup/memory.events 2>/dev/null || echo 0
}

finish() {
  tar -C "$RESULT_DIR" -cf - . >&3
  exit 0
}

SANDBOX=(setpriv --reuid=sandbox --regid=sandbox --clear-groups --inh-caps=-all)

mkdir -m 700 "$RESULT_DIR"
"${SANDBOX[@]}" mkdir -p "$WORK_DIR" 3>&-
"${SANDBOX[@]}" cp "$INPUT_DIR/$SOURCE_FILE" "$WORK_DIR/$SOURCE_FILE" 3>&-
"${SANDBOX[@]}" cp /judge/wrapper/* "$WORK_DIR/" 3>&-

cd "$WORK_DIR"
timeout -s KILL "${COMPILE_TIMEOUT}s" "${SANDBOX[@]}" bash -c 'source /judge/language.sh && compile' \
  > "$RESULT_DIR/compile.full" 2>&1 3>&-
echo "$?" > "$RESULT_DIR/compile.exit"
head -c "$STDERR_LIMIT_BYTES" "$RESULT_DIR/compile.full" > "$RESULT_DIR/compile.log"
rm -f "$RESULT_DIR/compile.full"
if [ "$(cat "$RESULT_DIR/compile.exit")" -ne 0 ]; then
  finish
fi

for input_file in $(ls "$INPUT_DIR"/tests/*.in 2>/dev/null | sort -V); do
  test_name=$(basename "$input_file" .in)

  oom_before=$(oom_kill_count)
  /usr/bin/time -f "%e %M" -o "$RESULT_DIR/$test_name.time" \
    timeout -s KILL "${RUN_TIMEOUT}s" \
    "${SANDBOX[@]}" "${RUN_COMMAND[@]}" \
    < "$input_file" > "$RESULT_DIR/$test_name.out" 2> "$RESULT_DIR/$test_name.err.full" 3>&-
  exit_code=$?
  pkill -KILL -u sandbox 2>/dev/null
  oom_after=$(oom_kill_count)

  read -r elapsed_seconds max_rss_kb < <(tail -n 1 "$RESULT_DIR/$test_name.time")
  head -c "$STDERR_LIMIT_BYTES" "$RESULT_DIR/$test_name.err.full" > "$RESULT_DIR/$test_name.err"
  rm -f "$RESULT_DIR/$test_name.err.full" "$RESULT_DIR/$test_name.time"
  oom_killed=$([ "$oom_after" -gt "$oom_before" ] && echo true || echo false)
  {
    echo "exit=$exit_code"
    echo "time_seconds=$elapsed_seconds"
    echo "memory_kb=$max_rss_kb"
    echo "oom_killed=$oom_killed"
  } > "$RESULT_DIR/$test_name.meta"
done

finish
