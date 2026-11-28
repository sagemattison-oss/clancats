package com.example.clancats.client;

import com.example.clancats.ActionPayload;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

/**
 * Base for the Clan Cats menus. The server owns all state: a screen is built from a JSON snapshot,
 * sends {@link ActionPayload}s when buttons are pressed, and is rebuilt in place when the server answers.
 */
public abstract class MenuScreen extends Screen {
    protected JsonObject data;

    protected MenuScreen(Text title, JsonObject data) {
        super(title);
        this.data = data;
    }

    public abstract String menuId();

    /** The server sent fresh state for this menu: rebuild the widgets. */
    public void update(JsonObject next) {
        this.data = next;
        this.clearChildren();
        this.init();
    }

    // ---- receiving ----
    public static void receive(MinecraftClient client, String menu, String json) {
        JsonObject data;
        try {
            data = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            return;
        }
        Screen current = client.currentScreen;
        if (current instanceof MenuScreen open && open.menuId().equals(menu)) {
            open.update(data);
            return;
        }
        // A refresh only updates a menu that's already open; a pop-up never interrupts another screen.
        if (data.has("refresh") && data.get("refresh").getAsBoolean()) return;
        if (data.has("auto") && data.get("auto").getAsBoolean() && current != null) return;

        MenuScreen next = switch (menu) {
            case "hub" -> new HubScreen(data);
            case "clans" -> new ChooseClanScreen(data);
            case "claim" -> new ClaimScreen(data);
            case "upgrades" -> new UpgradeScreen(data);
            case "admin" -> new AdminScreen(data);
            default -> null;
        };
        if (next != null) client.setScreen(next);
    }

    // ---- sending ----
    protected static void send(String action, String arg, String menu) {
        ClientPlayNetworking.send(new ActionPayload(action, arg, menu));
    }

    // ---- data helpers ----
    protected String str(String key) {
        return data.has(key) ? data.get(key).getAsString() : "";
    }

    protected int num(String key) {
        return data.has(key) ? data.get(key).getAsInt() : 0;
    }

    protected boolean flag(String key) {
        return data.has(key) && data.get(key).getAsBoolean();
    }

    // ---- widgets and drawing ----
    protected static Text colored(String s, int rgb) {
        return Text.literal(s).styled(st -> st.withColor(rgb));
    }

    protected ButtonWidget button(int x, int y, int width, Text label, boolean active, String tip,
                                  ButtonWidget.PressAction action) {
        ButtonWidget.Builder b = ButtonWidget.builder(label, action).dimensions(x, y, width, 20);
        if (tip != null && !tip.isEmpty()) b.tooltip(Tooltip.of(Text.literal(tip)));
        ButtonWidget built = b.build();
        built.active = active;
        return this.addDrawableChild(built);
    }

    protected ButtonWidget button(int x, int y, int width, String label, boolean active, String tip,
                                  ButtonWidget.PressAction action) {
        return button(x, y, width, Text.literal(label), active, tip, action);
    }

    /** The server's last result message, wrapped, green for success and red for a refusal. Returns the next free y. */
    protected int drawMessage(DrawContext context, int cx, int y) {
        String msg = str("msg");
        if (msg.isEmpty()) return y;
        int color = flag("ok") ? 0x55FF55 : 0xFF5555;
        for (OrderedText line : this.textRenderer.wrapLines(Text.literal(msg), 300)) {
            context.drawCenteredTextWithShadow(this.textRenderer, line, cx, y, color);
            y += 10;
        }
        return y;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
