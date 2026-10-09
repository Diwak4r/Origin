package np.edu.origin.engine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static np.edu.origin.engine.TestVocab.idea;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimilarityEngineTest {

    private static final int WEB = 1;
    private static final int MOBILE = 2;
    private static final int IOT = 4;

    private final SimilarityEngine engine = new SimilarityEngine();

    @Test
    void weightsAddUpToOne() {
        double sum = engine.scorers().stream().mapToDouble(Scorer::weight).sum();
        assertEquals(1.0, sum, 1e-9);
    }

    @Test
    void jaccardIsIntersectionOverUnion() {
        assertEquals(0.5, Scorer.jaccard(Set.of("a", "b", "c"), Set.of("b", "c", "d", "a", "e", "f")), 1e-9);
        assertEquals(0.0, Scorer.jaccard(Set.of(), Set.of("a")), 1e-9);
        assertEquals(1.0, Scorer.jaccard(Set.of("x"), Set.of("x")), 1e-9);
    }

    @Test
    void identicalIdeasScoreOne() {
        Candidate a = idea(1, "Library System", WEB, "library, booking", "Students borrow books and reserve them.");
        Candidate b = idea(2, "Library System", WEB, "library, booking", "Students borrow books and reserve them.");
        assertEquals(1.0, engine.compare(a, b).ruleScore(), 1e-9);
    }

    @Test
    void renamingDoesNotHideAnOldIdea() {
        Candidate old = idea(1, "Library Management System", WEB, "library, student, notification",
                "Records books, members and issue and return dates and lists overdue borrowers.");
        Candidate renamed = idea(0, "Book Depot Portal", WEB, "books, lending, alerts",
                "Students borrow books from the depot and get alerts before the return date.");
        Breakdown b = engine.compare(renamed, old);
        assertTrue(b.tagScore() >= 0.66, "synonyms should fold books/lending into library: " + b.tagScore());
        assertTrue(b.ruleScore() >= Band.SIMILAR_FROM, "a renamed library system must not look original: " + b.ruleScore());
    }

    @Test
    void rankReturnsClosestFirstAndRespectsTheLimit() {
        Candidate me = idea(0, "Clinic Queue", WEB, "hospital, notification", "Patients wait for the doctor.");
        List<Candidate> pool = List.of(
                idea(1, "Farm Market", MOBILE, "farmer, market-price", "Farmers sell vegetables."),
                idea(2, "Hospital Token", WEB, "hospital, notification", "Patients wait for their token."),
                idea(3, "Blood Finder", MOBILE, "blood, notification", "Find donors."));
        List<Breakdown> top = engine.rank(me, pool, 2);
        assertEquals(2, top.size());
        assertEquals(2, top.get(0).other().id());
        assertTrue(top.get(0).ruleScore() >= top.get(1).ruleScore());
    }

    @Test
    void blendIsSixtyFortyAndIgnoresMissingConcept() {
        assertEquals(0.5, Breakdown.blend(0.5, null), 1e-9);
        assertEquals(0.6 * 0.5 + 0.4 * 1.0, Breakdown.blend(0.5, 1.0), 1e-9);
    }

    @Test
    void bandsMatchTheThresholds() {
        assertEquals(Band.ORIGINAL, Band.of(0.3499, 50));
        assertEquals(Band.SIMILAR, Band.of(0.35, 50));
        assertEquals(Band.DUPLICATE, Band.of(0.60, 50));
        assertEquals(Band.LOW_CONFIDENCE, Band.of(0.95, 14));
    }

    /**
     * Fifteen pairs labelled by the team before the engine was run on them.
     * The engine must agree with the human band on at least 12 of 15 (80%).
     */
    @Test
    void agreesWithHumanLabelsOnAtLeastTwelveOfFifteen() {
        List<Case> cases = labelledPairs();
        int agree = 0;
        StringBuilder log = new StringBuilder();
        for (Case c : cases) {
            double s = engine.compare(c.a(), c.b()).ruleScore();
            Band engineBand = Band.of(s, 100);
            if (engineBand == c.human()) agree++;
            log.append(String.format("%-28s %-9s engine %-9s %.2f%n", c.a().title(), c.human(), engineBand, s));
        }
        System.out.print(log);
        System.out.println("Band agreement: " + agree + "/15");
        assertTrue(agree >= 12, "Engine agreed with the human label on only " + agree + " of 15 cases\n" + log);
    }

    private record Case(Candidate a, Candidate b, Band human) { }

    private static List<Case> labelledPairs() {
        return List.of(
                new Case(idea(0, "Library Management System", WEB, "library, student", "Issue and return books, fines for late return."),
                         idea(1, "College Library Portal", WEB, "library, student", "Students issue and return books and pay late fines."), Band.DUPLICATE),
                new Case(idea(0, "Book Lending Desk", WEB, "books, lending, reservation", "Reserve books online and collect them at the desk."),
                         idea(1, "Online Library Book Lending Portal", WEB, "library, booking, student", "Students reserve books online and get a reminder."), Band.DUPLICATE),
                new Case(idea(0, "Hospital Management", WEB, "hospital, billing, appointment", "Patients, appointments and bills."),
                         idea(1, "Clinic Billing Desk", WEB, "clinic, invoice", "Clinic staff print patient bills."), Band.SIMILAR),
                new Case(idea(0, "Pharmacy Stock Tracker", 3, "medicine, inventory, billing", "Track medicine stock and expiry, print bills."),
                         idea(1, "Pharmacy Inventory and Billing", 3, "medicine, inventory, billing", "Medicine stock, expiry dates and customer bills."), Band.DUPLICATE),
                new Case(idea(0, "Blood Donor Finder", MOBILE, "blood, notification", "Find nearby donors of a blood group."),
                         idea(1, "Blood Request Relay", MOBILE, "blood-donation, sms, notification", "Relay urgent requests to donors by SMS."), Band.SIMILAR),
                new Case(idea(0, "Kalimati Price SMS", MOBILE, "market-price, farmer, sms", "Daily wholesale prices sent to farmers by SMS."),
                         idea(1, "Hospital Token System", WEB, "hospital, notification", "Digital OPD tokens."), Band.ORIGINAL),
                new Case(idea(0, "Smart Irrigation", IOT, "sensor, irrigation", "Soil moisture sensor switches the pump."),
                         idea(1, "Air Quality Station", IOT, "sensor, air-quality", "PM2.5 sensor posts readings to a dashboard."), Band.SIMILAR),
                new Case(idea(0, "Attendance by QR", MOBILE, "attendance, student", "Students scan a code to mark presence."),
                         idea(1, "Attendance System", WEB, "attendance, student", "Teachers mark daily attendance per subject."), Band.SIMILAR),
                new Case(idea(0, "Futsal Matchmaker", MOBILE, "sports, rating, chat", "Pairs futsal teams of similar level."),
                         idea(1, "Library Management System", WEB, "library, student", "Issue and return books."), Band.ORIGINAL),
                new Case(idea(0, "Ward Letter Tracker", WEB, "ward-office, document", "Track recommendation letters."),
                         idea(1, "Municipal Complaint Portal", WEB, "complaint, citizen-service", "Citizens lodge complaints."), Band.ORIGINAL),
                new Case(idea(0, "Online Shop", WEB, "e-commerce, payment, delivery", "Customers buy products and track delivery."),
                         idea(1, "Online Shopping System", WEB, "e-commerce, payment, delivery", "Browse products, pay online and track delivery."), Band.DUPLICATE),
                new Case(idea(0, "Expense Tracker", MOBILE, "expense, analytics", "Record daily expenses and see charts."),
                         idea(1, "Cooperative Savings", 3, "cooperative, savings, loan", "Member savings and loans."), Band.ORIGINAL),
                new Case(idea(0, "Bus Route Finder", MOBILE, "bus, route, map", "Find which bus passes your stop."),
                         idea(1, "Bus Ticket Booking", WEB, "bus, booking, payment", "Book long-route bus seats."), Band.ORIGINAL),
                new Case(idea(0, "Hotel Booking Portal", WEB, "hotel, reservation, payment", "Guests book rooms and pay online."),
                         idea(1, "Online Hotel Reservation Portal", WEB, "hotel, booking, payment", "Guests search rooms, book and pay online."), Band.DUPLICATE),
                new Case(idea(0, "Diabetes Risk Predictor", 5, "prediction, patient", "Predict diabetes risk from a public dataset."),
                         idea(1, "Heart Risk Forecast", 5, "forecast, patient, analytics", "Forecast heart disease risk with regression."), Band.SIMILAR));
    }

    // ---------- v3 upgrade: rare-term weights, containment, gated type, coverage, story ----------

    @Test
    void rareTermsWeighMoreThanCommonOnes() {
        TermStats stats = new TermStats(java.util.Map.of("library", 9, "banker", 0));
        assertEquals(1.0, stats.weight("banker"), 1e-9);
        assertEquals(1.0 / (1.0 + Math.log(10)), stats.weight("library"), 1e-9);
        assertTrue(stats.weight("banker") > stats.weight("library"));
    }

    @Test
    void smallIdeaInsideABigProjectIsNotHalfSimilar() {
        Set<String> mine = Set.of("library");
        Set<String> theirs = Set.of("library", "student", "notification", "billing");
        // plain Jaccard says 0.25; containment says the whole idea already exists: 0.8 * 0.25 + 0.2 * 1.0
        assertEquals(0.4, Overlap.of(mine, theirs, TermStats.NONE), 1e-9);
    }

    @Test
    void overlapIsOneForIdenticalSetsAndZeroForDisjointOnes() {
        assertEquals(1.0, Overlap.of(Set.of("a", "b"), Set.of("a", "b"), TermStats.NONE), 1e-9);
        assertEquals(0.0, Overlap.of(Set.of("a"), Set.of("b"), TermStats.NONE), 1e-9);
        assertEquals(0.0, Overlap.of(Set.of(), Set.of("b"), TermStats.NONE), 1e-9);
    }

    @Test
    void typeBonusCountsOnlyWhenTopicsOverlap() {
        Candidate a = idea(0, "Futsal Matchmaker", WEB, "sports, rating", "Pairs futsal teams.");
        Candidate unrelatedSameType = idea(1, "Pharmacy Stock", WEB, "medicine, inventory", "Tracks medicine expiry.");
        assertEquals(0.0, engine.compare(a, unrelatedSameType).ruleScore(), 1e-9);
        Candidate related = idea(2, "Futsal Ranking", WEB, "sports, rating", "Ranks futsal teams.");
        Candidate relatedOtherType = idea(3, "Futsal Ranking", MOBILE, "sports, rating", "Ranks futsal teams.");
        assertTrue(engine.compare(a, related).ruleScore() > engine.compare(a, relatedOtherType).ruleScore());
    }

    @Test
    void coverageShowsAMashUpOfTwoOldProjects() {
        Candidate mashUp = idea(0, "Library Attendance Kiosk", WEB, "library, attendance", "Mark attendance at the library desk.");
        Candidate lib = idea(1, "Library System", WEB, "library, student", "Issue books.");
        Candidate att = idea(2, "Attendance System", WEB, "attendance, student", "Mark attendance.");
        SimilarityEngine e = SimilarityEngine.learnFrom(List.of(lib, att));
        List<Breakdown> top = e.rank(mashUp, List.of(lib, att), 3);
        Coverage c = e.coverage(mashUp, top, 3);
        assertEquals(2, c.projects());
        assertTrue(c.share() > 0.5, "most of the idea is covered: " + c.share());
        assertTrue(c.sentence().contains("2 projects together"));
        assertEquals("", e.coverage(mashUp, List.of(), 3).sentence());
    }

    @Test
    void storySplitsSharedYoursAndTheirs() {
        Candidate mine = idea(0, "Book Depot Portal", WEB, "library, qr-code", "Scan a code to borrow.");
        Candidate old = idea(1, "Library System", WEB, "library, student", "Issue books.");
        Story s = engine.story(mine, old);
        assertTrue(s.shared().contains("library"));
        assertTrue(s.yoursOnly().contains("qr-code"));
        assertTrue(s.theirsOnly().contains("student"));
        String fix = s.fix("Library System", 62);
        assertTrue(fix.startsWith("You match Library System (62%) on "));
        assertTrue(fix.contains("Your real difference is "));
        assertTrue(!fix.contains("\u2014"), "no em dashes in user-facing text");
    }

    @Test
    void storyListsTagsBeforePlainWords() {
        Candidate mine = idea(0, "Water Tanker Queue", WEB, "water, scheduling", "Households claim aging tankers every morning.");
        Candidate old = idea(1, "Hospital Queue", WEB, "queue, hospital", "Patients wait for tokens.");
        Story s = engine.story(mine, old);
        assertTrue(s.yoursOnly().indexOf("water") < s.yoursOnly().indexOf("aging"), "tags first: " + s.yoursOnly());
        assertTrue(s.yoursOnly().indexOf("scheduling") < s.yoursOnly().indexOf("claim"), "tags first: " + s.yoursOnly());
    }

    @Test
    void storyFixToldWhenNothingIsNew() {
        Candidate mine = idea(0, "Library Lite", WEB, "library", "Books.");
        Candidate old = idea(1, "Library System", WEB, "library, student", "Books and students.");
        Story s = new Story(List.of("library"), List.of(), List.of("student"));
        assertTrue(s.fix("Library System", 70).contains("add nothing it lacks"));
        assertTrue(new Story(List.of(), List.of("x"), List.of()).fix("T", 3).contains("Nothing to change"));
    }

    /** The old v2 formula, kept here only so the new one has something to be measured against. */
    private static double legacy(Candidate a, Candidate b) {
        return 0.45 * Scorer.jaccard(a.tags(), b.tags()) + 0.35 * Scorer.jaccard(a.keywords(), b.keywords())
                + 0.20 * (a.typeId() == b.typeId() ? 1.0 : 0.0);
    }

    /**
     * Twenty-five pairs labelled by hand BEFORE the new formula was run: the 15 from the v2 test
     * plus 10 that stress small ideas, renames and name-only collisions. The new formula ships
     * only if it agrees with the human label at least as often as the old one.
     */
    @Test
    void newFormulaAgreesAtLeastAsOftenAsTheOldOne() {
        List<Case> cases = allCases();
        List<Candidate> everyone = new java.util.ArrayList<>();
        for (Case c : cases) { everyone.add(c.a()); everyone.add(c.b()); }
        SimilarityEngine learned = SimilarityEngine.learnFrom(everyone);

        int old = 0, plain = 0, withStats = 0;
        for (Case c : cases) {
            if (Band.of(legacy(c.a(), c.b()), 100) == c.human()) old++;
            if (Band.of(engine.compare(c.a(), c.b()).ruleScore(), 100) == c.human()) plain++;
            if (Band.of(learned.compare(c.a(), c.b()).ruleScore(), 100) == c.human()) withStats++;
        }
        System.out.println("Agreement on " + cases.size() + " pairs: old " + old + ", new " + plain
                + ", new with term weights " + withStats);
        assertTrue(withStats >= old, "new formula agreed on " + withStats + " but the old one on " + old);
    }

    /** The 15 original pairs plus 10 that stress small ideas, renames and name-only collisions. */
    private static List<Case> allCases() {
        List<Case> cases = new java.util.ArrayList<>(labelledPairs());
        cases.addAll(List.of(
                new Case(idea(0, "Fine Calculator", WEB, "library", "Calculates the late fine for returned books."),
                         idea(1, "Library Management System", WEB, "library, student, notification", "Issue and return books, fines for late return."), Band.SIMILAR),
                new Case(idea(0, "Library Attendance Kiosk", WEB, "library, attendance", "Mark attendance at the library desk."),
                         idea(1, "Library Management System", WEB, "library, student", "Issue and return books."), Band.SIMILAR),
                new Case(idea(0, "Bus Ticket Booking", WEB, "bus, booking, payment", "Book long-route bus seats and pay."),
                         idea(1, "Hotel Booking Portal", WEB, "hotel, booking, payment", "Guests book rooms and pay online."), Band.ORIGINAL),
                new Case(idea(0, "Futsal Matchmaker", WEB, "sports, rating", "Pairs futsal teams of similar level."),
                         idea(1, "Pharmacy Stock Tracker", WEB, "medicine, inventory", "Track medicine stock and expiry."), Band.ORIGINAL),
                new Case(idea(0, "Medicine Depot Tracker", 3, "drug, warehouse, bill", "Track medicine stock and expiry, print bills."),
                         idea(1, "Pharmacy Stock Tracker", 3, "medicine, inventory, billing", "Track medicine stock and expiry, print bills."), Band.DUPLICATE),
                new Case(idea(0, "SMS Blood Alerts", MOBILE, "blood, sms, alert", "Text donors when a group is needed."),
                         idea(1, "Blood Donor Finder", MOBILE, "blood, notification", "Find nearby donors of a blood group."), Band.SIMILAR),
                new Case(idea(0, "Crop Yield Forecast", WEB, "forecast, farmer", "Forecast the harvest from rainfall data."),
                         idea(1, "Diabetes Risk Predictor", 5, "prediction, patient", "Predict diabetes risk from a public dataset."), Band.ORIGINAL),
                new Case(idea(0, "Hospital Suite with Lab Reports", WEB, "hospital, billing, appointment, patient", "Appointments, bills and lab reports."),
                         idea(1, "Clinic Billing Desk", WEB, "clinic, invoice", "Clinic staff print patient bills."), Band.SIMILAR),
                new Case(idea(0, "Library of Things", WEB, "sharing, community, tool", "Neighbours lend tools and ladders to each other."),
                         idea(1, "Library Management System", WEB, "library, student", "Issue and return books."), Band.ORIGINAL),
                new Case(idea(0, "Exam Seat Planner", WEB, "exam, seating", "Assigns students to exam halls."),
                         idea(1, "Exam Result Portal", WEB, "exam, result, student", "Publishes results online."), Band.ORIGINAL)));
        return cases;
    }
}
