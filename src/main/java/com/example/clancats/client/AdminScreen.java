package com.example.clancats.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Operator tools: set a cat's prey (the /cat setprey command). */
public class AdminScreen extends MenuScreen {
    private int index = 0;
    private String amountText = "10";
    private TextFieldWidget amountField;

    public AdminScreen(JsonObject data) {
        super(Text.literal("Operator tools"), data);
    }

    @Override
    public String menuId() {
        return "admin";
    }

    @Override
    public void update(JsonObject next) {
        if (amountField != null) amountText = amountField.getText();
        super.update(next);
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        JsonArray players = data.getAsJsonArray("players");
        if (players.size() == 0) {
            button(cx - 50, this.height / 2 + 10, 100, "Back", true, null, b -> send("open", "hub", ""));
            return;
        }
        index = Math.min(index, players.size() - 1);
        String name = players.get(index).getAsString();

        amountField = new TextFieldWidget(this.textRenderer, cx - 100, 96, 200, 20, Text.literal("Prey"));
        amountField.setMaxLength(6);
        amountField.setTextPredicate(s -> s.matches("\\d{0,6}"));
        amountField.setText(amountText);
        this.addDrawableChild(amountField);

        button(cx - 100, 60, 200, "Target: " + name, players.size() > 1, "Click to switch cat.", b -> {
            amountText = amountField.getText();
            index = (index + 1) % players.size();
            this.clearChildren();
            this.init();
        });
        button(cx - 100, 124, 200, "Set prey", true,
                "Sets prey (and so rank) for this cat. Same as /cat setprey.", b -> {
                    amountText = amountField.getText();
                    send("setprey", name + ";" + (amountText.isEmpty() ? "0" : amountText), "admin");
                });
        button(cx - 100, 150, 98, "Back to hub", true, null, b -> send("open", "hub", ""));
        button(cx + 2, 150, 98, "Close", true, null, b -> this.close());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int cx = this.width / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, 20, 0xFFAA00);
        if (data.getAsJsonArray("players").size() == 0) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("No cats are online."), cx, this.height / 2 - 10, 0xAAAAAA);
            return;
        }
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Prey amount"), cx, 84, 0xAAAAAA);
        drawMessage(context, cx, 178);
    }
}
