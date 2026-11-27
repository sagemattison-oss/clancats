package com.example.clancats.client;

import com.example.clancats.CatAbilities;
import com.example.clancats.CatVariants;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.List;

/** Fur picker. Each button just runs "/cat fur <name>"; the server does the rest. */
public class FurScreen extends Screen {
    public FurScreen() {
        super(Text.literal("Choose your fur"));
    }

    @Override
    protected void init() {
        List<String> names = CatVariants.namesWithDefault();
        int cols = 3, w = 104, h = 20, gap = 6;
        int startX = this.width / 2 - (cols * w + (cols - 1) * gap) / 2;
        int startY = 128;

        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            int x = startX + (i % cols) * (w + gap);
            int y = startY + (i / cols) * (h + gap);
            Text label = name.equals("default") ? Text.literal("Default") : Text.literal(CatVariants.pretty(name));
            this.addDrawableChild(ButtonWidget.builder(label, b -> choose(name))
                    .dimensions(x, y, w, h).build());
        }

        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> this.close())
                .dimensions(this.width / 2 - 50, this.height - 28, 100, 20).build());
    }

    private void choose(String name) {
        if (this.client == null) return;
        ClientPlayNetworkHandler handler = this.client.getNetworkHandler();
        if (handler != null) handler.sendChatCommand("cat fur " + name);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFF);

        if (this.client == null || this.client.player == null) return;
        if (!CatAbilities.isCat(this.client.player)) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("You are not a cat yet. Close this and run /cat become."),
                    this.width / 2, 60, 0xFFAAAA);
            return;
        }
        CatEntity preview = CatClientState.catFor(this.client.player);
        if (preview != null) {
            InventoryScreen.drawEntity(context, this.width / 2 - 50, 26, this.width / 2 + 50, 120,
                    70, 0.0625f, (float) mouseX, (float) mouseY, preview);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
