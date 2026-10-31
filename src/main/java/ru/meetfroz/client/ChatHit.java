package ru.meetfroz.client;

/**
 * Результат «теста на попадание» клика по строке чата.
 * Не содержит версия-специфичных типов — строится миксином ChatHudMixin.
 */
public final class ChatHit {

    /** Полный текст строки чата, по которой кликнули. */
    public final String line;
    /** Индекс символа строки, на который указывает курсор. */
    public final int charIndex;

    public ChatHit(String line, int charIndex) {
        this.line = line;
        this.charIndex = charIndex;
    }
}
