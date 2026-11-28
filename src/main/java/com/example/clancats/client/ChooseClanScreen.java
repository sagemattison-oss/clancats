package com.example.clancats.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Join a clan, or stay (or become) a loner. Shown when you become a cat without a clan, and from the hub. */
public class ChooseClanScreen extends MenuScreen {
    public ChooseClanScreen(JsonObject data) {
        super(Text.literal(data.has("clan") && !data.get("clan").getAsString().isEmpty()
                ? "Change clan" : "Choose your clan"), data);
    }

    @Override
    public String menuId() {
        return "clans";
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        String current = str("clan");
        JsonArray clans = data.getAsJsonArray("clans");
        for (int i = 0; i < clans.size(); i++) {
            JsonObject c = clans.get(i).getAsJsonObject();
            String id = c.get("id").getAsString();
            String name = c.get("name").getAsString();
            int color = c.get("color").getAsInt();
            boolean mine = id.equals(current);
            int x = i % 2 == 0 ? cx - 155 : cx + 5;
            int y = 66 + (i / 2) * 46;
            button(x, y, 150, colored((mine ? "> " : "Join ") + name, color), !mine,
                    mine ? "You are in this clan." : null, b -> {
                        send("join", id, "");
                        this.close();
                    });
        }
        int y = 66 + 2 * 46 + 4;
        if (current.isEmpty()) {
            button(cx - 100, y, 200, "Remain a loner", true, "Walk alone. You can join a clan any time from the hub (J).",
                    b -> this.close());
        } else {
            button(cx - 100, y, 200, "Leave my clan (become a loner)", true, null, b -> {
                send("leave", "", "");
                this.close();
            });
        }
        button(cx - 50, y + 24, 100, "Cancel", true, null, b -> this.close());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int cx = this.width / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, 14, 0xFFAA00);
        String sub = str("clan").isEmpty()
                ? "Pick a clan to hunt and fight beside, or walk alone as a loner."
                : "Joining another clan means leaving your current one.";
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(sub), cx, 34, 0xAAAAAA);

        JsonArray clans = data.getAsJsonArray("clans");
        for (int i = 0; i < clans.size(); i++) {
            JsonObject c = clans.get(i).getAsJsonObject();
            int x = i % 2 == 0 ? cx - 155 : cx + 5;
            int y = 66 + (i / 2) * 46;
            String stats = c.get("members").getAsInt() + " online  |  " + c.get("camps").getAsInt() + " camp(s)";
            context.drawTextWithShadow(this.textRenderer, Text.literal(stats), x + 2, y + 24, 0x999999);
        }
    }
}
