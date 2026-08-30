#ifndef EXTDLL_H
#define EXTDLL_H

// Classic HLSDK umbrella header for the trimmed (dlls/less) hlsdk tree
// vendored with metamod-p. Declares the handful of engine-side types that
// metamod sources rely on from extdll.h without dragging in the game DLL
// headers, which this hlsdk intentionally omits.

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>

#include "mathlib.h"	// vec_t / vec3_t

#include "const.h"		// func_t, string_t, edict_t, TraceResult, ...
#include "progdefs.h"	// globalvars_t
#include "edict.h"		// entvars_t
#include "eiface.h"		// enginefuncs_s, g_engfuncs API

#endif	// EXTDLL_H