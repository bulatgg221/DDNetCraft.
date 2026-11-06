# AI Builder 0.3.0 — Minecraft Fabric 1.21.1

A prompt-to-blocks mod for **singleplayer Creative Mode**.
Java 21 and Fabric API required. Back up your world before building.

## Setup in LabyMod or vanilla Fabric
1. Delete all older aibuilder .jar files from the active profile's mods directory.
2. Install aibuilder-fabric-0.3.0.jar and restart the game.
3. Open a singleplayer world and type **`/ai key`** (this is a normal
   Fabric server command, unlike the older /aikey client command). You can also
   press **F8** or try **`/ai setup`**.
4. Select **Polza AI** or **OpenAI**, and choose your model.
5. For Polza, the tested catalog ID is **`openai/gpt-4.1-mini`**.
   Polza chat endpoint: `https://polza.ai/api/v1/chat/completions`.
   The Polza API key is normally prefixed `pza_`. The model uses API balance
   on Polza, not your ChatGPT subscription.
6. Paste the key in the **masked GUI field**, NOT in Minecraft chat, and click
   **Сохранить ключ и настройки**.
7. Run **`/ai build построй деревянный дом`** in Creative Mode.

## Commands
- `/ai key`, `/ai setup`: open provider/model/key screen in local singleplayer
- `/aikey`: optional client-side alias; LabyMod may intercept it
- **F8**: open API settings regardless of client chat command handling
- `/ai build <prompt>`: generate and place Minecraft blocks from a text prompt
- `/ai demo`: offline medieval spawn test, no API key required
- `/ai status`: progress
- `/ai cancel`: stop and roll back work in progress
- `/ai undo`: undo last completed construction
- `/ai reload`: reload advanced config values
- `/ai help`: command list

## Secret separation and migration
The game creates `config/aibuilder/config.json`, relative to the **active game
profile**. The GUI resolves this path automatically, including LabyMod profiles.

- OpenAI key: `config/aibuilder/api-key.txt`
- Polza AI key: `config/aibuilder/polza-key.txt`
- Keys are never put into source code, Minecraft chat arguments, or GitHub.
- The GUI stores each provider's key separately to prevent sending an OpenAI
  secret to Polza or vice versa.
- Existing 0.2.x configurations without a `provider` setting stay on OpenAI
  until the provider is changed in the new GUI.
- `/ai key` requires an **integrated singleplayer server**, not dedicated server.
  If the new GUI is not loading under LabyMod, test a clean Fabric 1.21.1 profile.

The selected provider's API endpoint is fixed in source (allowlisted), not entered
as an arbitrary URL. Full world backups are strongly recommended. Use only
API keys with appropriate usage limits.

## Build source
GitHub Actions → Build AI Builder Fabric → latest successful run → Artifacts.
