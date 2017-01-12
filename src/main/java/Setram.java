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

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.MongoException;
import org.jsoup.select.Elements;

import org.neo4j.driver.v1.*;
import static org.neo4j.driver.v1.Values.parameters;

import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import java.io.IOException;


public class Setram {
    public static void main(String[] args) {

        port(Integer.valueOf(System.getenv("PORT")));

        try {

            get("/", (req, res) -> display() );
            get("/timetable/", (req, res) -> addToTimetable() );
            get("/test/", (req, res) -> test() );
            get("/donotsleep/", (req, res) -> donotsleep() );

        } catch(Exception e){
            e.printStackTrace();
        }

    }

    private static String donotsleep() {
        return "I'm awake!";
    }

    private static String test() {
        return findTravelTime("Gare SNCF", "Saint-Martin", new Date());
    }

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

        Driver driver = GraphDatabase.driver( System.getenv("GRAPHENEDB_BOLT_URL"), AuthTokens.basic( System.getenv("GRAPHENEDB_BOLT_USER"), System.getenv("GRAPHENEDB_BOLT_PASSWORD") ) );
        Session session = driver.session();

        for (String busLine : busLines) {
            StatementResult result = session.run("MATCH (b {name: {busLine}})-[r:STOPS_AT]->(s:Stop {name: {stopArea}}) " +
                    "RETURN r.refs AS refs, r.ran AS ran",
                    parameters("busLine", busLine, "stopArea", stopArea));
            while ( result.hasNext() )
            {
                Record record = result.next();
                params[1] = new NameValuePair("refs", record.get("refs").asString());
                params[2] = new NameValuePair("ran", record.get("ran").asString());
            }
            addRealMinutesBeforeDepartures(busLine, realMinutesBeforeDepartures, params, now, isoDateFormat, day);
        }

        driver.close();
        session.close();

        // Sorting
        Collections.sort(realMinutesBeforeDepartures, comp);

        return realMinutesBeforeDepartures;
    }


    private static String addToTimetable() throws IOException, ParseException {

        // We open MongoDB and Neo4J connections

        DBCollection myCollection = connectToMongoDB("timetable");

        Driver driver = GraphDatabase.driver( System.getenv("GRAPHENEDB_BOLT_URL"), AuthTokens.basic( System.getenv("GRAPHENEDB_BOLT_USER"), System.getenv("GRAPHENEDB_BOLT_PASSWORD") ) );
        Session session = driver.session();

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

        // We read Neo4J data and launch the process that get the site's data and record in MondoDB

        try {
            StatementResult resultBusLines = session.run("MATCH (n) " +
                    "WHERE n:Bus OR n:Tram " +
                    "RETURN n.name AS name, n.stringForTimetable AS stringForTimetable");
            while (resultBusLines.hasNext()) {
                Record busLineRecord = resultBusLines.next();
                StatementResult resultStops = session.run("MATCH (n {name: {busLine}})-[STOPS_AT]->(s)" +
                                "RETURN s.name AS name, s.stringForTimetable AS stringForTimetable",
                        parameters("busLine", busLineRecord.get("name").asString()));
                while (resultStops.hasNext()) {
                    Record stopRecord = resultStops.next();
                    String searchUrlWithoutDate = searchUrlPart1BeforeBusLine + busLineRecord.get("stringForTimetable").asString() + searchUrlPart2BeforeStop + stopRecord.get("stringForTimetable").asString() + searchUrlPart3BeforeDate;
                    captureTimetable(busLineRecord.get("name").asString(), stopRecord.get("name").asString(), searchUrlWithoutDate, isoDateFormat, day, dayForSearchUrl, myCollection);
                    Thread.sleep(1000); // To be nice
                }
            }
        } catch (Exception e) {
            System.out.println("ERROR: " + e);
        }

        driver.close();
        session.close();

        return "Capture terminée";

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


    private static void captureTimetable(String busLine, String stop, String searchUrlWithoutDate, SimpleDateFormat isoDateFormat, String day, String dayForSearchUrl, DBCollection myCollection) throws IOException, ParseException {

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
                        myCollection.update(document, document, true, false); // Upsert allows to insert only if it doesn't already exists
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
