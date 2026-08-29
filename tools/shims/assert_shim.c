#include <stdio.h>

#define ASSERT_LOG "/storage/emulated/0/xash/assert.txt"

extern "C" {

void __wrap___assert2(const char *file, int line, const char *function, const char *msg)
{
	FILE *f = fopen(ASSERT_LOG, "a");
	if (f)
	{
		fprintf(f, "ASSERT FAILED: %s (%s:%d in %s)\n", msg ? msg : "", file, line, function ? function : "");
		fclose(f);
	}
}

void __wrap___assert_fail(const char *expr, const char *file, int line, const char *function)
{
	FILE *f = fopen(ASSERT_LOG, "a");
	if (f)
	{
		fprintf(f, "ASSERT FAILED: %s (%s:%d in %s)\n", expr ? expr : "", file, line, function ? function : "");
		fclose(f);
	}
}

}