#ifndef UTIL_H
#define UTIL_H

// Minimal classic HL SDK dlls/util.h shim for metamod-p. Supplies only the
// classic SDK utility bits the metamod loader sources rely on: BOOL,
// hudtextparms_t, FStrEq, the engine message write plumbing (MESSAGE_* /
// WRITE_*) and the classic SVC_* protocol identifiers. This hlsdk tree
// intentionally omits the gamedll CBaseEntity world (see hlsdk_readme.txt).

#include <extdll.h>

typedef int BOOL;

// Classic SDK SVC_* protocol identifiers (historically issued from dlls/util.h).
#define SVC_TEMPENTITY		23
#define SVC_INTERMISSION	30
#define SVC_CDTRACK			32
#define SVC_WEAPONANIM		35
#define SVC_ROOMTYPE		37
#define	SVC_DIRECTOR		51

inline BOOL FStrEq(const char *sz1, const char *sz2) {
	return (strcmp(sz1, sz2) == 0);
}

typedef struct hudtextparms_s
{
	float		x;
	float		y;
	int			effect;
	byte		r1, g1, b1, a1;
	byte		r2, g2, b2, a2;
	float		fadeinTime;
	float		fadeoutTime;
	float		holdTime;
	float		fxTime;
	int			channel;
} hudtextparms_t;

// Engine message send/write plumbing (classic dlls/util.h).
#define MESSAGE_BEGIN( msg_dest, msg_type, pOrigin, pEntity ) ( *g_engfuncs.pfnMessageBegin )( (msg_dest), (msg_type), (pOrigin), (pEntity) )
#define MESSAGE_END() ( *g_engfuncs.pfnMessageEnd )()
#define WRITE_BYTE( value ) ( *g_engfuncs.pfnWriteByte )( (value) )
#define WRITE_CHAR( value ) ( *g_engfuncs.pfnWriteChar )( (value) )
#define WRITE_SHORT( value ) ( *g_engfuncs.pfnWriteShort )( (value) )
#define WRITE_LONG( value ) ( *g_engfuncs.pfnWriteLong )( (value) )
#define WRITE_ANGLE( value ) ( *g_engfuncs.pfnWriteAngle )( (value) )
#define WRITE_COORD( value ) ( *g_engfuncs.pfnWriteCoord )( (value) )
#define WRITE_STRING( value ) ( *g_engfuncs.pfnWriteString )( (value) )


// Classic SDK util.h support bits.
#define TRUE	1
#define FALSE	0

// STRING() via the engine string table (Xash SDK: pfnSzFromIndex).
#define STRING(iString) ( *g_engfuncs.pfnSzFromIndex )( (iString) )

inline edict_t *INDEXENT(int iEdictNum) {
	return (*g_engfuncs.pfnPEntityOfEntIndex)(iEdictNum);
}

#endif // UTIL_H
