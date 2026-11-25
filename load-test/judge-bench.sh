#!/bin/bash
# 사용: load-test/judge-bench.sh <채점 이미지> [실행기마다 반복 수=20] [동시 실행 수=1]
set -uo pipefail

IMAGE=$1
RUNS=${2:-20}
PARALLEL=${3:-1}
MEMORY_LIMIT_MB=265
EXPECTED=(2 3 2 0 1 0 2 49 24 0)

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/in/tests" "$WORK/results"
cat > "$WORK/in/Solution.java" <<'EOF'
class Solution { public int solution(int[] numbers) { int count = 0; for (int number : numbers) if (number % 2 == 0) count++; return count; } }
EOF
inputs=(
  '[[1,2,3,4,5]]'
  '[[2,4,6]]'
  '[[-3,-2,0,7]]'
  '[[1]]'
  '[[0]]'
  '[[-1,-3,-5]]'
  '[[-4,-2,1,3]]'
  '[[-817,-706,-668,-667,-603,269,260,671,-727,-889,82,-480,84,514,394,439,41,-766,-635,-73,557,-613,-335,318,26,-634,-360,-343,-69,519,718,-305,-72,796,-581,-503,82,-760,-940,-849,-363,-554,70,-12,-536,-768,-185,-254,-918,-826,-7,-736,710,605,-600,203,-420,-463,-906,182,-778,-306,-569,-109,548,767,-527,539,-20,-965,998,327,914,647,908,213,663,12,-661,500,637,-919,713,679,-725,782,34,-780,442,-172,-777,411,73,-788,237,-283,912,325,-743,-639]]'
  '[[338,-325,813,-883,687,-884,-559,-639,909,50,-127,-893,991,-273,-488,602,796,209,798,-541,293,449,505,-341,160,761,875,-85,462,12,186,-279,-151,630,-497,-19,-857,710,378,328,99,751,-805,828,-751,527,482,97,214,-385,690,-196,402,991,-882,930,-340]]'
  '[[1,1,1,1,999,-999,1,1,-1,-999,1,1,1,-1,-1,1,-1,1,999,999,-999,-1,-999,999,-1,999,-1,1,-999,999,-1,999,-999,999,-1,999,999,-999,999,999,-1,-999,-999,999,-999,1,-1,999,999,999,-999,1,-999,-1,999,1,1,999,-999,-999,-999,-999,999,-1,-999,1,-1,1,1,1,-999,-999,-999,-1,999,999,-999,-999,999,1,1,999,-1,1,-1,999,1,-999,999,1,-1,1,999,999,-1,999,999,-999,1,1]]'
)
for index in "${!inputs[@]}"; do
  printf '%s' "${inputs[$index]}" > "$WORK/in/tests/$((index + 1)).in"
done
chmod -R a+rX "$WORK/in"
INPUT_PATH=$(cygpath -m "$WORK/in" 2>/dev/null || echo "$WORK/in")

SANDBOX_OPTIONS=(
  --network none --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=32m,mode=1777
  --memory "${MEMORY_LIMIT_MB}m" --memory-swap "${MEMORY_LIMIT_MB}m" --cpus 1 --pids-limit 128 --ulimit core=0
  --cap-drop ALL --cap-add SETUID --cap-add SETGID --cap-add KILL --security-opt no-new-privileges
  -v "$INPUT_PATH:/judge/in:ro"
)

now_ms() {
  echo $(( $(date +%s%N) / 1000000 ))
}

measure_full() {
  local result_dir=$1
  mkdir -p "$result_dir"
  local start end
  start=$(now_ms)
  MSYS_NO_PATHCONV=1 docker run --rm "${SANDBOX_OPTIONS[@]}" \
    -e COMPILE_TIMEOUT=10 -e RUN_TIMEOUT=3 -e MEMORY_LIMIT_MB=$MEMORY_LIMIT_MB "$IMAGE" > "$result_dir/result.tar"
  end=$(now_ms)
  tar -xf "$result_dir/result.tar" -C "$result_dir"
  local correct=true test_seconds=0 max_memory_kb=0
  for index in "${!EXPECTED[@]}"; do
    local test_name=$((index + 1))
    grep -q "\"returnValue\":${EXPECTED[$index]}," "$result_dir/$test_name.out" 2>/dev/null || correct=false
    local seconds memory_kb
    seconds=$(sed -n 's/^time_seconds=//p' "$result_dir/$test_name.meta")
    memory_kb=$(sed -n 's/^memory_kb=//p' "$result_dir/$test_name.meta")
    test_seconds=$(awk -v sum="$test_seconds" -v value="$seconds" 'BEGIN { print sum + value }')
    [ "$memory_kb" -gt "$max_memory_kb" ] && max_memory_kb=$memory_kb
  done
  echo "$((end - start)) $(awk -v value="$test_seconds" 'BEGIN { print value * 1000 }') $max_memory_kb $correct"
  rm -rf "$result_dir"
}

measure_compile() {
  MSYS_NO_PATHCONV=1 docker run --rm "${SANDBOX_OPTIONS[@]}" -e HEAP_LIMIT_MB=$((MEMORY_LIMIT_MB - 55)) \
    --entrypoint bash "$IMAGE" -c '
      SANDBOX=(setpriv --reuid=sandbox --regid=sandbox --clear-groups --inh-caps=-all)
      "${SANDBOX[@]}" mkdir -p /tmp/work
      "${SANDBOX[@]}" cp /judge/in/Solution.java /judge/wrapper/* /tmp/work/
      cd /tmp/work
      start=$(date +%s%N)
      "${SANDBOX[@]}" bash -c "source /judge/language.sh && compile" > /dev/null 2>&1
      exit_code=$?
      end=$(date +%s%N)
      echo "$(( (end - start) / 1000000 )) $exit_code"'
}

worker() {
  local worker_index=$1
  for run in $(seq "$RUNS"); do
    echo "full $(measure_full "$WORK/results/$worker_index-$run")"
    echo "compile $(measure_compile)"
  done
}

for worker_index in $(seq "$PARALLEL"); do
  worker "$worker_index" > "$WORK/results/worker-$worker_index.log" &
done
wait
cat "$WORK"/results/worker-*.log > "$WORK/all.log"

summarize() {
  local label=$1 values
  values=$(cat)
  echo "$values" | sort -n | awk -v label="$label" '{ values[NR] = $1 } END {
    p95 = int(NR * 0.95 + 0.999999); if (p95 > NR) p95 = NR
    median = (NR % 2) ? values[(NR + 1) / 2] : (values[NR / 2] + values[NR / 2 + 1]) / 2
    printf "%-24s n=%-3d 중간값 %7.0f  p95 %7.0f  최소 %7.0f  최대 %7.0f\n", label, NR, median, values[p95], values[1], values[NR]
  }'
}

echo "이미지 $IMAGE, 실행기 $PARALLEL개 × $RUNS회"
awk '$1 == "full" { print $2 }' "$WORK/all.log" | summarize "컨테이너 전체 (ms)"
awk '$1 == "compile" { print $2 }' "$WORK/all.log" | summarize "javac (ms)"
awk '$1 == "full" { print $3 / 10 }' "$WORK/all.log" | summarize "테스트 1개 java (ms)"
awk '$1 == "full" { print $4 }' "$WORK/all.log" | summarize "테스트 최대 메모리 (KB)"
echo "정답 아닌 실행: $(awk '$1 == "full" && $5 != "true"' "$WORK/all.log" | wc -l)건, javac 실패: $(awk '$1 == "compile" && $3 != 0' "$WORK/all.log" | wc -l)건"
