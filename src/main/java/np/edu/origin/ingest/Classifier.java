package np.edu.origin.ingest;

import np.edu.origin.engine.Vocabulary;
import np.edu.origin.model.Lookup;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Fills in the domain and the project type when a file does not say. It reads the column text first
 * when there is one, then counts keyword hits in the project's own words. Anything it cannot place
 * goes to the general bucket (Community and Social, Web App) rather than to a wrong specific one.
 */
public final class Classifier {

    private static final Map<String, List<String>> DOMAIN_WORDS = Map.ofEntries(
            Map.entry("EDU", List.of("student", "school", "college", "exam", "result", "course", "class", "teacher",
                    "library", "attendance", "quiz", "learning", "hostel", "timetable", "lecture", "syllabus", "campus")),
            Map.entry("HLT", List.of("hospital", "patient", "doctor", "clinic", "medicine", "pharmacy", "health", "blood",
                    "appointment", "vaccine", "disease", "diabetes", "ambulance")),
            Map.entry("AGR", List.of("farmer", "farm", "crop", "agriculture", "irrigation", "soil", "livestock", "harvest",
                    "fertilizer", "dairy")),
            Map.entry("TOU", List.of("hotel", "tourist", "tourism", "trekking", "travel", "homestay", "guide", "resort")),
            Map.entry("FIN", List.of("bank", "loan", "cooperative", "savings", "payment", "expense", "insurance", "finance",
                    "wallet", "budget")),
            Map.entry("COM", List.of("shop", "commerce", "inventory", "billing", "store", "retail", "restaurant", "order",
                    "delivery", "sales", "customer")),
            Map.entry("TRN", List.of("bus", "ride", "parking", "traffic", "route", "vehicle", "fuel", "transport", "taxi",
                    "driver")),
            Map.entry("GOV", List.of("ward", "citizen", "complaint", "voting", "election", "tax", "municipality",
                    "government", "certificate")),
            Map.entry("ENV", List.of("waste", "disaster", "landslide", "flood", "earthquake", "pollution", "weather",
                    "water", "environment", "climate")),
            Map.entry("JOB", List.of("job", "recruitment", "internship", "skill", "freelance", "career", "resume",
                    "employment", "hiring")),
            Map.entry("SOC", List.of("volunteer", "donation", "event", "community", "chat", "social", "charity", "ngo")),
            Map.entry("MED", List.of("music", "movie", "news", "streaming", "sports", "game", "entertainment", "video",
                    "podcast", "radio")));

    /** Checked in this order; the first type whose words appear wins. */
    private static final List<Map.Entry<String, List<String>>> TYPE_WORDS = List.of(
            Map.entry("Game", List.of("game", "gaming", "unity")),
            Map.entry("IoT / Hardware", List.of("iot", "arduino", "sensor", "raspberry", "esp32", "esp8266", "hardware", "rfid")),
            Map.entry("Mobile App", List.of("mobile", "android", "ios", "flutter", "kotlin")),
            Map.entry("Desktop App", List.of("desktop", "swing", "javafx", "awt", "winforms")),
            Map.entry("Data & Analytics", List.of("analytics", "prediction", "dataset", "machine", "forecast", "dashboard")),
            Map.entry("Web App", List.of("web", "website", "php", "html", "servlet", "jsp", "laravel", "django")));

    private final List<Lookup> domains;
    private final List<Lookup> types;

    public Classifier(List<Lookup> domains, List<Lookup> types) {
        this.domains = domains;
        this.types = types;
    }

    public int domainId(String columnText, String projectText) {
        String t = columnText == null ? "" : columnText.strip().toLowerCase(Locale.ROOT);
        if (!t.isEmpty()) {
            for (Lookup d : domains) {
                String name = d.getName().toLowerCase(Locale.ROOT);
                String first = name.split("[ &]+")[0];
                if (name.equals(t) || d.getCode().equalsIgnoreCase(t) || name.contains(t) || t.contains(first)) return d.getId();
            }
        }
        Set<String> words = Vocabulary.words(projectText);
        Lookup best = null;
        int bestHits = 0;
        for (Lookup d : domains) {
            int hits = 0;
            for (String kw : DOMAIN_WORDS.getOrDefault(d.getCode(), List.of())) if (hasWord(words, kw)) hits++;
            if (hits > bestHits) {
                best = d;
                bestHits = hits;
            }
        }
        return best != null ? best.getId() : byCode(domains, "SOC").getId();
    }

    public int typeId(String columnText, String projectText) {
        Integer fromColumn = typeFromWords(Vocabulary.words(columnText));
        if (fromColumn != null) return fromColumn;
        Integer fromText = typeFromWords(Vocabulary.words(projectText));
        if (fromText != null) return fromText;
        return types.stream().filter(t -> t.getName().equals("Web App")).findFirst().orElse(types.get(0)).getId();
    }

    private Integer typeFromWords(Set<String> words) {
        for (var entry : TYPE_WORDS) {
            for (String kw : entry.getValue()) {
                if (hasWord(words, kw)) {
                    return types.stream().filter(t -> t.getName().equals(entry.getKey())).map(Lookup::getId).findFirst().orElse(null);
                }
            }
        }
        return null;
    }

    /** A word matches a keyword when it is equal to it or, for longer keywords, starts with it ("farmers"). */
    private static boolean hasWord(Set<String> words, String kw) {
        for (String w : words) {
            if (w.equals(kw) || (kw.length() >= 4 && w.startsWith(kw))) return true;
        }
        return false;
    }

    private static Lookup byCode(List<Lookup> list, String code) {
        return list.stream().filter(l -> code.equals(l.getCode())).findFirst().orElse(list.get(0));
    }
}
