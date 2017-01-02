/**
 * Created by Pierre Grandjean on 28/12/2016.
 */

import static org.apache.commons.lang3.StringUtils.isNumeric;
import static org.apache.commons.lang3.StringUtils.remove;
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
import org.apache.commons.lang3.time.DateUtils;
import org.jsoup.select.Elements;

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

        } catch(Exception e){
            e.printStackTrace();
        }

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

        SimpleDateFormat justDayDateFormatForSearchUrl = new SimpleDateFormat("yyyy|MM|d");
        justDayDateFormatForSearchUrl.setTimeZone(TimeZone.getTimeZone("Europe/Paris"));
        String dayForSearchUrl = justDayDateFormatForSearchUrl.format(now);

        String searchUrlWithoutDate;

        String htmlTextToDisplay = "ERROR in display() method";


        ArrayList<NameValuePair> realMinutesBeforeDepartures = new ArrayList<>();
        NameValuePair params[] = new NameValuePair[3];
        params[0] = new NameValuePair("a", "refresh");

        // Securité Sociale - Bus 3 - Vers République
        params[1] = new NameValuePair("refs", "268640008|268633352");
        params[2] = new NameValuePair("ran", "524818637");
        addRealMinutesBeforeDepartures("Bus 3 dir Oasis", realMinutesBeforeDepartures, params, now, isoDateFormat, day);

        // Securité Sociale - Bus 12 - Vers République
        params[1] = new NameValuePair("refs", "269260050");
        params[2] = new NameValuePair("ran", "663034440");
        addRealMinutesBeforeDepartures("Bus 12 dir République", realMinutesBeforeDepartures, params, now, isoDateFormat, day);

        // Securité Sociale - Bus 23 - Vers République
        params[1] = new NameValuePair("refs", "269979663");
        params[2] = new NameValuePair("ran", "177820456");
        addRealMinutesBeforeDepartures("Bus 23 dir République", realMinutesBeforeDepartures, params, now, isoDateFormat, day);

        // Securité Sociale - Bus 25 - Vers République
        params[1] = new NameValuePair("refs", "270112787|270112533|270111761|270111507");
        params[2] = new NameValuePair("ran", "452935723");
        addRealMinutesBeforeDepartures("Bus 25 dir République", realMinutesBeforeDepartures, params, now, isoDateFormat, day);

        htmlTextToDisplay = "Prochains départ de Sécurité Sociale : <br/>";

        // Sorting
        Collections.sort(realMinutesBeforeDepartures, comp);

        for (NameValuePair realMinutesBeforeDeparture : realMinutesBeforeDepartures) {
            htmlTextToDisplay = htmlTextToDisplay + realMinutesBeforeDeparture.getValue() + " minutes (" + realMinutesBeforeDeparture.getName() + ")<br/>";
        }


        return htmlTextToDisplay;

    }


    private static String addToTimetable() throws IOException, ParseException {
        Date now = new Date();

        SimpleDateFormat isoDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        isoDateFormat.setTimeZone(TimeZone.getTimeZone("Europe/Paris"));
        SimpleDateFormat justDayDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        justDayDateFormat.setTimeZone(TimeZone.getTimeZone("Europe/Paris"));
        String day = justDayDateFormat.format(now);

        SimpleDateFormat justDayDateFormatForSearchUrl = new SimpleDateFormat("yyyy|MM|d");
        justDayDateFormatForSearchUrl.setTimeZone(TimeZone.getTimeZone("Europe/Paris"));
        String dayForSearchUrl = justDayDateFormatForSearchUrl.format(now);

        String searchUrlWithoutDate;

        String htmlTextToDisplay = "";

        htmlTextToDisplay = htmlTextToDisplay + "Recording Tram 1 direction Antarès-MMArena, Arrêt Université ...<br/>";
        searchUrlWithoutDate = "http://lemans.prod.navitia.com/Navitia/HP_4_HP.asp?Network=1%7CSTA1%7CSetram&Line=27%7CSET33%7CT1%7CAntar%E8s%20-%20Universit%E9%7CAntar%E8s%20vers%20Universit%E9%7CUniversit%E9%20vers%20Antar%E8s%7C16%7CTramway&Direction=-1&StopArea=342%7CSET1606%7CUniversite%7CLe%20Mans&Date=";
        captureTimetable("Tram 1 dir. Antarès-MMArena", "Université", searchUrlWithoutDate, isoDateFormat, day, dayForSearchUrl);
        htmlTextToDisplay = htmlTextToDisplay + "... done<br/>";

        htmlTextToDisplay = htmlTextToDisplay + "Recording Tram 1 direction Antarès-MMArena, Arrêt Gambetta-Mûriers ...<br/>";
        searchUrlWithoutDate = "http://lemans.prod.navitia.com/Navitia/HP_4_HP.asp?Network=1%7CSTA1%7CSetram&Line=27%7CSET33%7CT1%7CAntar%E8s%20-%20Universit%E9%7CAntar%E8s%20vers%20Universit%E9%7CUniversit%E9%20vers%20Antar%E8s%7C16%7CTramway&Direction=-1&StopArea=204%7CSET1590%7CGambetta-muriers%7CLe%20Mans&Date=";
        captureTimetable("Tram 1 dir. Antarès-MMArena", "Gambetta-Mûriers", searchUrlWithoutDate, isoDateFormat, day, dayForSearchUrl);
        htmlTextToDisplay = htmlTextToDisplay + "... done<br/>";

        htmlTextToDisplay = htmlTextToDisplay + "Recording Tram 1 direction Antarès-MMArena, Arrêt Éperon Cité Plantagenêt ...<br/>";
        searchUrlWithoutDate = "http://lemans.prod.navitia.com/Navitia/HP_4_HP.asp?Network=1%7CSTA1%7CSetram&Line=27%7CSET33%7CT1%7CAntar%E8s%20-%20Universit%E9%7CAntar%E8s%20vers%20Universit%E9%7CUniversit%E9%20vers%20Antar%E8s%7C16%7CTramway&Direction=-1&StopArea=185%7CSET130%7CEperon%7CLe%20Mans&Date=";
        captureTimetable("Tram 1 dir. Antarès-MMArena", "Éperon Cité Plantagenêt", searchUrlWithoutDate, isoDateFormat, day, dayForSearchUrl);
        htmlTextToDisplay = htmlTextToDisplay + "... done<br/>";

        htmlTextToDisplay = htmlTextToDisplay + "Recording Tram 1 direction Antarès-MMArena, Arrêt République ...<br/>";
        searchUrlWithoutDate = "http://lemans.prod.navitia.com/Navitia/HP_4_HP.asp?Network=1%7CSTA1%7CSetram&Line=27%7CSET33%7CT1%7CAntar%E8s%20-%20Universit%E9%7CAntar%E8s%20vers%20Universit%E9%7CUniversit%E9%20vers%20Antar%E8s%7C16%7CTramway&Direction=-1&StopArea=318%7CSET318%7CR%E9publique%7CLe%20Mans&Date=";
        captureTimetable("Tram 1 dir. Antarès-MMArena", "République", searchUrlWithoutDate, isoDateFormat, day, dayForSearchUrl);
        htmlTextToDisplay = htmlTextToDisplay + "... done<br/>";

        htmlTextToDisplay = htmlTextToDisplay + "Recording Tram 1 direction Antarès-MMArena, Arrêt Saint-Martin ...<br/>";
        searchUrlWithoutDate = "http://lemans.prod.navitia.com/Navitia/HP_4_HP.asp?Network=1%7CSTA1%7CSetram&Line=27%7CSET33%7CT1%7CAntar%E8s%20-%20Universit%E9%7CAntar%E8s%20vers%20Universit%E9%7CUniversit%E9%20vers%20Antar%E8s%7C16%7CTramway&Direction=-1&StopArea=321%7CSET357%7CSaint%20Martin%7CLe%20Mans&Date=";
        captureTimetable("Tram 1 dir. Antarès-MMArena", "Saint-Martin", searchUrlWithoutDate, isoDateFormat, day, dayForSearchUrl);
        htmlTextToDisplay = htmlTextToDisplay + "... done<br/>";


        htmlTextToDisplay = htmlTextToDisplay + "Recording Bus 12 direction Antarès-MMArena, Arrêt Californie ...<br/>";
        searchUrlWithoutDate = "http://lemans.prod.navitia.com/Navitia/HP_4_HP.asp?Network=1|STA1|Setram&Line=9|SET52|12|R%EF%BF%BDpublique%20-%20St%20Martin|R%EF%BF%BDpublique%20vers%20St%20Martin|St%20Martin%20vers%20R%EF%BF%BDpublique|5|Bus&Direction=1&StopArea=143|SET64|Californie|Le%20Mans&Date=";
        captureTimetable("Bus 12 dir. Saint-Martin", "Californie", searchUrlWithoutDate, isoDateFormat, day, dayForSearchUrl);
        htmlTextToDisplay = htmlTextToDisplay + "... done<br/>";

        return htmlTextToDisplay;

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

        DBCollection myCollection = connectToDB("timetable");

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
                    if (isNumeric(e.text())) {  // The empty table cells contains white space character
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


    private static DBCollection connectToDB(String collectionName) {
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
