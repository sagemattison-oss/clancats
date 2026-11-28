package com.example.clancats.client;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Pops up when a Warrior-or-higher cat walks into an unclaimed wild camp. */
public class ClaimScreen extends MenuScreen {
    public ClaimScreen(JsonObject data) {
        super(Text.literal("Unclaimed wild camp"), data);
    }

    @Override
    public String menuId() {
        return "claim";
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = this.height / 2 + 10;
        if (flag("hasClan")) {
            button(cx - 100, y, 200, colored("Claim for " + str("clanName"), num("clanColor")), true, null, b -> {
                send("claim", "", "");
                this.close();
            });
        } else {
            button(cx - 100, y, 200, "Choose a clan first", true,
                    "A camp can only be claimed for a clan.", b -> send("open", "clans", ""));
        }
        button(cx - 100, y + 24, 200, "Not now", true, null, b -> this.close());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int cx = this.width / 2;
        int y = this.height / 2 - 54;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, y, 0xFFAA00);
        String kind = num("kind") == 1 ? "surface" : "cave";
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("A " + kind + " camp lies empty. Its Great Rock belongs to no clan."), cx, y + 20, 0xDDDDDD);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Claim it and it becomes your clan's home: stock it, upgrade it, raise kits."), cx, y + 32, 0xAAAAAA);
    }
}
