package ru.meetfroz.compat;

import ru.meetfroz.client.ChatHit;

/**
 * «Утиный» интерфейс, навешиваемый миксином ChatHudMixin на ChatHud.
 * Позволяет получить текст строки чата и индекс символа под курсором,
 * переиспользуя собственную приватную раскладку игры.
 */
public interface IChatHudClick {

    /**
     * @return попадание клика по видимой строке чата либо null, если мимо.
     */
    ChatHit meetfroz$hitTest(double mouseX, double mouseY);
}
