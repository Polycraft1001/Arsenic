package arsenic.command.impl;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import arsenic.command.Command;
import arsenic.command.CommandInfo;
import arsenic.gui.themes.Theme;
import arsenic.gui.themes.ThemeManager;
import arsenic.gui.themes.style.ThemeLoader;
import arsenic.main.Arsenic;
import arsenic.utils.minecraft.PlayerUtils;

import static arsenic.utils.java.JavaUtils.autoCompleteHelper;

@CommandInfo(name = "theme", args = {"load/reload/list", "name"}, aliases = { "t" },
        help = "loads a cgui theme, lists themes or reloads the theme files", minArgs = 1)
public class ThemeCommand extends Command {
    @Override
    public void execute(String[] args) {
        ThemeManager manager = Arsenic.getArsenic().getThemeManager();
        String action = args[0];

        if (action.equalsIgnoreCase("reload")) {
            // reads ~/Arsenic/themes/*.json again, even when no file date changed
            int count = manager.loadCustomThemes();
            PlayerUtils.addWaterMarkedMessageToChat("Reloaded §d§l" + count + "§r theme file(s) from " + ThemeLoader.folder());
            PlayerUtils.addWaterMarkedMessageToChat("Current theme: §d§l" + manager.getCurrentTheme().getJsonKey()
                    + "§r (errors are in the log)");
            return;
        }

        if (action.equalsIgnoreCase("list")) {
            Theme current = manager.getCurrentTheme();
            String names = manager.getContents().stream()
                    .map(theme -> theme == current ? "§d§l" + theme.getJsonKey() + "§r" : theme.getJsonKey())
                    .collect(Collectors.joining(", "));
            PlayerUtils.addWaterMarkedMessageToChat("Themes: " + names);
            return;
        }

        if (action.equalsIgnoreCase("load")) {
            if (args.length < 2) {
                PlayerUtils.addWaterMarkedMessageToChat("Usage: theme load <name>");
                return;
            }
            Theme theme = manager.getContentByJsonKey(args[1]);
            if (theme != null) {
                manager.setCurrentTheme(theme);
                PlayerUtils.addWaterMarkedMessageToChat("Set theme to §d§l" + theme.getJsonKey());
                return;
            }
            PlayerUtils.addWaterMarkedMessageToChat("Could not find §d§l" + args[1] + " theme");
            return;
        }
        PlayerUtils.addWaterMarkedMessageToChat(action + " is not a valid argument (try 'load', 'list' or 'reload')");
    }

    @Override
    protected List<String> getAutoComplete(String str, int arg, List<String> list) {
        if (arg == 0)
            return autoCompleteHelper(Arrays.asList("load", "list", "reload"), str);
        if (arg == 1)
            return autoCompleteHelper(Arsenic.getArsenic().getThemeManager().getContents().stream()
                    .map(Theme::getJsonKey).collect(Collectors.toList()), str);
        return list;
    }
}
