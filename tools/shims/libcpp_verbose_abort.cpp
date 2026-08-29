#include <cstdarg>
#include <cstdio>
#include <cstdlib>
namespace std {
namespace __ndk1 {
void __libcpp_verbose_abort(char const* format, ...) {
    fputs("libc++: fatal error (__libcpp_verbose_abort)\n", stderr);
    abort();
}
}  // namespace __ndk1
}  // namespace std