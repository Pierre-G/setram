/**
 * Created by Pierre Grandjean on 28/12/2016.
 */

import static org.apache.commons.lang3.StringUtils.isNumeric;
import static org.apache.commons.lang3.StringUtils.remove;
import static org.apache.commons.lang3.time.DateUtils.addDays;
import static spark.Spark.port;
import static spark.Spark.get;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.DomText;
import com.gargoylesoftware.htmlunit.util.NameValuePair;

import com.mongodb.*;
import org.apache.commons.lang3.time.DateUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import org.jsoup.select.Elements;

import org.neo4j.cypher.internal.frontend.v2_3.ast.functions.Str;
import org.neo4j.graphalgo.*;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.io.fs.FileUtils;

import java.io.File;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;

import java.io.IOException;


public class Setram {

    static GraphDatabaseService graphDb;
    static DBCollection timetableCollection;
    static DBCollection pathsCollection;

    private static final RelationshipType NEXT = RelationshipType.withName( "NEXT" );
    private static final RelationshipType STOPS_AT = RelationshipType.withName( "STOPS_AT" );

    private static final Label STOP = Label.label( "Stop" );
    private static final Label BUS = Label.label( "Bus" );
    private static final Label TRAM = Label.label( "Tram" );

    private static String previousRelationship;


    public static void main(String[] args) throws IOException {

        // We open MongoDB connection, delete Neo4J files (not sure if needed), launch Neo4J database and load cypher file

        timetableCollection = connectToMongoDB("timetable");
        pathsCollection = connectToMongoDB("paths");

        clearNeo4jDb();

        File data = new File("data");
        graphDb = new GraphDatabaseFactory().newEmbeddedDatabase(data);
        registerShutdownHook( graphDb );

        initNeo4jDb();

        // We parameterize SparkJava

        port(Integer.valueOf(System.getenv("PORT")));

        try {
            get("/", (req, res) -> display() );

            get("/timetable/", (req, res) -> addToTimetable() );

            get("/test/", (req, res) -> recordPathsBetweenGivenStops("Cimetière", "Jaurès-Pavillon") );
            get("/record/", (req, res) -> recordPathsBetweenAllStops() );
            get("/resume-record/", (req, res) -> resumeRecordPathsBetweenAllStops("Guy Bouriat", "Saint-Martin") );

            get("/read/", (req, res) -> readNeo4jDb() );
            get("/donotsleep/", (req, res) -> donotsleep() );

        } catch(Exception e){
            e.printStackTrace();
        }

    }


    private static String recordPathsBetweenGivenStops(String departure, String arrival) {

        // Delete all documents from pathsCollection Using blank BasicDBObject
        BasicDBObject voidDocument = new BasicDBObject();
        pathsCollection.remove(voidDocument);

        try ( Transaction tx = graphDb.beginTx() )
        {
            Node departureNode = graphDb.findNode(STOP, "name", departure);
            Node arrivalNode = graphDb.findNode(STOP, "name", arrival);
            recordPathsBetweenTwoStops(departureNode, arrivalNode);
            tx.success();
        } catch (Exception e) {
            System.out.println(e);
        }

        return "OK";
    }

    private static String resumeRecordPathsBetweenAllStops(String wantedDepartureNodeString, String wantedArrivalNodeString) {

        Thread t = new Thread() {
            public void run() {

                boolean flagFirstWantedDepartureNode = false;
                boolean flagFirstWantedArrivalNode = false;

                try ( Transaction tx = graphDb.beginTx() )
                {
                    // Find all Stops
                    ResourceIterator<Node> departureNodes = graphDb.findNodes(STOP);
                    while( departureNodes.hasNext() )
                    {
                        Node departureNode = departureNodes.next();
                        if (!flagFirstWantedDepartureNode && departureNode.getProperty("name").toString().equals(wantedDepartureNodeString)) {
                            flagFirstWantedDepartureNode = true;
                        }

                        if (flagFirstWantedDepartureNode) {
                            ResourceIterator<Node> arrivalNodes = graphDb.findNodes(STOP);
                            while (arrivalNodes.hasNext()) {
                                Node arrivalNode = arrivalNodes.next();
                                if (!flagFirstWantedArrivalNode && arrivalNode.getProperty("name").toString().equals(wantedArrivalNodeString)) {
                                    flagFirstWantedArrivalNode = true;
                                }
                                if (flagFirstWantedArrivalNode) {
                                    if (!departureNode.getProperty("name").toString().equals(arrivalNode.getProperty("name").toString())) {
                                        recordPathsBetweenTwoStops(departureNode, arrivalNode);
                                    }
                                }
                            }
                        }
                    }
                    tx.success();
                } catch (Exception e) {
                    System.out.println(e);
                }

            }
        };
        t.start();

        return "Recording needed paths. This may take a while.";

    }

