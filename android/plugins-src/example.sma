#include <amxmodx>
#include <amxmodxmeta>
#include <engine>

#define PLUGIN_NAME "CS16 AMXX Example"
#define PLUGIN_VERSION "1.0.0"
#define PLUGIN_AUTHOR "CI"

public plugin_init()
{
    register_plugin(PLUGIN_NAME, PLUGIN_VERSION, PLUGIN_AUTHOR);
    register_cvar("amxx_helper_example", "1");
}

public client_putinserver(id)
{
    client_print(id, print_chat, "[AMXX] Example plugin loaded (compiled on GitHub)");
}