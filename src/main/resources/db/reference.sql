-- Reference data: domains, project types, controlled tag vocabulary and synonyms.
-- INSERT IGNORE keeps this file safe to re-run.

INSERT IGNORE INTO semesters (code, label, is_active) VALUES ('2026-FALL', 'Fall 2026 (BIT 4th Semester)', TRUE);

INSERT IGNORE INTO domains (id, name, short_name) VALUES
 (1,'Education','EDU'), (2,'Health','HLT'), (3,'Agriculture','AGR'), (4,'Tourism & Travel','TOU'),
 (5,'Finance & Banking','FIN'), (6,'Commerce & Retail','COM'), (7,'Transport & Mobility','TRN'),
 (8,'Governance & Civic','GOV'), (9,'Environment & Disaster','ENV'), (10,'Employment & Skills','JOB'),
 (11,'Community & Social','SOC'), (12,'Media & Entertainment','MED');

INSERT IGNORE INTO project_types (id, name, effort) VALUES
 (1,'Web App',2), (2,'Mobile App',3), (3,'Desktop App',2), (4,'IoT / Hardware',4), (5,'Data & Analytics',3), (6,'Game',3);

INSERT IGNORE INTO tags (name) VALUES
 ('student'),('exam'),('result'),('attendance'),('library'),('course'),('e-learning'),('quiz'),('timetable'),('hostel'),
 ('hospital'),('patient'),('appointment'),('pharmacy'),('medicine'),('blood-donation'),('telemedicine'),('health-record'),('vaccination'),
 ('farmer'),('crop'),('market-price'),('livestock'),('irrigation'),('soil'),
 ('hotel'),('trekking'),('tourism'),('travel'),('booking'),('homestay'),('guide'),
 ('banking'),('loan'),('cooperative'),('savings'),('payment'),('expense'),('stock-market'),('insurance'),
 ('e-commerce'),('inventory'),('billing'),('shop'),('delivery'),('restaurant'),('food-order'),('pos'),
 ('bus'),('ride-sharing'),('parking'),('traffic'),('route'),('vehicle'),('fuel'),
 ('ward-office'),('citizen-service'),('complaint'),('voting'),('tax'),('document'),
 ('water'),('waste'),('disaster'),('landslide'),('flood'),('earthquake'),('air-quality'),('energy'),('weather'),
 ('job'),('recruitment'),('skill'),('internship'),('foreign-employment'),('freelancing'),
 ('volunteer'),('donation'),('event'),('social-network'),('chat'),('lost-and-found'),
 ('music'),('movie'),('news'),('streaming'),('sports'),('game'),
 ('gps'),('sms'),('qr-code'),('rfid'),('sensor'),('dashboard'),('prediction'),('recommendation'),('chatbot'),('map'),
 ('analytics'),('notification'),('rating'),('queue'),('verification'),('scheduling');

-- Synonyms: renaming an idea cannot hide it. alias -> canonical tag
INSERT IGNORE INTO tag_synonyms (alias, tag_id)
SELECT s.alias, t.id FROM (
 SELECT 'book' alias,'library' tag UNION ALL SELECT 'books','library' UNION ALL SELECT 'lending','library'
 UNION ALL SELECT 'borrowing','library' UNION ALL SELECT 'borrow','library' UNION ALL SELECT 'catalog','library'
 UNION ALL SELECT 'catalogue','library' UNION ALL SELECT 'presence','attendance' UNION ALL SELECT 'roll-call','attendance'
 UNION ALL SELECT 'clinic','hospital' UNION ALL SELECT 'doctor','appointment' UNION ALL SELECT 'drug','medicine'
 UNION ALL SELECT 'drugs','medicine' UNION ALL SELECT 'medicines','medicine' UNION ALL SELECT 'blood','blood-donation'
 UNION ALL SELECT 'blood-bank','blood-donation' UNION ALL SELECT 'donor','blood-donation' UNION ALL SELECT 'farming','farmer'
 UNION ALL SELECT 'agriculture','farmer' UNION ALL SELECT 'crops','crop' UNION ALL SELECT 'harvest','crop'
 UNION ALL SELECT 'lodge','hotel' UNION ALL SELECT 'trek','trekking' UNION ALL SELECT 'reservation','booking'
 UNION ALL SELECT 'reserve','booking' UNION ALL SELECT 'bank','banking' UNION ALL SELECT 'sahakari','cooperative'
 UNION ALL SELECT 'saving','savings' UNION ALL SELECT 'esewa','payment' UNION ALL SELECT 'khalti','payment'
 UNION ALL SELECT 'wallet','payment' UNION ALL SELECT 'ecommerce','e-commerce' UNION ALL SELECT 'shopping','e-commerce'
 UNION ALL SELECT 'online-shopping','e-commerce' UNION ALL SELECT 'stock','inventory' UNION ALL SELECT 'warehouse','inventory'
 UNION ALL SELECT 'invoice','billing' UNION ALL SELECT 'bill','billing' UNION ALL SELECT 'food','food-order'
 UNION ALL SELECT 'canteen','restaurant' UNION ALL SELECT 'ride','ride-sharing' UNION ALL SELECT 'carpool','ride-sharing'
 UNION ALL SELECT 'taxi','ride-sharing' UNION ALL SELECT 'municipality','ward-office' UNION ALL SELECT 'ward','ward-office'
 UNION ALL SELECT 'complaints','complaint' UNION ALL SELECT 'grievance','complaint' UNION ALL SELECT 'election','voting'
 UNION ALL SELECT 'garbage','waste' UNION ALL SELECT 'trash','waste' UNION ALL SELECT 'recycling','waste'
 UNION ALL SELECT 'pollution','air-quality' UNION ALL SELECT 'jobs','job' UNION ALL SELECT 'vacancy','recruitment'
 UNION ALL SELECT 'hiring','recruitment' UNION ALL SELECT 'manpower','foreign-employment' UNION ALL SELECT 'charity','donation'
 UNION ALL SELECT 'events','event' UNION ALL SELECT 'message','chat' UNION ALL SELECT 'messaging','chat'
 UNION ALL SELECT 'film','movie' UNION ALL SELECT 'films','movie' UNION ALL SELECT 'iot','sensor'
 UNION ALL SELECT 'sensors','sensor' UNION ALL SELECT 'forecast','prediction' UNION ALL SELECT 'predict','prediction'
 UNION ALL SELECT 'bot','chatbot' UNION ALL SELECT 'maps','map' UNION ALL SELECT 'location','gps'
 UNION ALL SELECT 'tracking','gps' UNION ALL SELECT 'alerts','notification' UNION ALL SELECT 'alert','notification'
 UNION ALL SELECT 'reviews','rating' UNION ALL SELECT 'review','rating' UNION ALL SELECT 'elearning','e-learning'
 UNION ALL SELECT 'lms','e-learning' UNION ALL SELECT 'online-class','e-learning' UNION ALL SELECT 'marks','result'
 UNION ALL SELECT 'grades','result' UNION ALL SELECT 'routine','timetable' UNION ALL SELECT 'schedule','scheduling'
 UNION ALL SELECT 'token','queue' UNION ALL SELECT 'waiting','queue' UNION ALL SELECT 'microbus','bus'
 UNION ALL SELECT 'sajha','bus' UNION ALL SELECT 'vaccine','vaccination' UNION ALL SELECT 'immunization','vaccination'
) s JOIN tags t ON t.name = s.tag;
