package dev.tentaworks.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The action-bar line now showing: the controls hint never covers another line and is kept up only while it is still the one shown. */
@Mixin(Gui.class)
public interface GuiAccessor {
    @Accessor("overlayMessageString")
    @Nullable Component tentaworks$overlayMessage();

    @Accessor("overlayMessageTime")
    int tentaworks$overlayMessageTime();
}
