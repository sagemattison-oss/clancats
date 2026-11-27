package com.example.clancats.client;

import com.example.clancats.CatNames;
import com.example.clancats.RecruitPayload;
import com.example.clancats.entity.CatJob;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/** Name a new recruit and pick its job. */
public class RecruitScreen extends Screen {
    private final int entityId;
    private final String suggestedName;
    private TextFieldWidget nameField;
    private int jobIndex = 0;
    private final ButtonWidget[] jobButtons = new ButtonWidget[CatJob.values().length];

    public RecruitScreen(int entityId, String suggestedName) {
        super(Text.literal("Welcome a new clanmate"));
        this.entityId = entityId;
        this.suggestedName = suggestedName;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;

        nameField = new TextFieldWidget(this.textRenderer, cx - 100, 50, 200, 20, Text.literal("Name"));
        nameField.setMaxLength(24);
        nameField.setText(suggestedName);
        this.addDrawableChild(nameField);
        this.setInitialFocus(nameField);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Random name"),
                b -> nameField.setText(CatNames.random())).dimensions(cx - 100, 76, 200, 20).build());

        CatJob[] jobs = CatJob.values();
        for (int i = 0; i < jobs.length; i++) {
            final int index = i;
            jobButtons[i] = ButtonWidget.builder(Text.literal(jobs[i].title), b -> {
                jobIndex = index;
                refreshJobLabels();
            }).dimensions(cx - 105 + i * 72, 112, 66, 20).build();
            this.addDrawableChild(jobButtons[i]);
        }
        refreshJobLabels();

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Welcome to the clan"), b -> confirm())
                .dimensions(cx - 100, 170, 200, 20).build());
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, b -> this.close())
                .dimensions(cx - 50, 196, 100, 20).build());
    }

    private void refreshJobLabels() {
        CatJob[] jobs = CatJob.values();
        for (int i = 0; i < jobs.length; i++) {
            jobButtons[i].setMessage(Text.literal((i == jobIndex ? "> " : "") + jobs[i].title));
        }
    }

    private void confirm() {
        ClientPlayNetworking.send(new RecruitPayload(entityId, nameField.getText(), jobIndex));
        this.close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int cx = this.width / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, 20, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Name"), cx, 38, 0xAAAAAA);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Job"), cx, 100, 0xAAAAAA);
        CatJob job = CatJob.values()[jobIndex];
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(job.title + " " + job.blurb), cx, 142, 0xFFFFAA);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
