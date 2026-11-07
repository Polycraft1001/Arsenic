package arsenic.module.impl.client;

import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.TextProperty;
import arsenic.utils.java.FileUtils;
import arsenic.utils.minecraft.PlayerUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anonymous chat between Arsenic users, through the relay in tools/chat-relay.
 * The relay only receives a random token it generated itself: never the Minecraft username, UUID or session.
 */
@ModuleInfo(name = "ArsenicChat", description = "Anonymous chat between Arsenic users (.irc / #message)",
        category = ModuleCategory.CLIENT, tier = ModuleTier.QOL)
public class ArsenicChat extends Module {

    public static final String ICON = "\u2726";
    private static final String PREFIX = "\u00a78[\u00a7bChat\u00a78] \u00a7r";
    private static final int DEFAULT_PORT = 25600;

    public final TextProperty server = new TextProperty("Server", "127.0.0.1:25600", 64);
    public final BooleanProperty hashShortcut = new BooleanProperty("# Shortcut", true);
    public final BooleanProperty joinMessages = new BooleanProperty("Join Messages", true);
    public final BooleanProperty discordMessages = new BooleanProperty("Discord Messages", true);

    private final Set<String> ignored = ConcurrentHashMap.newKeySet();
    private final Object writeLock = new Object();
    private volatile Thread thread;
    private volatile Socket socket;
    private volatile Writer out;
    private volatile String nick;
    private volatile long backoff = 2000;
    private volatile boolean announcedFailure;

    public static ArsenicChat get() {
 return Arsenic.getArsenic().getModuleManager().getModuleByClass(ArsenicChat.class);
    }

    /** "#message" shortcut from the vanilla chat. @return true if the message was handled */
    public static boolean handleShortcut(String msg) {
        if (msg.length() < 2 || msg.charAt(0) != '#')
     return false;
        ArsenicChat chat = get();
        if (chat == null || !chat.isEnabled() || !chat.hashShortcut.getValue())
     return false;
 if (!chat.sendMessage(msg.substring(1).trim()))
     chat.show("\u00a7cNot connected to the Arsenic chat.");
        return true;
    }

    @Override
    protected void onEnable() {
 loadIgnored();
        announcedFailure = false;
        backoff = 2000;
 Thread t = new Thread(this::run, "Arsenic-Chat");
        t.setDaemon(true);
        thread = t;
        t.start();
    }

    @Override
    protected void onDisable() {
        Thread t = thread;
        thread = null;
        closeSocket();
        if (t != null)
     t.interrupt();
 nick = null;
    }

    // ---------------------------------------------------------------- API used by .irc

    public boolean isConnected() { return nick != null && out != null; }

    public String getNick() { return nick; }

    public boolean sendMessage(String text) {
        if (text.isEmpty())
     return true;
        JsonObject o = packet("msg");
        o.addProperty("text", text);
        return trySend(o);
    }

    public boolean requestNick(String name) {
        JsonObject o = packet("nick");
        o.addProperty("name", name);
 return trySend(o);
    }

    public boolean requestOnline() { return trySend(packet("online")); }

    public boolean requestRandomNick() { return trySend(packet("gennick")); }

    /** @return true if the name is now ignored, false if it no longer is */
    public boolean toggleIgnore(String name) {
        String key = name.toLowerCase();
        boolean now = !ignored.remove(key);
        if (now)
     ignored.add(key);
        saveIgnored();
        return now;
    }

    public List<String> getIgnored() { return new ArrayList<>(ignored); }

    // ---------------------------------------------------------------- network