    private static String recordPathsBetweenAllStops() {

        Thread t = new Thread() {
            public void run() {

                try {
                    Thread.sleep(60000);    // To let the user stop the application if he launch the command by error
                } catch (Exception e) {
                    System.out.println(e);
                }

                // Delete all documents from pathsCollection using blank BasicDBObject
                BasicDBObject voidDocument = new BasicDBObject();
                pathsCollection.remove(voidDocument);

                try ( Transaction tx = graphDb.beginTx() )
                {
                    // Find all Stops
                    ResourceIterator<Node> departureNodes = graphDb.findNodes(STOP);
                    while( departureNodes.hasNext() )
                    {
                        Node departureNode = departureNodes.next();

                        ResourceIterator<Node> arrivalNodes = graphDb.findNodes(STOP);
                        while (arrivalNodes.hasNext()) {
                            Node arrivalNode = arrivalNodes.next();
                            if (!departureNode.getProperty("name").toString().equals(arrivalNode.getProperty("name").toString())) {
                                recordPathsBetweenTwoStops(departureNode, arrivalNode);
                            }
                        }
                    }
                    tx.success();
                } catch (Exception e) {
                    System.out.println(e);
                }

            }
        };
        t.start();

        Thread t2 = new Thread() {
            public void run() {
                try {   // Count down
                    System.out.println("60 seconds before MongoDB data erasing");
                    Thread.sleep(10000);
                    System.out.println("50 seconds before MongoDB data erasing");
                    Thread.sleep(10000);
                    System.out.println("40 seconds before MongoDB data erasing");
                    Thread.sleep(10000);
                    System.out.println("30 seconds before MongoDB data erasing");
                    Thread.sleep(10000);
                    System.out.println("20 seconds before MongoDB data erasing");
                    Thread.sleep(10000);
                    System.out.println("10 seconds before MongoDB data erasing");
                } catch (Exception e) {
                    System.out.println(e);
                }
            }
        };
        t2.start();

        return "Recording needed paths. This may take a while.\nYou have one minute to stop the app if you make an error.";

    }


