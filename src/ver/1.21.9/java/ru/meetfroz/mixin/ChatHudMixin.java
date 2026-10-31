package ru.meetfroz.mixin;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.OrderedText;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import ru.meetfroz.client.ChatHit;
import ru.meetfroz.compat.IChatHudClick;

import java.util.List;

/**
 * Переиспользует СОБСТВЕННУЮ приватную раскладку чата игры, чтобы получить
 * текст строки и индекс символа под курсором. Математика берётся из самой игры
 * (toChatLineX/Y + getMessageLineIndex), поэтому не ломается между версиями.
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin implements IChatHudClick {

    @Shadow
    private List<ChatHudLine.Visible> visibleMessages;

    @Shadow
    private net.minecraft.client.MinecraftClient client;

    @Shadow
    private double toChatLineX(double x) {
        throw new AssertionError();
    }

    @Shadow
    private double toChatLineY(double y) {
        throw new AssertionError();
    }

    @Shadow
    private int getMessageLineIndex(double x, double y) {
        throw new AssertionError();
    }

    @Override
    public ChatHit meetfroz$hitTest(double mouseX, double mouseY) {
        ChatHud self = (ChatHud) (Object) this;
        if (!self.isChatFocused()) return null; // клик по сообщениям работает при открытом чате

        double lineX = this.toChatLineX(mouseX);
        double lineY = this.toChatLineY(mouseY);
        int idx = this.getMessageLineIndex(lineX, lineY);
        if (idx < 0 || idx >= this.visibleMessages.size()) return null;

        OrderedText content = this.visibleMessages.get(idx).content();
        StringBuilder sb = new StringBuilder();
        content.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });

        int charIndex = MathHelper.floor(lineX);
        if (charIndex < 0) charIndex = 0;
        if (charIndex > sb.length()) charIndex = sb.length();
        return new ChatHit(sb.toString(), charIndex);
    }
}
