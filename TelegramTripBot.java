import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class TelegramTripBot {

    private static final String BOT_TOKEN = System.getenv("TELEGRAM_BOT_TOKEN") != null ? 
            System.getenv("TELEGRAM_BOT_TOKEN") : "8784092427:AAH_DEm_t-Z2yVXYEittLJ6PSy6OOBuB7kI";
    private static final String TELEGRAM_API_URL = "https://api.telegram.org/bot" + BOT_TOKEN;

    private static final String FULL_SCHEDULE = 
        "🌴 *KERALA - RAMESWARAM - TIRUPATI TOUR MASTERPLAN*\n" +
        "📅 *Dates:* 21-Oct to 02-Nov-2026 (13 Days)\n\n" +
        "• *Oct 21-22:* Vadodara ➔ Aluva (Tr 20932) ➔ Munnar\n" +
        "• *Oct 23:* Munnar (Mattupetty, Ripple Zipline & Double Decker Bus)\n" +
        "• *Oct 24:* Munnar ➔ Thekkady (Elephant Junction ₹1500 package)\n" +
        "• *Oct 25:* Thekkady ➔ Alleppey (Airbnb Suite & Mr Reji Shikara)\n" +
        "• *Oct 26:* Alleppey (Mullakkal Temple & Marari Beach)\n" +
        "• *Oct 27:* Alleppey ➔ Varkala (Tr 12075 Jan Shatabdi 06:35 PM)\n" +
        "• *Oct 28:* Varkala Cliff ➔ Trivandrum (Padmanabhaswamy) ➔ Tr 16343 (20:30) to RMM\n" +
        "• *Oct 29:* Rameswaram (22 Holy Wells bath & Ramanathaswamy Darshan)\n" +
        "• *Oct 30:* Dhanushkodi & Ram Setu Point ➔ Tr 16780 (16:55) to Tirupati\n" +
        "• *Oct 31:* Tirupati (Tirumala Balaji Darshan & Laddu prasadam)\n" +
        "• *Nov 01:* Padmavathi Ammavari Temple ➔ Tr 22620 from Renigunta (21:20)\n" +
        "• *Nov 02:* Arrive home at Raj Nandgaon (05:48 PM) 🎉";

    public static void main(String[] args) {
        System.out.println("🤖 Starting Telegram Trip Bot...");

        // Start lightweight HTTP health server for Render/Railway free hosting
        int port = 8080;
        try {
            String envPort = System.getenv("PORT");
            if (envPort != null && !envPort.isEmpty()) {
                port = Integer.parseInt(envPort);
            }
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", exchange -> {
                String resp = "Kerala Telegram & WhatsApp Trip Bot is Active & Running 24/7!";
                exchange.sendResponseHeaders(200, resp.length());
                OutputStream os = exchange.getResponseBody();
                os.write(resp.getBytes());
                os.close();
            });

            // Twilio WhatsApp Webhook Handler (No PC needed!)
            server.createContext("/whatsapp", exchange -> {
                if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    InputStream is = exchange.getRequestBody();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                    StringBuilder body = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) body.append(line);

                    Map<String, String> params = parseFormData(body.toString());
                    String incomingMsg = params.getOrDefault("Body", "").trim();
                    System.out.println("📩 WhatsApp query via Twilio: " + incomingMsg);

                    String reply = getAnswerForQuery(incomingMsg);
                    String twiml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                   "<Response>\n" +
                                   "    <Message>" + escapeXml(reply) + "</Message>\n" +
                                   "</Response>";

                    byte[] bytes = twiml.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/xml; charset=UTF-8");
                    exchange.sendResponseHeaders(200, bytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(bytes);
                    os.close();
                } else {
                    String ok = "Twilio WhatsApp Webhook endpoint is healthy!";
                    exchange.sendResponseHeaders(200, ok.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(ok.getBytes());
                    os.close();
                }
            });

            server.setExecutor(null);
            server.start();
            System.out.println("🌐 Cloud Web Server (Telegram + WhatsApp) listening on port " + port);
        } catch (Exception e) {
            System.err.println("Could not start HTTP server: " + e.getMessage());
        }

        // Run Telegram Long-Polling Loop
        long lastUpdateId = 0;
        while (true) {
            try {
                String updatesJson = fetchUpdates(lastUpdateId);
                if (updatesJson != null && updatesJson.contains("\"update_id\":")) {
                    lastUpdateId = processUpdates(updatesJson, lastUpdateId);
                }
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                System.err.println("Polling error: " + e.getMessage());
                try { Thread.sleep(3000); } catch (Exception ignored) {}
            }
        }
    }

    private static String fetchUpdates(long offset) {
        try {
            URL url = new URL(TELEGRAM_API_URL + "/getUpdates?timeout=20&offset=" + (offset > 0 ? (offset + 1) : 0));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder resp = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) resp.append(line);
                in.close();
                return resp.toString();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static long processUpdates(String json, long currentOffset) {
        long highestId = currentOffset;
        String[] updates = json.split("\\{\"update_id\":");
        for (int i = 1; i < updates.length; i++) {
            String chunk = updates[i];
            long id = extractLong(chunk, 0);
            if (id > highestId) highestId = id;

            // Check callback_query (button press) or standard message
            if (chunk.contains("\"callback_query\":")) {
                handleCallbackQuery(chunk);
            } else if (chunk.contains("\"message\":")) {
                handleTextMessage(chunk);
            }
        }
        return highestId;
    }

    private static void handleTextMessage(String chunk) {
        long chatId = extractChatId(chunk);
        String text = extractMessageText(chunk);
        if (chatId == 0 || text == null) return;

        text = text.trim();
        System.out.println("📩 Text from " + chatId + ": " + text);

        if (text.equalsIgnoreCase("/start") || text.equalsIgnoreCase("/menu") || text.equalsIgnoreCase("hi") || text.equalsIgnoreCase("hello")) {
            sendMainMenu(chatId);
        } else {
            String answer = getAnswerForQuery(text);
            sendMessage(chatId, answer, true);
        }
    }

    private static void handleCallbackQuery(String chunk) {
        long chatId = extractCallbackChatId(chunk);
        String data = extractCallbackData(chunk);
        if (chatId == 0 || data == null) return;

        System.out.println("🔘 Button Click from " + chatId + ": " + data);
        String reply = getAnswerForQuery(data);
        sendMessage(chatId, reply, true);
    }

    private static String getAnswerForQuery(String q) {
        String lower = q.toLowerCase();

        if (lower.contains("train") || lower.contains("berth") || lower.contains("pnr") || lower.contains("btn_berths")) {
            return "🚆 *TRAIN 16343 AMRITHA EXPRESS (TVC ➔ RMM)*\n" +
                   "📅 28-Oct 20:30 (8:30 PM) ➔ 29-Oct 12:15 PM\n" +
                   "📍 All 8 Confirmed Berths in *Coach S12*:\n\n" +
                   "• *Savitri Sahu:* Berth 40 (Side Upper)\n" +
                   "• *Narayandas Sahu:* Berth 32 (Side Upper)\n" +
                   "• *Jaydev Sahu:* Berth 43 (Upper)\n" +
                   "• *Jigar Sahu:* Berth 42 (Middle)\n" +
                   "• *Hemlata:* Berth 34 (Middle)\n" +
                   "• *Yogank:* Berth 35 (Upper)\n" +
                   "• *Mohardas Sahu:* Berth 37 (Middle)\n" +
                   "• *Rukhamanibai:* Berth 38 (Upper)\n\n" +
                   "PNRs: `4865798071` & `4952965974`";
        }

        if (lower.contains("elephant") || lower.contains("thekkady") || lower.contains("btn_elephant")) {
            return "🐘 *ELEPHANT JUNCTION (THEKKADY)*\n" +
                   "📍 Address: Murukkady P.O., Kumily, Kerala 685535\n" +
                   "🗺️ Map Pin: https://maps.app.goo.gl/Wyr4eo4hNsErXmBD7\n" +
                   "📞 Call: `+919447163342` / `+914869224142`\n\n" +
                   "⚠️ *IMPORTANT DIRECTIONS:* Must take Laksham Veedu Colony Road directly in front of KSEB Substation. (Avoid Karimuttam's on the main road!)\n\n" +
                   "💰 *Selected:* ₹1,500 package only.";
        }

        if (lower.contains("alleppey") || lower.contains("boating") || lower.contains("reji") || lower.contains("shikara") || lower.contains("btn_alleppey")) {
            return "🛶 *ALLEPPEY STAY & BOATING*\n\n" +
                   "🏡 *Accommodation:* Private Suite Stay\n" +
                   "🔗 Airbnb Link: https://www.airbnb.com/l/oEZFBOte\n\n" +
                   "⛵ *Boating Contact:* Mr. Reji (Saved: 'Mr Reji Kayaking Alleppey')\n" +
                   "✅ *Plan:* Book full exclusive Shikara boat ride for entire family.\n" +
                   "❌ *Note:* Strictly NO houseboat stay.\n\n" +
                   "🏖️ *Places:* Marari Beach, Mullakkal Temple, Mount Carmel Church.";
        }

        if (lower.contains("munnar") || lower.contains("zipline") || lower.contains("ripple") || lower.contains("btn_munnar")) {
            return "🧗 *MUNNAR ACTIVITIES (22-23 Oct)*\n\n" +
                   "1️⃣ *Ripple Adventure Zipline:*\n" +
                   "📍 Location: Panniyarkutty, Munnar\n" +
                   "🗺️ Map Pin: https://maps.google.com/maps?q=9.97966480255127%2C77.06129455566406&z=17&hl=en\n" +
                   "💰 Pricing: ~₹500/person for zipline\n" +
                   "🕒 Timing: 8:30 AM – 5:30 PM\n\n" +
                   "2️⃣ *KSRTC Double Decker Sightseeing Bus:*\n" +
                   "🚌 3-Hour evening tour (departs ~4:30 PM)\n" +
                   "🌐 Booking: https://onlineksrtcswift.com/";
        }

        if (lower.contains("varkala") || lower.contains("cliff") || lower.contains("btn_varkala")) {
            return "🏖️ *VARKALA COASTAL HIGHLIGHTS*\n\n" +
                   "🚆 *Train to Varkala (27-Oct):*\n" +
                   "• Train 12075 Jan Shatabdi: ALLP 06:35 PM ➔ VAK 08:13 PM\n\n" +
                   "🌅 *Must-See Spots:*\n" +
                   "• Varkala Cliff (Sunset, cafes, panoramic sea views)\n" +
                   "• Black Sand Beach (Dark sand shoreline)\n" +
                   "• Optional: Paravoor backwaters & Mangrove Island Kayaking (~30 mins away)";
        }

        if (lower.contains("schedule") || lower.contains("itinerary") || lower.contains("plan") || lower.contains("btn_schedule")) {
            return FULL_SCHEDULE;
        }

        return "🤖 *Trip Assistant:* I received your question: \"" + q + "\".\n\n" +
               "Tap an option below or type: *train*, *elephant*, *alleppey*, *munnar*, *varkala*, or *schedule*!";
    }

    private static void sendMainMenu(long chatId) {
        String welcome = "🌴 *Welcome to Kerala & South India Trip Guide!* 🌴\n\n" +
                         "I am your 24/7 pocket assistant for all tickets, berths, directions, and contacts.\n\n" +
                         "👇 *Tap a button below or type any question:*";

        String inlineKeyboard = "{\"inline_keyboard\":[" +
            "[{\"text\":\"🚆 Train Berths & PNR\",\"callback_data\":\"btn_berths\"},{\"text\":\"🐘 Elephant Junction\",\"callback_data\":\"btn_elephant\"}]," +
            "[{\"text\":\"🛶 Alleppey Boating & Stay\",\"callback_data\":\"btn_alleppey\"},{\"text\":\"🧗 Munnar Zipline\",\"callback_data\":\"btn_munnar\"}]," +
            "[{\"text\":\"🏖️ Varkala Cliffs\",\"callback_data\":\"btn_varkala\"},{\"text\":\"📅 Full 13-Day Plan\",\"callback_data\":\"btn_schedule\"}]" +
            "]}";

        sendPostMessage(chatId, welcome, inlineKeyboard);
    }

    private static void sendMessage(long chatId, String text, boolean withMenuButton) {
        String inlineKeyboard = withMenuButton ? 
            "{\"inline_keyboard\":[[{\"text\":\"🔙 Back to Menu\",\"callback_data\":\"/start\"}]]}" : null;
        sendPostMessage(chatId, text, inlineKeyboard);
    }

    private static void sendPostMessage(long chatId, String text, String replyMarkup) {
        try {
            URL url = new URL(TELEGRAM_API_URL + "/sendMessage");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);

            String escapedText = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
            String json = "{\"chat_id\":" + chatId + ",\"text\":\"" + escapedText + "\",\"parse_mode\":\"Markdown\"";
            if (replyMarkup != null) {
                json += ",\"reply_markup\":" + replyMarkup;
            }
            json += "}";

            OutputStream os = conn.getOutputStream();
            os.write(json.getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            if (code != 200) {
                BufferedReader err = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
                StringBuilder errStr = new StringBuilder();
                String line;
                while ((line = err.readLine()) != null) errStr.append(line);
                err.close();
                System.err.println("Telegram API error (" + code + "): " + errStr);
            }
        } catch (Exception e) {
            System.err.println("Send error: " + e.getMessage());
        }
    }

    private static long extractChatId(String str) {
        int idx = str.indexOf("\"chat\":{\"id\":");
        if (idx == -1) return 0;
        return extractLong(str, idx + 13);
    }

    private static long extractCallbackChatId(String str) {
        int idx = str.indexOf("\"from\":{\"id\":");
        if (idx == -1) return 0;
        return extractLong(str, idx + 13);
    }

    private static String extractCallbackData(String str) {
        int idx = str.indexOf("\"data\":\"");
        if (idx == -1) return null;
        int end = str.indexOf("\"", idx + 8);
        return end != -1 ? str.substring(idx + 8, end) : null;
    }

    private static String extractMessageText(String str) {
        int idx = str.indexOf("\"text\":\"");
        if (idx == -1) return null;
        int end = str.indexOf("\"", idx + 8);
        return end != -1 ? str.substring(idx + 8, end) : null;
    }

    private static long extractLong(String str, int startIdx) {
        StringBuilder sb = new StringBuilder();
        for (int i = startIdx; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isDigit(c) || (c == '-' && sb.length() == 0)) sb.append(c);
            else if (sb.length() > 0) break;
        }
        try { return Long.parseLong(sb.toString()); } catch (Exception e) { return 0; }
    }

    private static Map<String, String> parseFormData(String formData) {
        Map<String, String> map = new HashMap<>();
        if (formData == null || formData.isEmpty()) return map;
        String[] pairs = formData.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            try {
                String key = idx > 0 ? URLDecoder.decode(pair.substring(0, idx), "UTF-8") : pair;
                String value = idx > 0 && pair.length() > idx + 1 ? URLDecoder.decode(pair.substring(idx + 1), "UTF-8") : "";
                map.put(key, value);
            } catch (Exception ignored) {}
        }
        return map;
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
