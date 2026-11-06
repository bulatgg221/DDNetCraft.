# AI Builder Fabric 1.21.1
A separate prompt-to-blocks Minecraft mod: /ai build <prompt>, /ai demo, /ai undo, /ai cancel, /ai status.
Java 21, Fabric Loader + Fabric API. Requires separate OpenAI API credits.
Use config/aibuilder/api-key.txt locally (never commit or share the key).
Run in a singleplayer Creative Mode world and back up the world before testing.

## API key setup from Minecraft chat

1. Start **singleplayer** Fabric Minecraft 1.21.1.
2. Type **`/aikey`** in the Minecraft chat.
3. Paste your own OpenAI API key into the **masked local GUI field** (Ctrl+V).
4. Click **Сохранить ключ** (Save key). The file is stored in the current game's
   Fabric config directory: `config/aibuilder/api-key.txt`.
5. Type `/ai build small cozy oak cabin with a balcony`.

The secret is NEVER typed as a chat command, passed to the server chat, put into
the command history, or included in source code. You can also delete the local key
from that screen. The external OpenAI API requires its own paid billing plan.

In LabyMod, `/aikey` may be intercepted by its chat handler. **F8** opens the
same window with no chat command. The hotkey is configurable under Minecraft Controls.
The client defers the screen opening until after the chat UI closes.