    private static void recordPathsBetweenTwoStops(Node departureNode, Node arrivalNode) {

        List<BasicDBObject> documents = new ArrayList<>();
/*
        try ( Transaction tx = graphDb.beginTx() )
        {
*/
            System.out.println("Searching paths between " + departureNode.getProperty("name") + " and " + arrivalNode.getProperty("name"));

            previousRelationship = "";

            WeightedPath minimumPath = GraphAlgoFactory.dijkstra(PathExpanders.forTypeAndDirection( NEXT, Direction.OUTGOING ), "cost").findSinglePath(departureNode, arrivalNode);

            PathFinder<Path> finder = GraphAlgoFactory.allSimplePaths(
                    PathExpanders.forTypeAndDirection( NEXT, Direction.OUTGOING ), minimumPath.length() + 4);
            Iterable<Path> paths = finder.findAllPaths(departureNode, arrivalNode);

            Integer i = 0;
            for (Path path : paths) {
                Iterable<Relationship> relationships = path.relationships();
                Map<Integer, String> directions = new HashMap<>();
                Map<Integer, String> stopsWithDirections = new HashMap<>();

                Integer r = 0;
                String previousRelationship = "";
                Integer relationshipsVariationNumber = 0;
                for (Relationship relationship : relationships) {
                    if ( ! relationship.getProperty("for").toString().equals(previousRelationship) ) {
                        directions.put(r, relationship.getProperty("for").toString());
                        stopsWithDirections.put(r, relationship.getStartNode().getProperty("name").toString());
                        previousRelationship = relationship.getProperty("for").toString();
                        relationshipsVariationNumber++;
                    }
                    r++;
                }

                if (relationshipsVariationNumber < 4) { // We record paths with minimum changes
                    BasicDBObject document = new BasicDBObject();
                    document.put("departure", departureNode.getProperty("name").toString());
                    document.put("arrival", arrivalNode.getProperty("name").toString());
                    document.put("path length", path.length());
                    document.put("changes", relationshipsVariationNumber - 1);
                    document.put("directions", directions.toString());
                    document.put("stops with directions", stopsWithDirections.toString());
                    documents.add(document);
                }

                i++;
            }

            System.out.println("\t" + i + " path(s) found");
/*
            tx.success();
        } catch (Exception e) {
            System.out.println(e);
        }
*/
        if (!documents.isEmpty()) {
            pathsCollection.insert(documents);
        }

    }



    private static String donotsleep() {
        return "I'm awake!";
    }


    private static String initNeo4jDb() throws IOException {

        System.out.println("Init - Start");
        String query = "";
        query = readFile("Neo4j-data.cypher", Charset.defaultCharset());
        try ( Transaction tx = graphDb.beginTx() )
        {
            graphDb.execute(query);
            tx.success();
        } catch (Exception e) {
            System.out.println(e);
        }
        System.out.println("Init - End");
        return "OK";
    }