    private void run() {
 Thread self = Thread.currentThread();
        while (thread == self) {
     try (Socket s = new Socket()) {
  socket = s;
  InetSocketAddress address = parseServer();
         s.connect(address, 5000);
  s.setSoTimeout(30000);
         s.setKeepAlive(true);
  s.setTcpNoDelay(true);
  BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
         out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));

  JsonObject hello = packet("hello");
         String token = readToken();
  if (token != null)
      hello.addProperty("token", token);
         send(hello);

  int idle = 0;
         while (thread == self) {
      String line;
      try {
          line = in.readLine();
      } catch (SocketTimeoutException e) {
          if (++idle > 2)
throw new IOException("server stopped responding");
   send(packet("ping"));
   continue;
      }
      if (line == null)
   break;
      idle = 0;
      if (line.isEmpty() || line.length() > 8192)
   continue;
      try {
   handle(new JsonParser().parse(line).getAsJsonObject());
      } catch (RuntimeException ignoredPacket) {
   // malformed packet: ignored
      }
         }
            } catch (IOException | RuntimeException e) {
         if (thread == self && !announcedFailure) {
      announcedFailure = true;
      show("\u00a7cArsenic chat unreachable (" + e.getMessage() + "), retrying in the background.");
         }
     } finally {
         out = null;
  socket = null;
         if (nick != null && thread == self)
      show("\u00a77Disconnected from the Arsenic chat, reconnecting...");
  nick = null;
     }
            if (thread != self)
  break;
     try {
  Thread.sleep(backoff);
     } catch (InterruptedException e) {
         break;
     }
            backoff = Math.min(backoff * 2, 60000);
        }
    }

    private void handle(JsonObject m) {
        String type = str(m, "t");
 switch (type) {
     case "welcome": {
         nick = str(m, "nick");
         String token = str(m, "token");
         if (!token.isEmpty())
      writeToken(token);
  backoff = 2000;
         announcedFailure = false;
         int online = m.has("online") ? m.get("online").getAsInt() : 1;
         show("\u00a77Connected as \u00a7b" + nick + "\u00a77 (" + online + " online). "
          + "Type \u00a7f#message\u00a77 or \u00a7f.irc <message>\u00a77.");
         break;
     }
     case "msg": {
  String from = str(m, "from");
  if (ignored.contains(from.toLowerCase()))
      return;
         boolean discord = "discord".equals(str(m, "src"));
  if (discord && !discordMessages.getValue())
      return;
  String name = discord ? "\u00a79[Discord] \u00a73" + from
   : "\u00a7b" + ICON + " " + (from.equalsIgnoreCase(nick) ? "\u00a7b" : "\u00a73") + from;
         show(name + "\u00a78: \u00a7f" + str(m, "text"));
  break;
     }
     case "join":
         if (joinMessages.getValue())
      show("\u00a7a+ \u00a77" + str(m, "nick"));
         break;
     case "leave":
  if (joinMessages.getValue())
      show("\u00a7c- \u00a77" + str(m, "nick"));
         break;
     case "rename":
  show("\u00a77" + str(m, "from") + " is now \u00a7b" + str(m, "to"));
  break;
            case "nick":
         nick = str(m, "nick");
         break;
     case "online": {
         List<String> users = new ArrayList<>();
         JsonElement e = m.get("users");
         if (e != null && e.isJsonArray())
      for (JsonElement u : (JsonArray) e)
   users.add(clean(u.getAsString()));
         show("\u00a77" + users.size() + " online: \u00a7f" + String.join("\u00a77, \u00a7f", users));
         break;
     }
     case "sys":
  show("\u00a7e[Announcement] \u00a7f" + str(m, "text"));
         break;
     case "err":
         show("\u00a7c" + str(m, "text"));
         if (m.has("fatal") && m.get("fatal").getAsBoolean()) {
      thread = null; // no reconnect (ban)
      mc.addScheduledTask(() -> setEnabled(false));
         }
  break;
     default:
         break;
 }
    }

    private boolean trySend(JsonObject o) {
        if (!isConnected())
     return false;
        try {
     send(o);
     return true;
        } catch (IOException e) {
            closeSocket();
            return false;
 }
    }

    private void send(JsonObject o) throws IOException {
        synchronized (writeLock) {
     Writer w = out;
     if (w == null)
         throw new IOException("not connected");
     w.write(o.toString());
     w.write('\n');
     w.flush();
        }
    }

    private void closeSocket() {
        Socket s = socket;
        if (s != null)
     try { s.close(); } catch (IOException ignoredClose) { }
    }

    private InetSocketAddress parseServer() throws IOException {
        String value = server.getValue().trim();
        int colon = value.lastIndexOf(':');
        String host = colon > 0 ? value.substring(0, colon) : value;
        int port = DEFAULT_PORT;
        if (colon > 0) {
     try {
  port = Integer.parseInt(value.substring(colon + 1).trim());
     } catch (NumberFormatException e) {
  throw new IOException("invalid port in Server");
            }
        }
 if (host.isEmpty() || port < 1 || port > 65535)
     throw new IOException("invalid Server address");
        return new InetSocketAddress(host, port);
    }

    // ---------------------------------------------------------------- affichage

    private void show(String msg) {
        mc.addScheduledTask(() -> {
     if (mc.thePlayer != null)
         PlayerUtils.addMessageToChat(PREFIX + msg);
 });
    }

    private static JsonObject packet(String type) {
        JsonObject o = new JsonObject();
        o.addProperty("t", type);
 return o;
    }

    private static String str(JsonObject m, String key) {
        JsonElement e = m.get(key);
        return e == null || e.isJsonNull() ? "" : clean(e.getAsString());
    }

    /** Network input must never inject colour codes or control characters. */
    private static String clean(String s) {
        return s.replace("\u00a7", "").replaceAll("[\\p{Cntrl}\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2069\\uFEFF]", "");
    }

    // ---------------------------------------------------------------- fichiers locaux

    private static File file(String name) { return new File(FileUtils.getArsenicFolderDirAsFile(), name); }

    private static String readToken() {
        try {
     File f = file("chat-token.txt");
     if (!f.isFile())
  return null;
     String t = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
     return t.matches("[a-f0-9]{64}") ? t : null;
 } catch (IOException e) {
     return null;
        }
    }

    private static void writeToken(String token) {
 if (!token.matches("[a-f0-9]{64}") || token.equals(readToken()))
     return;
 try {
     File f = file("chat-token.txt");
     Files.write(f.toPath(), token.getBytes(StandardCharsets.UTF_8));
     f.setReadable(false, false);
     f.setReadable(true, true);
     f.setWritable(false, false);
     f.setWritable(true, true);
        } catch (IOException ignoredWrite) { }
    }

    private void loadIgnored() {
        ignored.clear();
        try {
     File f = file("chat-ignore.txt");
     if (f.isFile())
         for (String l : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8))
      if (!l.trim().isEmpty())
   ignored.add(l.trim().toLowerCase());
        } catch (IOException ignoredRead) { }
    }

    private void saveIgnored() {
        try {
     Files.write(file("chat-ignore.txt").toPath(), ignored, StandardCharsets.UTF_8);
        } catch (IOException ignoredWrite) { }
    }
}
