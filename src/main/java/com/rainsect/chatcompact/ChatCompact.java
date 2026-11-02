package com.rainsect.chatcompact;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Chat Compact —— 折叠聊天中重复的相同消息，显示为 [xN] 计数。
 * 纯客户端 mod，适用于 Minecraft 1.12.2 Forge。
 */
@Mod(modid = ChatCompact.MODID, name = ChatCompact.NAME, version = ChatCompact.VERSION,
        clientSideOnly = true, acceptedMinecraftVersions = "[1.12,1.12.2]")
public class ChatCompact {

    public static final String MODID = "chatcompact";
    public static final String NAME = "Chat Compact";
    public static final String VERSION = "1.0.0";

    @EventHandler
    @SideOnly(Side.CLIENT)
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new ChatCompactListener());
    }
}
