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

    public static void main(String[] args) {
        System.out.println("🤖 Starting Ultimate Kerala & South India Trip Assistant Bot...");

        // Start HTTP Health & Twilio Webhook Server
        int port = 8080;
        try {
            String envPort = System.getenv("PORT");
            if (envPort != null && !envPort.isEmpty()) port = Integer.parseInt(envPort);

            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", exchange -> {
                String resp = "Kerala & South India Comprehensive 24/7 Trip Assistant Active!";
                exchange.sendResponseHeaders(200, resp.length());
                OutputStream os = exchange.getResponseBody();
                os.write(resp.getBytes());
                os.close();
            });

            // Twilio WhatsApp Webhook Endpoint
            server.createContext("/whatsapp", exchange -> {
                if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    InputStream is = exchange.getRequestBody();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                    StringBuilder body = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) body.append(line);

                    Map<String, String> params = parseFormData(body.toString());
                    String incomingMsg = params.getOrDefault("Body", "").trim();
                    System.out.println("📩 WhatsApp Query: " + incomingMsg);

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
                    String ok = "WhatsApp Endpoint Active!";
                    exchange.sendResponseHeaders(200, ok.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(ok.getBytes());
                    os.close();
                }
            });

            server.setExecutor(null);
            server.start();
            System.out.println("🌐 Cloud Web Server active on port " + port);
        } catch (Exception e) {
            System.err.println("Could not start HTTP server: " + e.getMessage());
        }

        // Long-polling for Telegram updates
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
        if (data.equalsIgnoreCase("/start") || data.equalsIgnoreCase("btn_menu")) {
            sendMainMenu(chatId);
        } else {
            String reply = getAnswerForQuery(data);
            sendMessage(chatId, reply, true);
        }
    }

    // Comprehensive Knowledge Base Engine
    public static String getAnswerForQuery(String q) {
        String lower = q.toLowerCase();

        // 1. Train Berths & PNR Data
        if (lower.contains("train") || lower.contains("berth") || lower.contains("pnr") || lower.contains("seat") || lower.contains("btn_berths")) {
            return "🚆 *COMPLETE TRAIN & BERTH DIRECTORY*\n\n" +
                   "1️⃣ *TRAIN 20932 (INDB TVCN SF EX)*\n" +
                   "• Vadodara (BRC) ➔ Aluva (AWY) | 21-Oct 05:06 AM ➔ 22-Oct 11:18 AM\n" +
                   "• PNR: `8253285113` | Class: Sleeper (SL)\n" +
                   "• 6 Passengers: Hemlata (35), Yogank (35), Jigar (26), Jaydev (26), Savitri (55), Narayandas (57)\n\n" +
                   "2️⃣ *TRAIN 16343 AMRITHA EXPRESS (Coach S12 Confirmed)*\n" +
                   "• TVC (Trivandrum) ➔ RMM (Rameswaram) | 28-Oct 20:30 ➔ 29-Oct 12:15\n" +
                   "• PNRs: `4865798071` & `4952965974` (Total 8 confirmed berths):\n" +
                   "  - Narayandas Sahu: Berth 32 (Side Upper)\n" +
                   "  - Hemlata: Berth 34 (Middle)\n" +
                   "  - Yogank: Berth 35 (Upper)\n" +
                   "  - Mohardas Sahu: Berth 37 (Middle)\n" +
                   "  - Rukhamanibai: Berth 38 (Upper)\n" +
                   "  - Savitri Sahu: Berth 40 (Side Upper)\n" +
                   "  - Jigar Sahu: Berth 42 (Middle)\n" +
                   "  - Jaydev Sahu: Berth 43 (Upper)\n\n" +
                   "3️⃣ *TRAIN 16780 (RMM ➔ TPTY)*: 30-Oct 16:55 | PNR `4864111470` | Coach B1 Berth 8 (Rukmanibai) & RAC 1 (Mohardas)\n" +
                   "4️⃣ *TRAIN 22620 (RU ➔ RJN)*: 01-Nov 21:20 | PNR `4951463160` | Coach M2 Berth 33 Lower (Narayandas)";
        }

        // 2. Cab / Taxi / Transit Rates & How to Travel
        if (lower.contains("cab") || lower.contains("taxi") || lower.contains("auto") || lower.contains("fare") || lower.contains("btn_cabs") || lower.contains("transit")) {
            return "🚕 *KERALA CABS, TAXI RATES & TRANSIT GUIDE*\n\n" +
                   "1️⃣ *Aluva Station ➔ Munnar (110 km | 3.5 - 4 hrs):*\n" +
                   "• Recommended: Pre-book private 12-14 seater Tempo Traveller (~₹4,500 - ₹5,500) or Innova (~₹3,200 - ₹3,800).\n" +
                   "• Where to find: Right outside Aluva Station exit (Prepaid Taxi Counter available).\n\n" +
                   "2️⃣ *Munnar ➔ Thekkady (90 km | 3 hrs):*\n" +
                   "• Taxi Rate: ~₹2,500 - ₹3,500.\n" +
                   "• KSRTC Bus option: Departs Munnar Central Bus Stand hourly (₹110/person).\n\n" +
                   "3️⃣ *Thekkady ➔ Alleppey (135 km | 4 hrs):*\n" +
                   "• Taxi Rate: ~₹3,500 - ₹4,500.\n\n" +
                   "4️⃣ *Alleppey ➔ Varkala:*\n" +
                   "• Best way: *Train 12075 Jan Shatabdi* (06:35 PM ➔ 08:13 PM, 1h 38m, ₹100-325) or Ernad Express (05:48 PM).\n\n" +
                   "💡 *Tip:* Ola & Uber work in Kochi/Aluva/Trivandrum. For hill stations (Munnar/Thekkady), local drivers or pre-booked private tempo is best!";
        }

        // 3. Malayalam Language & Useful Phrases
        if (lower.contains("malayalam") || lower.contains("language") || lower.contains("translate") || lower.contains("speak") || lower.contains("btn_malayalam")) {
            return "🗣️ *ESSENTIAL KERALA (MALAYALAM) TRANSLATION GUIDE*\n\n" +
                   "• *Hello / Greetings:* നമസ്കാരം (Namaskāram)\n" +
                   "• *Thank you:* നന്ദി (Nanni)\n" +
                   "• *How much is this?:* ഇതിന് എത്രയാ വില? (Ithinu ethraya vila?)\n" +
                   "• *Please take us to...:* ഞങ്ങളെ ... കൊണ്ടുപോകുമോ? (Njangale ... kondupōkumō?)\n" +
                   "• *Where is the taxi/auto stand?:* ടാക്സി/ഓട്ടോ സ്റ്റാൻഡ് എവിടെയാണ്? (Taxi/Auto stand evideyānu?)\n" +
                   "• *Is this purely vegetarian?:* ഇത് ശുദ്ധ സസ്യാഹാരമാണോ? (Ithu shuddha sasyāhāramānō?)\n" +
                   "• *Drinking water please:* കുടിവെള്ളം തരുമോ (Kudivellam tharumō?)\n" +
                   "• *Where is the temple?:* അമ്പലം എവിടെയാണ്? (Ambalam evideyānu?)\n" +
                   "• *Please help:* ദയവായി സഹായിക്കൂ (Dayavāyi sahāyikkū)\n\n" +
                   "💡 *Note:* Most drivers, shopkeepers & hotel staff in Kerala understand English and basic Hindi!";
        }

        // 4. Temple Darshan & Dress Codes
        if (lower.contains("temple") || lower.contains("darshan") || lower.contains("dress") || lower.contains("balaji") || lower.contains("btn_temples")) {
            return "🛕 *SACRED TEMPLE GUIDELINES & DRESS CODES*\n\n" +
                   "1️⃣ *Sree Padmanabhaswamy Temple (Trivandrum - 28 Oct):*\n" +
                   "• ⚠️ *Strict Traditional Dress Code:* \n" +
                   "  - Men: Must wear *Dhoti / Mundu* only (bare chest, no shirts, no trousers).\n" +
                   "  - Women: Saree or traditional pavada/half-saree. (No jeans, leggings, or western clothes).\n" +
                   "• Lockers available outside for phones & bags.\n\n" +
                   "2️⃣ *Ramanathaswamy Temple (Rameswaram - 29 Oct):*\n" +
                   "• 🌊 *22 Sacred Wells (Theertham) Ritual:*\n" +
                   "  1. Start with sea bath at Agni Theertham.\n" +
                   "  2. Enter temple complex for bath under 22 holy wells (temple staff pours buckets).\n" +
                   "  3. Change into dry clothes (carry 1 set of extra dry clothes & towel).\n" +
                   "  4. Proceed for Jyotirlinga Darshan. No leather belts/wallets inside!\n\n" +
                   "3️⃣ *Tirumala Balaji (Tirupati - 31 Oct):*\n" +
                   "• Men: Dhoti with Angavastram or Kurta-Pyjama. Women: Saree or Chudidar with Dupatta.\n" +
                   "• 🥮 *Laddus:* 1 Free laddu given on darshan ticket; extra laddus available at counters for ₹50 each!";
        }

        // 5. Elephant Junction (Thekkady)
        if (lower.contains("elephant") || lower.contains("thekkady") || lower.contains("btn_elephant")) {
            return "🐘 *ELEPHANT JUNCTION (THEKKADY / KUMILY)*\n\n" +
                   "📍 *Full Address:* Murukkady P.O., Thekkady, Kumily, Kerala 685535\n" +
                   "🗺️ *Google Maps Pin:* https://maps.app.goo.gl/Wyr4eo4hNsErXmBD7\n" +
                   "📞 *Contact Numbers:* +91 94471 63342 | +91 4869 224142\n" +
                   "💰 *Selected Package:* ₹1,500 package (\"1500/- wala hi karna\") — elephant bath/shower, feeding, and photo-ops.\n\n" +
                   "⚠️ *CRUCIAL NAVIGATION WARNING:* \n" +
                   "Must enter via *Laksham Veedu Colony Road* directly in front of the *KSEB Substation*.\n" +
                   "❌ DO NOT enter 'Karimuttam's Elephant Junction' on the main road — that is a completely different place!";
        }

        // 6. Alleppey Boating & Stay
        if (lower.contains("alleppey") || lower.contains("reji") || lower.contains("boat") || lower.contains("kayak") || lower.contains("shikara") || lower.contains("btn_alleppey")) {
            return "🛶 *ALLEPPEY (ALAPPUZHA) STAY & BOATING*\n\n" +
                   "🏡 *Stay Accommodation:* Private Suite Stay\n" +
                   "🔗 Airbnb Link: https://www.airbnb.com/l/oEZFBOte\n\n" +
                   "⛵ *Boat Guide:* Mr. Reji (Saved: 'Mr Reji Kayaking Alleppey')\n" +
                   "✅ *Ride Preference:* Full exclusive Shikara boat ride booked for the entire family.\n" +
                   "🚫 *STRICT RULE:* Strictly NO houseboat stay.\n\n" +
                   "🏖️ *Places to Explore:* Marari Beach (sunset), Mullakkal Rajarajeswari Temple, and historic Mount Carmel Church.";
        }

        // 7. Munnar & Ripple Zipline
        if (lower.contains("munnar") || lower.contains("zipline") || lower.contains("ripple") || lower.contains("btn_munnar")) {
            return "🧗 *MUNNAR ADVENTURES & SIGHTSEEING*\n\n" +
                   "1️⃣ *Ripple Adventures Zipline:*\n" +
                   "📍 Address: Panniyarkutty, Munnar, Kerala 685565\n" +
                   "🗺️ Google Map: https://maps.google.com/maps?q=9.97966480255127%2C77.06129455566406&z=17&hl=en\n" +
                   "🌐 Info: https://wandersmunnar.com/ripple-adventures-munnar/\n" +
                   "💰 Price: ~₹500/person (Waterfall zipline, roller coaster zipline, giant swing).\n" +
                   "🕒 Timings: 8:30 AM – 5:30 PM\n\n" +
                   "2️⃣ *KSRTC Double Decker Sightseeing Bus:*\n" +
                   "🚌 3-Hour evening panoramic tour (departs Munnar ~4:30 PM)\n" +
                   "🌐 Booking: https://onlineksrtcswift.com/\n\n" +
                   "3️⃣ *Places:* Mattupetty Dam, Echo Point, Tea Gardens & spice markets.";
        }

        // 8. Varkala Cliffs & Beaches
        if (lower.contains("varkala") || lower.contains("cliff") || lower.contains("btn_varkala")) {
            return "🏖️ *VARKALA CLIFFS & COASTAL EXPERIENCES*\n\n" +
                   "🚆 *Intermediate Train (27-Oct):*\n" +
                   "• Train 12075 TVC Jan Shatabdi (ALLP 06:35 PM ➔ VAK 08:13 PM, 1h 38m)\n" +
                   "• Alternate: 16605 Ernad Express (ALLP 05:48 PM ➔ VAK 07:57 PM)\n\n" +
                   "🌅 *Attractions:* \n" +
                   "• *Varkala Cliff:* Arabian sea panoramic view & sunset cafes.\n" +
                   "• *Black Sand Beach:* Unique geological shoreline.\n" +
                   "• *Paravoor & Mangroves:* Backwater & Mangrove Island Kayaking (~30 mins away).";
        }

        // 9. Master Schedule
        if (lower.contains("schedule") || lower.contains("itinerary") || lower.contains("plan") || lower.contains("btn_schedule")) {
            return "🌴 *COMPLETE 13-DAY TOUR CALENDAR*\n\n" +
                   "• *21-Oct:* Vadodara (BRC) departure on Tr 20932 (05:06 AM)\n" +
                   "• *22-Oct:* Aluva arrival (11:18 AM) ➔ Scenic drive to Munnar ➔ KSRTC Double Decker bus\n" +
                   "• *23-Oct:* Munnar Tea Estates & Ripple Adventures Zipline\n" +
                   "• *24-Oct:* Munnar ➔ Thekkady (Kumily) ➔ Elephant Junction (₹1500 package)\n" +
                   "• *25-Oct:* Thekkady ➔ Alleppey Airbnb Suite ➔ Mr. Reji Shikara full boat ride\n" +
                   "• *26-Oct:* Mullakkal Temple, Mt. Carmel Church & Marari Beach\n" +
                   "• *27-Oct:* Train 12075 Jan Shatabdi to Varkala (06:35 PM ➔ 08:13 PM)\n" +
                   "• *28-Oct:* Varkala Cliff ➔ Trivandrum Padmanabhaswamy ➔ Amritha Exp (20:30) to RMM\n" +
                   "• *29-Oct:* Rameswaram arrival (12:15 PM) ➔ 22 Holy Wells bath & Jyotirlinga\n" +
                   "• *30-Oct:* Dhanushkodi & Ram Setu Point ➔ Tr 16780 (16:55) to Tirupati\n" +
                   "• *31-Oct:* Tirupati arrival (10:10 AM) ➔ Tirumala Balaji Darshan\n" +
                   "• *01-Nov:* Padmavathi Temple ➔ Train 22620 from Renigunta (21:20)\n" +
                   "• *02-Nov:* Arrive home at Raj Nandgaon (05:48 PM) 🎉";
        }

        return "🤖 *Trip Assistant:* I received: \"" + q + "\".\n\n" +
               "Ask me anything! For example: *berths*, *cabs*, *malayalam phrases*, *elephant junction*, *temples*, *zipline*, *alleppey*, *varkala*, or *schedule*!";
    }

    private static void sendMainMenu(long chatId) {
        String welcome = "🌴 *Welcome to Kerala & South India Master Travel Guide!* 🌴\n\n" +
                         "I cover 100% of your trip: Berths, Cabs & Taxi fares, Maps, Malayalam translations, Temple dress codes & timings.\n\n" +
                         "👇 *Tap a button below or type any question:*";

        String inlineKeyboard = "{\"inline_keyboard\":[" +
            "[{\"text\":\"🚆 Train Berths & PNR\",\"callback_data\":\"btn_berths\"},{\"text\":\"🚕 Cabs & Taxi Rates\",\"callback_data\":\"btn_cabs\"}]," +
            "[{\"text\":\"🗣️ Malayalam Phrases\",\"callback_data\":\"btn_malayalam\"},{\"text\":\"🛕 Temple Rules & Laddus\",\"callback_data\":\"btn_temples\"}]," +
            "[{\"text\":\"🐘 Elephant Junction\",\"callback_data\":\"btn_elephant\"},{\"text\":\"🛶 Alleppey Boating & Stay\",\"callback_data\":\"btn_alleppey\"}]," +
            "[{\"text\":\"🧗 Munnar Zipline\",\"callback_data\":\"btn_munnar\"},{\"text\":\"🏖️ Varkala Cliffs\",\"callback_data\":\"btn_varkala\"}]," +
            "[{\"text\":\"📅 Full 13-Day Plan\",\"callback_data\":\"btn_schedule\"}]" +
            "]}";

        sendPostMessage(chatId, welcome, inlineKeyboard);
    }

    private static void sendMessage(long chatId, String text, boolean withMenuButton) {
        String inlineKeyboard = withMenuButton ? 
            "{\"inline_keyboard\":[[{\"text\":\"🔙 Back to Menu\",\"callback_data\":\"btn_menu\"}]]}" : null;
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