    private static String readFile(String path, Charset encoding)
            throws IOException
    {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, encoding);
    }

    private static void clearNeo4jDb() {
        try {
            FileUtils.deleteRecursively(new File("data"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String readNeo4jDb() {
        try ( Transaction tx = graphDb.beginTx() )
        {
            // Find all Stops
            ResourceIterator<Node> stops = graphDb.findNodes(STOP);
            System.out.println( "Stops:" );
            while( stops.hasNext() )
            {
                Node stop = stops.next();
                System.out.println( "\t" + stop.getProperty( "name" ) );
            }
            tx.success();
        } catch (Exception e) {
            System.out.println(e);
        }


        return "OK";
    }




    private static void registerShutdownHook( final GraphDatabaseService graphDb )
    {
        // Registers a shutdown hook for the Neo4j instance so that it
        // shuts down nicely when the VM exits (even if you "Ctrl-C" the
        // running application).
        Runtime.getRuntime().addShutdownHook( new Thread()
        {
            @Override
            public void run()
            {
                graphDb.shutdown();
            }
        } );
    }



    private static String addMinutesToNextStop(String busLine, String departure, String arrival) {

        try {

            BasicDBObject andQuery = new BasicDBObject();
            List<BasicDBObject> obj1 = new ArrayList<BasicDBObject>();
            obj1.add(new BasicDBObject("busLine", busLine));
            obj1.add(new BasicDBObject("stop", departure));
            andQuery.put("$and", obj1);

            DBObject doc1 = timetableCollection.findOne(andQuery);
            Date date1 = (Date) doc1.get("stopDate");

            BasicDBObject dateQuery = new BasicDBObject();
            dateQuery.put("stopDate", new BasicDBObject("$gte", date1));

            BasicDBObject andQuery2 = new BasicDBObject();
            List<BasicDBObject> obj2 = new ArrayList<BasicDBObject>();
            obj2.add(new BasicDBObject("busLine", busLine));
            obj2.add(new BasicDBObject("stop", arrival));
            obj2.add(dateQuery);
            andQuery2.put("$and", obj2);

            DBObject doc2 = timetableCollection.findOne(andQuery2);
            if (doc2 != null) {
                Date date2 = (Date) doc2.get("stopDate");
                Long minutesToNextStop = Duration.between(date1.toInstant(), date2.toInstant()).toMinutes();
                return "    Date1 : " + date1 + " ;    Date2 : " + date2 + " ;    Durée : " + minutesToNextStop;
            }
            else {
                if (busLine.equals("Tram 1 dir. Antarès-MMArena") && departure.equals("Guetteloup - Pôle Santé Sud") && arrival.equals("Antarès-MMArena")) {
                    return "    Date1 : " + date1 + " ;    Date2 : " + DateUtils.addMinutes(date1, 4) + " ;    Durée : " + 4 ;
                }
                if (busLine.equals("Tram 1 dir. Université") && departure.equals("Campus-Ribay") && arrival.equals("Université")) {
                    return "    Date1 : " + date1 + " ;    Date2 : " + DateUtils.addMinutes(date1, 2) + " ;    Durée : " + 2 ;
                }
                if (busLine.equals("Bus 3 dir. Gazonfier") && departure.equals("Charbonnière") && arrival.equals("Gazonfier")) {
                    return "    Date1 : " + date1 + " ;    Date2 : " + DateUtils.addMinutes(date1, 2) + " ;    Durée : " + 2 ;
                }
                if (busLine.equals("Bus 3 dir. Oasis - Centre des Expositions") && departure.equals("Jules Védrines") && arrival.equals("Oasis - Centre des Expositions")) {
                    return "    Date1 : " + date1 + " ;    Date2 : " + DateUtils.addMinutes(date1, 2) + " ;    Durée : " + 2 ;
                }
                if (busLine.equals("Bus 12 dir. République") && departure.equals("Comptes Du Maine - Office du Tourisme") && arrival.equals("République")) {
                    return "    Date1 : " + date1 + " ;    Date2 : " + DateUtils.addMinutes(date1, 3) + " ;    Durée : " + 3 ;
                }
                if (busLine.equals("Bus 12 dir. Saint-Martin") && departure.equals("Pontlieue Jean Mac") && arrival.equals("Saint-Martin")) {
                    return "    Date1 : " + date1 + " ;    Date2 : " + DateUtils.addMinutes(date1, 2) + " ;    Durée : " + 2 ;
                }
                if (busLine.equals("Bus 23 dir. République") && departure.equals("Comptes Du Maine - Office du Tourisme") && arrival.equals("République")) {
                    return "    Date1 : " + date1 + " ;    Date2 : " + DateUtils.addMinutes(date1, 3) + " ;    Durée : " + 3 ;
                }
                if (busLine.equals("Bus 23 dir. Yvré-l'Évêque") && departure.equals("Collège Pasteur") && arrival.equals("Yvré-L'Évêque")) {
                    return "    Date1 : " + date1 + " ;    Date2 : " + DateUtils.addMinutes(date1, 1) + " ;    Durée : " + 1 ;
                }
                if (busLine.equals("Bus 25 dir. République") && departure.equals("Comptes Du Maine - Office du Tourisme") && arrival.equals("République")) {
                    return "    Date1 : " + date1 + " ;    Date2 : " + DateUtils.addMinutes(date1, 3) + " ;    Durée : " + 3 ;
                }
                else {
                    return "NO ARRIVAL TIME! You have to manually set minutesToNextStop";
                }
            }


        } catch (Exception e) {
            return e.toString();
        }
    }


/*
    private static String test2() {

        try {
            // We open MongoDB and Neo4J connections

            DBCollection timetableCollection = connectToMongoDB("timetable");

            Driver driver = GraphDatabase.driver( System.getenv("GRAPHENEDB_BOLT_URL"), AuthTokens.basic( System.getenv("GRAPHENEDB_BOLT_USER"), System.getenv("GRAPHENEDB_BOLT_PASSWORD") ) );
            Session session = driver.session();

            StatementResult resultBusLines = session.run("MATCH (n) " +
                    "WHERE n:Bus OR n:Tram " +
                    "RETURN n.name AS name");
            while (resultBusLines.hasNext()) {
                Record busLineRecord = resultBusLines.next();
                StatementResult resultStops = session.run("MATCH (n {name: {busLine}})-[STOPS_AT]->(s)" +
                                "RETURN s.name AS name",
                        parameters("busLine", busLineRecord.get("name").asString()));
                System.out.println("======= busLine : " + busLineRecord.get("name") + " =======");
                while (resultStops.hasNext()) {
                    Record stopRecord = resultStops.next();
                    StatementResult resultNextStops = session.run("MATCH (:Stop {name: {stop}})-[:NEXT {for: {busLine}}]->(s)" +
                                    "RETURN s.name AS name",
                            parameters("stop", stopRecord.get("name").asString(), "busLine", busLineRecord.get("name").asString()));
                    while (resultNextStops.hasNext()) {
                        Record nextStopRecord = resultNextStops.next();
                        System.out.println("departure : " + stopRecord.get("name") + " ;    arrival : " + nextStopRecord.get("name"));
                        String tempVar = addMinutesToNextStop(busLineRecord.get("name").asString(), stopRecord.get("name").asString(), nextStopRecord.get("name").asString(), timetableCollection);
                        System.out.println(tempVar);
                    }
                }
            }

            return "End of processing";

        } catch (Exception e) {
            return e.toString();
        }

    }

    private static String test() {
//        return findTravelTime("Gare SNCF", "Saint-Martin", new Date());
    }

*/

    private static String findTravelTime(String departure, String arrival, Date departureHour) {

        Map<String,String> map = new HashMap<String,String>();
        map.put("Université", "342|Universite|Le%20Mans|||437035|2337548|678!0;677!0;680!0;679!0;");
        map.put("République", "318|R%E9publique|Le%20Mans|||440372|2335962|623!0;624!0;622!0;621!0;620!0;");
        map.put("Gare SNCF", "206|Gare%20SNCF|Le%20Mans|||440083|2335103|393!0;390!0;391!0;392!0;394!0;395!0;");
        map.put("Saint-Martin", "321|Saint%20Martin|Le%20Mans|||440979|2333984|632!0;629!0;631!0;630!0;");

        String departureStopAreaName = "";
        String arrivalStopAreaName = "";
        if (map.containsKey(departure)) {
            departureStopAreaName = map.get(departure);
        }
        if (map.containsKey(arrival)) {
            arrivalStopAreaName = map.get(arrival);
        }

        String urlPart1 = "http://lemans.prod.navitia.com/Navitia/ITI_2_AnswersList.asp?DPoint=StopArea|";
        String urlPart2 = "&APoint=StopArea|";
        String urlPart3 = "&Date="; // Date format : yyyy|MM|d
        String urlPart4 = "&Time=1|"; // Time format : h|m
        String urlPart5 = "&Criteria=2||&Mode=|1|Pas%20de%20pr%E9f%E9rences|&HangDistance=400&Extend=1&TotOuTard=tot&DateFinBases=&DateMajBases=";

        return "departureStopAreaName: " + departureStopAreaName + " ; arrivalStopAreaName: " + arrivalStopAreaName;

    }



    private static String display() {

        WebClient client = new WebClient();
        client.getOptions().setCssEnabled(false);
        client.getOptions().setJavaScriptEnabled(false);

        Date now = new Date();

        SimpleDateFormat isoDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        isoDateFormat.setTimeZone(TimeZone.getTimeZone("Europe/Paris"));

        SimpleDateFormat justDayDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        justDayDateFormat.setTimeZone(TimeZone.getTimeZone("Europe/Paris"));
        String day = justDayDateFormat.format(now);


        String htmlTextToDisplay = "ERROR in display() method";


        htmlTextToDisplay = "<p>Prochains départ de Sécurité Sociale : <br/>";

        ArrayList<String> busLines = new ArrayList<>();
        busLines.add("Bus 3 dir. Oasis - Centre des Expositions");
        busLines.add("Bus 12 dir. République");
        busLines.add("Bus 23 dir. République");
        busLines.add("Bus 25 dir. République");

        ArrayList<NameValuePair> realMinutesBeforeDepartures = readRealMinutesBeforeDepartures("Sécurité Sociale", busLines, now, isoDateFormat, day);

        for (NameValuePair realMinutesBeforeDeparture : realMinutesBeforeDepartures) {
            htmlTextToDisplay = htmlTextToDisplay + realMinutesBeforeDeparture.getValue() + " minutes (" + realMinutesBeforeDeparture.getName() + ")<br/>";
        }


        htmlTextToDisplay = htmlTextToDisplay + "</p><p>Prochains départ de Californie : <br/>";

        ArrayList<String> busLines2 = new ArrayList<>();
        busLines2.add("Bus 12 dir. République");
        busLines2.add("Bus 12 dir. Saint-Martin");

        ArrayList<NameValuePair> realMinutesBeforeDepartures2 = readRealMinutesBeforeDepartures("Californie", busLines2, now, isoDateFormat, day);

        for (NameValuePair realMinutesBeforeDeparture : realMinutesBeforeDepartures2) {
            htmlTextToDisplay = htmlTextToDisplay + realMinutesBeforeDeparture.getValue() + " minutes (" + realMinutesBeforeDeparture.getName() + ")<br/>";
        }

        htmlTextToDisplay = htmlTextToDisplay + "</p>";


        htmlTextToDisplay = htmlTextToDisplay + "</p><p>Prochains départ d'Université : <br/>";

        ArrayList<String> busLines3 = new ArrayList<>();
        busLines3.add("Tram 1 dir. Antarès-MMArena");

        ArrayList<NameValuePair> realMinutesBeforeDepartures3 = readRealMinutesBeforeDepartures("Université", busLines3, now, isoDateFormat, day);

        for (NameValuePair realMinutesBeforeDeparture : realMinutesBeforeDepartures3) {
            htmlTextToDisplay = htmlTextToDisplay + realMinutesBeforeDeparture.getValue() + " minutes (" + realMinutesBeforeDeparture.getName() + ")<br/>";
        }

        htmlTextToDisplay = htmlTextToDisplay + "</p>";


        return htmlTextToDisplay;

    }


    private static ArrayList<NameValuePair> readRealMinutesBeforeDepartures(String stopArea, ArrayList<String> busLines, Date now, SimpleDateFormat isoDateFormat, String day) {

        ArrayList<NameValuePair> realMinutesBeforeDepartures = new ArrayList<>();
        NameValuePair params[] = new NameValuePair[3];
        params[0] = new NameValuePair("a", "refresh");

        try ( Transaction tx = graphDb.beginTx() )
        {
            for (String busLine : busLines) {

                Node busNode = graphDb.findNode(BUS, "name", busLine);
                if (busNode == null) {
                    busNode = graphDb.findNode(TRAM, "name", busLine);
                }
                Node stopNode = graphDb.findNode(STOP, "name", stopArea);

                Iterable<Relationship> relationships = busNode.getRelationships(STOPS_AT, Direction.OUTGOING);
                for( Relationship relationship : relationships )
                {
                    if (relationship.getOtherNode(busNode).equals(stopNode)) {
                        params[1] = new NameValuePair("refs", relationship.getProperty("refs").toString());
                        params[2] = new NameValuePair("ran", relationship.getProperty("ran").toString());

                    }
                }

                addRealMinutesBeforeDepartures(busLine, realMinutesBeforeDepartures, params, now, isoDateFormat, day);

            }
            tx.success();

        } catch (Exception e) {
            System.out.println(e);
        }

        // Sorting
        Collections.sort(realMinutesBeforeDepartures, comp);

        return realMinutesBeforeDepartures;
    }


    private static String addToTimetable() throws IOException, ParseException {

        System.out.println("addToTimetable - Start");

        // We first delete all MongoDB records no more needed

        Date date = DateUtils.addDays(new Date(), -1);

        BasicDBObject dateQuery = new BasicDBObject();
        dateQuery.put("stopDate", new BasicDBObject("$lt", date));

        timetableCollection.remove(dateQuery);
        System.out.println("\t Removing of old MongoDB records - Success");

        // We build needed strings

        Date now = new Date();
        Date tomorrow = addDays(now, 1);

        SimpleDateFormat isoDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        isoDateFormat.setTimeZone(TimeZone.getTimeZone("Europe/Paris"));

        SimpleDateFormat justDayDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        justDayDateFormat.setTimeZone(TimeZone.getTimeZone("Europe/Paris"));
        String day = justDayDateFormat.format(tomorrow);

        SimpleDateFormat justDayDateFormatForSearchUrl = new SimpleDateFormat("yyyy|MM|d");
        justDayDateFormatForSearchUrl.setTimeZone(TimeZone.getTimeZone("Europe/Paris"));
        String dayForSearchUrl = justDayDateFormatForSearchUrl.format(tomorrow);

        String searchUrlPart1BeforeBusLine = "http://lemans.prod.navitia.com/Navitia/HP_4_HP.asp?Network=1|STA1|Setram&Line=";
        String searchUrlPart2BeforeStop = "&StopArea=";
        String searchUrlPart3BeforeDate = "&Date=";

        // We read Neo4J data and launch the process that get the site's data and record in MongoDB

        try ( Transaction tx = graphDb.beginTx() ) {

            ResourceIterator<Node> busNodes = graphDb.findNodes(BUS);
            writeTimetable(busNodes, isoDateFormat, day, dayForSearchUrl, searchUrlPart1BeforeBusLine,searchUrlPart2BeforeStop, searchUrlPart3BeforeDate);

            ResourceIterator<Node> tramNodes = graphDb.findNodes(TRAM);
            writeTimetable(tramNodes, isoDateFormat, day, dayForSearchUrl, searchUrlPart1BeforeBusLine,searchUrlPart2BeforeStop, searchUrlPart3BeforeDate);

            tx.success();

        } catch (Exception e) {
            System.out.println(e);
        }

        System.out.println("addToTimetable - End");
        return "Capture terminée";

    }

    private static void writeTimetable(ResourceIterator<Node> busOrTramNodes, SimpleDateFormat isoDateFormat, String day, String dayForSearchUrl, String searchUrlPart1BeforeBusLine,String searchUrlPart2BeforeStop, String searchUrlPart3BeforeDate) {

        try ( Transaction tx = graphDb.beginTx() ) {
            while (busOrTramNodes.hasNext()) {
                Node busNode = busOrTramNodes.next();
                System.out.println("writeTimetable - " + busNode.getProperty("name").toString());
                Iterable<Relationship> relationships = busNode.getRelationships(STOPS_AT, Direction.OUTGOING);
                for (Relationship relationship : relationships) {
                    Node stopNode = relationship.getOtherNode(busNode);
                    System.out.println("\t Stop : " + stopNode.getProperty("name").toString());
                    String searchUrlWithoutDate = searchUrlPart1BeforeBusLine + busNode.getProperty("stringForTimetable").toString() + searchUrlPart2BeforeStop + stopNode.getProperty("stringForTimetable").toString() + searchUrlPart3BeforeDate;
                    captureTimetable(busNode.getProperty("name").toString(), stopNode.getProperty("name").toString(), searchUrlWithoutDate, isoDateFormat, day, dayForSearchUrl);
                    Thread.sleep(1000); // To be nice
                }
            }
            tx.success();

        } catch (Exception e) {
            System.out.println(e);
        }
    }


    private static ArrayList<NameValuePair> addRealMinutesBeforeDepartures(String busLine, ArrayList<NameValuePair> realMinutesBeforeDepartures, NameValuePair params[], Date now, SimpleDateFormat isoDateFormat, String day) {

        try {
            final WebClient webClient = new WebClient();

            // Instead of requesting the page directly we create a WebRequestSettings object
            WebRequest requestSettings = new WebRequest(
                    new URL("http://dev.actigraph.fr/actipages/setram/module/mobile/pivk/relais.html.php"), HttpMethod.POST);

            // Then we set the request parameters
            requestSettings.setRequestParameters(new ArrayList());
            requestSettings.getRequestParameters().add(params[0]);
            requestSettings.getRequestParameters().add(params[1]);
            requestSettings.getRequestParameters().add(params[2]);
            // Finally, we can get the page
            HtmlPage realTimePage = webClient.getPage(requestSettings);

            List<DomText> realTimes = (List<DomText>)realTimePage.getByXPath("//li[@id]/text()");
            for (DomText realTime : realTimes) {
                String toWorkString = realTime.toString();
                if ( !toWorkString.contains("minutes") && !toWorkString.contains("H") ) { // For strings that do not mention any time, it's now
                    realMinutesBeforeDepartures.add(new NameValuePair(busLine, Integer.toString(0)));
                }
                else if (toWorkString.contains("H")) { // For strings like "Passage suivant à 12 H 34 pour REPUBLIQUE"
                    String hourString = toWorkString.substring(toWorkString.indexOf("à ") + 2, toWorkString.indexOf(" pour"));
                    // We have to convert a String (HH H mm) into a Date
                    String[] parts = hourString.split(" H ");
                    String hours = parts[0];
                    String minutes = parts[1];
                    Date realDate = isoDateFormat.parse(day + "T" + hours + ":" + minutes + ":00");

                    long timeDifference = realDate.getTime() - now.getTime();
                    realMinutesBeforeDepartures.add(new NameValuePair(busLine, Integer.toString((int)timeDifference/(1000*60))));

                }
                else {
                    String requiredString = toWorkString.substring(toWorkString.indexOf("dans ") + 5, toWorkString.indexOf(" minutes"));
                    realMinutesBeforeDepartures.add(new NameValuePair(busLine, requiredString));
//                    realMinutesBeforeDepartures.add(busLine, Integer.parseInt(requiredString));
                }
            }

        } catch(Exception e){
            e.printStackTrace();
        }
        return realMinutesBeforeDepartures;

    }


    private static Comparator<NameValuePair> comp = new Comparator<NameValuePair>() {
        @Override
        public int compare(NameValuePair p1, NameValuePair p2) {
            // return p1.getValue().compareTo(p2.getValue());
            return Integer.parseInt(p1.getValue()) - Integer.parseInt(p2.getValue());
        }
    };


    private static void captureTimetable(String busLine, String stop, String searchUrlWithoutDate, SimpleDateFormat isoDateFormat, String day, String dayForSearchUrl) throws IOException, ParseException {

        Document doc = Jsoup.connect(searchUrlWithoutDate + dayForSearchUrl).get();

        boolean existingColumn = true;
        Integer i = 0;
        while (existingColumn) {
            Elements el = doc.select("table.standard td:eq(" + i + ")");
            existingColumn = !el.isEmpty();
            boolean firstRow = true;
            String hour = "";
            for (Element e : el) {  // For each element of the column
                if (firstRow) {     // We set the hour, displayed by the first row
                    hour = remove(e.text(), "h");
                    firstRow = false;
                }
                else {  // We record the dates
                    if (isNumeric(e.text())) {  // The empty table cells contain white space character
                        Date date = isoDateFormat.parse(day + "T" + hour + ":" + e.text() + ":00");
                        BasicDBObject document = new BasicDBObject();
                        document.put("busLine", busLine);
                        document.put("stop", stop);
                        document.put("stopDate", date);
                        timetableCollection.update(document, document, true, false); // Upsert allows to insert only if it doesn't already exists
                    }
                }
            }
            i++;
        }
    }


    private static DBCollection connectToMongoDB(String collectionName) {
        DBCollection collection = null;
        try {
            MongoClientURI uri = new MongoClientURI(System.getenv("MONGODB_URI"));
            MongoClient mongoClient = new MongoClient(uri);
            DB db = mongoClient.getDB(System.getenv("MONGODB_DATABASE"));
            collection = db.getCollection(collectionName);
        } catch (MongoException e) {
            e.printStackTrace();
        }
        return collection;
    }


}
