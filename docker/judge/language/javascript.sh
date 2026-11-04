SOURCE_FILE=solution.js
compile() {
  node --check solution.js
}
RUN_COMMAND=(node --max-old-space-size=${HEAP_LIMIT_MB} --stack-size=65500 main.js)
