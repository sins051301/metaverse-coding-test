const solution = (s) => s.replace(/(.)\1*/g, (run) => run[0] + (run.length > 1 ? run.length : ''));
