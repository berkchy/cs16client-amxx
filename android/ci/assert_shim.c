/*
 * Minimal assert shim required by CS16Client's amxx build.
 * amxmodx uses __assert2/__assert_fail (glibc ABI). Android's bionic does not
 * provide them, so we wrap unresolved references onto this implementation.
 */
#include <stdio.h>
#include <stdlib.h>

#ifdef __cplusplus
extern "C" {
#endif

__attribute__((noreturn)) void __wrap___assert2(const char *file, int line,
                                                const char *func,
                                                const char *failedexpr) {
  fprintf(stderr, "assertion failed: %s:%d: %s: %s\n", file, line, func,
          failedexpr);
  abort();
}

__attribute__((noreturn)) void __wrap___assert_fail(const char *assertion,
                                                    const char *file,
                                                    unsigned int line,
                                                    const char *function) {
  fprintf(stderr, "assertion failed: %s:%d (%s): %s\n", file, line, function,
          assertion);
  abort();
}

#ifdef __cplusplus
}
#endif