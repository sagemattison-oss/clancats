package com.example.clancats.client;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** The Clan Hub (J): everything the /cat commands do, as buttons. */
public class HubScreen extends MenuScreen {
    private boolean confirmRelease = false;

    public HubScreen(JsonObject data) {
        super(Text.literal("Clan Hub"), data);
    }

    @Override
    public String menuId() {
        return "hub";
    }

    @Override
    public void update(JsonObject next) {
        confirmRelease = false;
        super.update(next);
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int left = cx - 155;
        int right = cx + 5;

        if (!flag("cat")) {
            button(cx - 100, this.height / 2 - 10, 200, "Become a cat", true,
                    "Take cat form: small, fast, a climber with night vision.", b -> send("become", "", "hub"));
            button(cx - 50, this.height / 2 + 16, 100, "Close", true, null, b -> this.close());
            return;
        }

        boolean clanless = str("clan").isEmpty();
        boolean inCamp = flag("inCamp");
        String owner = str("campOwner");

        int y = 78;
        // Row 1: clan
        button(left, y, 150, clanless ? "Choose a clan" : "Change clan", true,
                "See the four clans and join one.", b -> send("open", "clans", ""));
        button(right, y, 150, "Leave clan", !clanless, clanless ? "You are already a loner." : null,
                b -> send("leave", "", "hub"));
        // Row 2: finding camps
        y += 22;
        button(left, y, 150, "Find a wild camp", true,
                "Points to the nearest wild camp. Cave camps have a cave mouth and ladder shafts marked by log posts; "
                        + "surface camps are a hedged clearing.", b -> send("findcamp", "", "hub"));
        button(right, y, 150, "My clan's camp", flag("hasCamp"),
                flag("hasCamp") ? null : (clanless ? "Join a clan first." : "Your clan has no camp yet."),
                b -> send("camp", "", "hub"));
        // Row 3: claim / release
        y += 22;
        String claimTip = !inCamp ? "Stand inside a wild camp." : !owner.isEmpty() ? "Already claimed by " + owner + "."
                : clanless ? "Join a clan first." : !flag("mayCommand") ? "Needs Warrior rank or higher." : null;
        button(left, y, 150, "Claim this camp", flag("canClaim"), claimTip, b -> send("claim", "", "hub"));
        String relTip = !inCamp ? "Stand inside the camp you want to release." : owner.isEmpty() ? "This camp isn't claimed."
                : !flag("canRelease") ? "Only your clan's Leader or Deputy (or an operator) can release a camp." : null;
        button(right, y, 150, confirmRelease ? "Really release? Click again" : "Release this camp", flag("canRelease"),
                relTip != null ? relTip : "Releasing a camp loses its upgrades.", b -> {
                    if (!confirmRelease) {
                        confirmRelease = true;
                        b.setMessage(Text.literal("Really release? Click again"));
                    } else {
                        send("unclaim", "", "hub");
                    }
                });
        // Row 4: upgrades / fur
        y += 22;
        String upTip = clanless ? "Join a clan first." : !flag("hasCamp") ? "Your clan has no camp yet."
                : !flag("nearCamp") ? "Go to your camp first (within 48 blocks)." : "Or right-click the camp's fresh-kill barrel.";
        button(left, y, 150, "Camp upgrades", flag("nearCamp"), upTip, b -> send("open", "upgrades", ""));
        button(right, y, 150, "Fur menu", true, "Change your coat.", b -> this.client.setScreen(new FurScreen()));
        // Row 5: sounds
        y += 22;
        button(left, y, 100, "Meow", true, null, b -> send("sound", "meow", "hub"));
        button(cx - 50, y, 100, "Purr", true, null, b -> send("sound", "purr", "hub"));
        button(cx + 55, y, 100, "Hiss", true, null, b -> send("sound", "hiss", "hub"));
        // Row 6: revert / admin
        y += 22;
        if (flag("op")) {
            button(left, y, 150, "Revert to human", true, "Put your paws away. Your rank and prey are kept.",
                    b -> send("revert", "", "hub"));
            button(right, y, 150, "Set prey (operator)", true, null, b -> send("open", "admin", ""));
        } else {
            button(cx - 75, y, 150, "Revert to human", true, "Put your paws away. Your rank and prey are kept.",
                    b -> send("revert", "", "hub"));
        }
        y += 26;
        button(cx - 50, y, 100, "Close", true, null, b -> this.close());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int cx = this.width / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, 10, 0xFFAA00);

        if (!flag("cat")) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("You walk on two legs."), cx,
                    this.height / 2 - 40, 0xAAAAAA);
            drawMessage(context, cx, this.height / 2 - 26);
            return;
        }

        String rank = str("rank") + "  |  " + num("prey") + " prey";
        if (!str("nextRank").isEmpty()) rank += "  (" + num("nextNeeded") + " more for " + str("nextRank") + ")";
        context.drawCenteredTextWithShadow(this.textRenderer, colored(rank, num("rankColor")), cx, 24, 0xFFFFFF);

        String clan = str("clanName");
        if (flag("leader")) clan += "  |  Lives " + num("lives") + "/9";
        context.drawCenteredTextWithShadow(this.textRenderer, colored(clan, num("clanColor")), cx, 36, 0xFFFFFF);

        String camp = flag("hasCamp")
                ? "Camp at " + num("campX") + " " + num("campY") + " " + num("campZ") + "  (" + num("campDist")
                + " blocks, " + num("campUpgrades") + "/" + num("campMax") + " upgrades)"
                : (str("clan").isEmpty() ? "Loners have no camp." : "No camp claimed yet.");
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(camp), cx, 48, 0xAAAAAA);

        drawMessage(context, cx, 60);
    }
}
