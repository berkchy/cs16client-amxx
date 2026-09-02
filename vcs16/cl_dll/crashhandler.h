#ifndef CRASHHANDLER_H
#define CRASHHANDLER_H

#ifdef __ANDROID__
void CrashHandler_Init(void);
void CrashHandler_SetGameDir(const char *gamedir);
#else
static inline void CrashHandler_Init(void) {}
static inline void CrashHandler_SetGameDir(const char *gamedir) {}
#endif

#endif // CRASHHANDLER_H
