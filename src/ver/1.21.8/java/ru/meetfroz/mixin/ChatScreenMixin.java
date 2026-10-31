package ru.meetfroz.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.meetfroz.client.ChatCoordinateHandler;
import ru.meetfroz.client.ChatHit;
import ru.meetfroz.client.GpsSuggestions;
import ru.meetfroz.compat.IChatHudClick;

/**
 * Перехватывает клик мыши в экране чата (создание метки по координатам) и
 * добавляет настоящее автодополнение команды .gps: TAB циклически подставляет
 * варианты, а над полем ввода рисуется список подсказок с описаниями.
 */
@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    @Shadow
    protected TextFieldWidget chatField;

    // ---------------------------------------------------------------- клик по координатам

    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true)
    private void meetfroz$onMouseClick(double mouseX, double mouseY, int button,
                                       CallbackInfoReturnable<Boolean> cir) {
        if (button != 0) return;
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc.player == null || mc.inGameHud == null) return;

        ChatHit hit = ((IChatHudClick) mc.inGameHud.getChatHud()).meetfroz$hitTest(mouseX, mouseY);
        if (hit == null) return;

        if (ChatCoordinateHandler.handleClick(hit.line, hit.charIndex)) {
            cir.setReturnValue(true);
        }
    }

    // ---------------------------------------------------------------- автодополнение .gps

    @Inject(method = "keyPressed(III)Z", at = @At("HEAD"), cancellable = true)
    private void meetfroz$onKeyPressed(int keyCode, int scanCode, int modifiers,
                                       CallbackInfoReturnable<Boolean> cir) {
        if (keyCode != GLFW.GLFW_KEY_TAB) return;
        String text = this.chatField.getText();
        if (GpsSuggestions.onTab(text, t -> {
            this.chatField.setText(t);
            this.chatField.setCursorToEnd(false);
        })) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void meetfroz$onRender(DrawContext context, int mouseX, int mouseY, float delta,
                                   CallbackInfo ci) {
        GpsSuggestions.render(context, this.chatField.getText());
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void meetfroz$onRemoved(CallbackInfo ci) {
        GpsSuggestions.reset();
    }
}
