package arsenic.command.impl;

import arsenic.command.Command;
import arsenic.command.CommandInfo;
import arsenic.module.impl.client.ArsenicChat;
import arsenic.utils.minecraft.PlayerUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static arsenic.utils.java.JavaUtils.autoCompleteHelper;

@CommandInfo(name = "irc", args = { "message|nick|online|ignore|ignored", "..." }, aliases = { "ac" },
        help = "talks in the anonymous Arsenic chat (shortcut: #message)", minArgs = 0)
public class IrcCommand extends Command {

    @Override
    public void execute(String[] args) {
        ArsenicChat chat = ArsenicChat.get();
        if (chat == null) {
            PlayerUtils.addWaterMarkedMessageToChat("ArsenicChat module is unavailable");
            return;
        }
        if (!chat.isEnabled()) {
            PlayerUtils.addWaterMarkedMessageToChat("Enable the \u00a7bArsenicChat\u00a7r module first (.ArsenicChat)");
            return;
        }
        if (args.length == 0 || args[0].isEmpty()) {
            PlayerUtils.addWaterMarkedMessageToChat(chat.isConnected()
                    ? "Connected as \u00a7b" + chat.getNick() + "\u00a7r. Usage: .irc <message> | nick [name] | online | ignore <name> | ignored"
                    : "Not connected yet. Check the Server setting of ArsenicChat.");
            return;
        }
        if (args.length <= 2) {
            switch (args[0].toLowerCase()) {
                case "nick":
                    if (args.length == 1) {
                        PlayerUtils.addWaterMarkedMessageToChat(chat.isConnected()
                                ? "Your chat name is \u00a7b" + chat.getNick() : "Not connected");
                    } else if (!chat.requestNick(args[1])) {
                        notConnected();
                    }
                    return;
                case "online":
                    if (args.length == 1) {
                        if (!chat.requestOnline())
                            notConnected();
                        return;
                    }
                    break;
                case "ignore":
                    if (args.length == 2) {
                        PlayerUtils.addWaterMarkedMessageToChat(chat.toggleIgnore(args[1])
                                ? "Ignoring \u00a7c" + args[1] : "No longer ignoring \u00a7a" + args[1]);
                        return;
                    }
                    break;
                case "ignored":
                    if (args.length == 1) {
                        List<String> list = chat.getIgnored();
                        PlayerUtils.addWaterMarkedMessageToChat(list.isEmpty() ? "Nobody is ignored"
                                : "Ignored: " + String.join(", ", list));
                        return;
                    }
                    break;
                default:
                    break;
            }
        }
        if (!chat.sendMessage(String.join(" ", args).trim()))
            notConnected();
    }

    private static void notConnected() {
        PlayerUtils.addWaterMarkedMessageToChat("\u00a7cNot connected to the Arsenic chat");
    }

    @Override
    public List<String> getAutoComplete(String[] args) {
        if (args.length <= 1)
            return autoCompleteHelper(Arrays.asList("nick", "online", "ignore", "ignored"), args.length == 0 ? "" : args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("ignore")) {
            ArsenicChat chat = ArsenicChat.get();
            if (chat != null)
                return autoCompleteHelper(chat.getIgnored(), args[1]);
        }
        return new ArrayList<>();
    }
}
