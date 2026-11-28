package com.example.clancats.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** The camp upgrade tree. Opens when you right-click your clan's fresh-kill barrel, or from the hub. */
public class UpgradeScreen extends MenuScreen {
    private static final int TOP = 62;
    private static final int ROW = 36;

    public UpgradeScreen(JsonObject data) {
        super(Text.literal("Camp upgrades"), data);
    }

    @Override
    public String menuId() {
        return "upgrades";
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        if (!flag("available")) {
            button(cx - 50, this.height / 2 + 10, 100, "Close", true, null, b -> this.close());
            return;
        }
        boolean canSpend = flag("canSpend");
        JsonArray ups = data.getAsJsonArray("upgrades");
        for (int i = 0; i < ups.size(); i++) {
            JsonObject u = ups.get(i).getAsJsonObject();
            String id = u.get("id").getAsString();
            String state = u.get("state").getAsString();
            String label = switch (state) {
                case "owned" -> "[Owned] ";
                case "locked" -> "[Locked] ";
                case "ready" -> "[Buy] ";
                default -> "[Need more] ";
            };
            String tip = u.get("blurb").getAsString();
            if (!state.equals("owned")) {
                tip += "\nCost: " + u.get("cost").getAsString();
                String needs = u.get("needs").getAsString();
                if (!needs.isEmpty()) tip += "\nNeeds: " + needs;
            }
            if (state.equals("ready") && !canSpend) tip += "\nOnly a Warrior or higher can spend the camp's stores.";
            int x = i % 2 == 0 ? cx - 155 : cx + 5;
            int y = TOP + (i / 2) * ROW;
            button(x, y, 150, label + u.get("title").getAsString(), state.equals("ready") && canSpend, tip,
                    b -> send("upgrade", id, "upgrades"));
        }
        int y = TOP + ((ups.size() + 1) / 2) * ROW + 4;
        button(cx - 155, y, 150, "Open fresh-kill barrel", flag("nearBarrel"),
                flag("nearBarrel") ? "Stock it with prey, wool, wheat or hay, and herbs. (Sneak + right-click does this too.)"
                        : "Stand next to the barrel to open it.", b -> send("openbarrel", "", "upgrades"));
        button(cx + 5, y, 150, "Back to hub", true, null, b -> send("open", "hub", ""));
        button(cx - 50, y + 24, 100, "Close", true, null, b -> this.close());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int cx = this.width / 2;
        if (!flag("available")) {
            context.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, this.height / 2 - 30, 0xFFAA00);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(str("reason")), cx, this.height / 2 - 10, 0xFF5555);
            return;
        }
        context.drawCenteredTextWithShadow(this.textRenderer,
                colored(str("clanName") + " camp upgrades", num("clanColor")), cx, 10, 0xFFFFFF);

        // Barrel stock.
        if (flag("stockKnown")) {
            StringBuilder sb = new StringBuilder("Barrel: ");
            JsonArray stock = data.getAsJsonArray("stock");
            for (int i = 0; i < stock.size(); i++) {
                JsonObject s = stock.get(i).getAsJsonObject();
                if (i > 0) sb.append("  |  ");
                sb.append(s.get("amount").getAsInt()).append(' ').append(s.get("label").getAsString());
            }
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(sb.toString()), cx, 24, 0xDDDDDD);
        } else {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("Can't see the barrel from here. Stand closer to camp."), cx, 24, 0xFFAA55);
        }
        int below = drawMessage(context, cx, 38);
        if (str("msg").isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("Hover an upgrade for details. Costs come out of the barrel."), cx, 38, 0x888888);
        }

        // Cost line under each upgrade.
        JsonArray ups = data.getAsJsonArray("upgrades");
        for (int i = 0; i < ups.size(); i++) {
            JsonObject u = ups.get(i).getAsJsonObject();
            String state = u.get("state").getAsString();
            int x = i % 2 == 0 ? cx - 155 : cx + 5;
            int y = TOP + (i / 2) * ROW + 22;
            String line;
            int color;
            switch (state) {
                case "owned" -> { line = "Built"; color = 0x55FF55; }
                case "locked" -> { line = "Needs " + u.get("needs").getAsString(); color = 0x888888; }
                case "ready" -> { line = "Cost: " + u.get("cost").getAsString(); color = 0x55FF55; }
                default -> { line = "Cost: " + u.get("cost").getAsString(); color = 0xFF8855; }
            }
            context.drawTextWithShadow(this.textRenderer, Text.literal(line), x + 2, y, color);
        }
    }
}
