#include <amxmodx>
#include <amxmisc>

#pragma semicolon 1

new const PLUGIN_NAME[] = "Hello AMXX";
new const PLUGIN_VERSION[] = "1.0.0";
new const PLUGIN_AUTHOR[] = "berkchy";

public plugin_init()
{
	register_plugin(PLUGIN_NAME, PLUGIN_VERSION, PLUGIN_AUTHOR);

	register_concmd("am_hello", "CmdHello", ADMIN_ADMIN, "Prints a hello message");

	new map[32];
	get_mapname(map, charsmax(map));

	server_print("[Hello] Plugin loading on map '%s'", map);
}

public CmdHello(id, level, cid)
{
	if (!cmd_access(id, level, cid, 1))
	{
		return PLUGIN_HANDLED;
	}

	client_print(id, print_chat, "[Hello] v1.0.0 is running (cellsize 64)");
	return PLUGIN_HANDLED;
}