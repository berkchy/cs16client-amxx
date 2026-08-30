# Plugins source

Drop your `.sma` files here. On every `v*` tag (or manual workflow run), GitHub
Actions compiles them with the project's pawncc and packages the resulting
`.amxx` files into `amxx-plugins.zip` (attached to the release).

On the device, copy the `.amxx` files into:

    /storage/emulated/0/xash/cstrike/addons/amxmodx/plugins/

and add a corresponding `plugins.ini` line if you want them auto-loaded.

Optional: `include/` next to your `.sma` files will be added to the include path.