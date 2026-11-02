package com.rainsect.chatcompact;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.util.text.ChatType;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 监听客户端收到的聊天消息，把"内容一字不差"的重复消息折叠成一行并累加计数。
 *
 * 折叠粒度（模式2）：只要文本完全相同就合并，不要求连续；不同玩家/不同文本视为不同消息。
 *
 * 实现要点：
 *  - 接管所有 CHAT / SYSTEM 消息（跳过 GAME_INFO 即 action bar），取消原版处理，改由本类呈现。
 *  - 为每一种文本分配一个稳定的 chatId，并记录出现次数。
 *  - 借助 GuiNewChat.printChatMessageWithOptionalDeletion(comp, chatId) 的去重机制：
 *    同 chatId 会先 deleteChatLine 删除旧行、再把新行插到最上方，从而原位合并并更新计数。
 *  - 用 accessOrder 的 LinkedHashMap 做 LRU，最多跟踪 MAX_TRACKED 种文本，防止内存无限增长。
 *
 * 版本注意：1.12.2（14.23.5.x）的 ClientChatReceivedEvent.getType() 返回 ChatType 枚举而非 byte，
 * 这里直接使用 public getter，dev/prod 名称一致，无需反射。
 */
@SideOnly(Side.CLIENT)
public class ChatCompactListener {

    /** 计数后缀样式：灰色 " §8[xN]"。 */
    private static final String COUNT_PREFIX = " §8[x";
    private static final String COUNT_SUFFIX = "]";
    /** 计数封顶，超过显示 100+。 */
    private static final int COUNT_CAP = 100;
    /** 最多同时跟踪多少种不同文本（LRU 淘汰最久未出现的）。 */
    private static final int MAX_TRACKED = 512;

    /** 文本 -> 记录(chatId + 计数)；accessOrder=true 使其按最近使用排序，便于 LRU 淘汰。 */
    private final Map<String, Rec> history = new LinkedHashMap<String, Rec>(16, 0.75f, true);
    private int idSeq = 0;

    private static final class Rec {
        final int id;
        int count;
        Rec(int id) { this.id = id; }
    }

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        try {
            handle(event);
        } catch (Throwable t) {
            // 任何异常都不应影响正常聊天：保持消息原样放行（不取消事件）。
        }
    }

    private void handle(ClientChatReceivedEvent event) {
        if (event.getType() == ChatType.GAME_INFO) {
            return; // action bar（如伤害/提示条）不折叠，交还原版
        }

        final ITextComponent newComp = event.getMessage();
        if (newComp == null) {
            return;
        }

        final Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.ingameGUI == null) {
            return;
        }
        final GuiNewChat chat = mc.ingameGUI.getChatGUI();
        if (chat == null) {
            return;
        }

        final String text = newComp.getUnformattedText();

        // 接管消息，由本 mod 决定如何呈现。
        event.setCanceled(true);

        Rec rec = history.get(text); // 命中会把该条移到 LRU 末尾
        if (rec == null) {
            // 该文本第一次出现：分配稳定 chatId，正常显示（不带计数）。
            rec = new Rec(nextId());
            rec.count = 1;
            history.put(text, rec);
            chat.printChatMessageWithOptionalDeletion(newComp, rec.id);
            if (history.size() > MAX_TRACKED) {
                Iterator<String> it = history.keySet().iterator();
                if (it.hasNext()) {
                    it.next();
                    it.remove(); // 淘汰最久未出现的一种文本
                }
            }
        } else {
            // 再次出现（无论是否连续）：计数 +1，用同一 chatId 原位合并。
            rec.count++;
            String shown = rec.count >= COUNT_CAP ? COUNT_CAP + "+" : String.valueOf(rec.count);
            newComp.appendSibling(new TextComponentString(COUNT_PREFIX + shown + COUNT_SUFFIX));
            chat.printChatMessageWithOptionalDeletion(newComp, rec.id);
        }
    }

    /** 生成一个非 0 的 chatId（0 是原版默认，不参与去重删除）。 */
    private int nextId() {
        int id = ++idSeq;
        if (id == 0) {
            id = ++idSeq; // 仅在 int 溢出回绕时跳过 0
        }
        return id;
    }
}
