/**
 * Created by Pierre Grandjean on 28/12/2016.
 */

import static spark.Spark.*;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.DomText;
import com.gargoylesoftware.htmlunit.util.NameValuePair;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

public class Setram {
    public static void main(String[] args) {

        port(Integer.valueOf(System.getenv("PORT")));

        WebClient client = new WebClient();
        client.getOptions().setCssEnabled(false);
        client.getOptions().setJavaScriptEnabled(false);
        try {

            get("/", (req, res) -> display(client) );

        }catch(Exception e){
            e.printStackTrace();
        }

    }

    private static String display(WebClient client) {

        Date now = new Date();

        // To forge searchURL, we have to capture date and hour as : time=07|29&date=2016|12|28
        SimpleDateFormat customDateFormat = new SimpleDateFormat("'time='HH|mm'&date='yyyy|MM|dd");
        customDateFormat.setTimeZone(TimeZone.getTimeZone("Europe/Paris"));
        String dateForSearchUrl = customDateFormat.format(now);

        SimpleDateFormat isoDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        isoDateFormat.setTimeZone(TimeZone.getTimeZone("Europe/Paris"));
        SimpleDateFormat justDayDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        justDayDateFormat.setTimeZone(TimeZone.getTimeZone("Europe/Paris"));
        String day = justDayDateFormat.format(now);

        String htmlTextToDisplay = "ERROR in display() method";

        try {
            // TODO : refactor this commented part as a method
/*
            String searchUrl = "http://setram.mobi/module/mobile/itineraire/iti_choix.php?depart=StopArea%7C342%7CUniversite%7CLe_Mans%7C%7C%7C437035%7C2337548%7C678%210%2114%3B677%210%2114%3B680%210%2114%3B679%210%2114%3B&arrive=StopArea%7C321%7CSaint_Martin%7CLe_Mans%7C%7C%7C440979%7C2333984%7C632%210%2114%3B629%210%2114%3B631%210%2114%3B630%210%2114%3B&sens=1&" + dateForSearchUrl;
            HtmlPage timetablePage = client.getPage(searchUrl);

            List<DomText> items = (List<DomText>)timetablePage.getByXPath("//td[@class='ligne-heure']/text()");

            ArrayList<Date> plannedDepartures = new ArrayList<>();
            ArrayList<Date> plannedArrivals = new ArrayList<>();

            Integer itemsNumber = 0;
            for (DomText item : items) {
                // We have to convert a String (HHhmm) into a Date
                String[] parts = item.toString().split("h");
                String hours = parts[0];
                String minutes = parts[1];
                Date date = isoDateFormat.parse(day + "T" + hours + ":" + minutes + ":00");

                if (itemsNumber%2 == 0) { // nombre pair
                    plannedDepartures.add(date);
                }
                else { // nombre impair
                    plannedArrivals.add(date);
                }
                itemsNumber++;
            }
*/

            ArrayList<NameValuePair> realMinutesBeforeDepartures = new ArrayList<NameValuePair>();
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
/*
            Integer i;
            for (i=0; i<itemsNumber/2; i++) {
                htmlTextToDisplay = htmlTextToDisplay + isoDateFormat.format(plannedDepartures.get(i)) + " - " + isoDateFormat.format(plannedArrivals.get(i)) + "<br/>";
            }
            htmlTextToDisplay = htmlTextToDisplay + "Réels départs dans (en minutes) : <br/>";
*/

            // Sorting
            Collections.sort(realMinutesBeforeDepartures, comp);

            for (NameValuePair realMinutesBeforeDeparture : realMinutesBeforeDepartures) {
//                long durationDate = plannedArrivals.get(0).getTime() - plannedDepartures.get(0).getTime();
                htmlTextToDisplay = htmlTextToDisplay + realMinutesBeforeDeparture.getValue() + " (" + realMinutesBeforeDeparture.getName() + ")<br/>";
//                + " durée trajet : " + durationDate/(1000*60) + "minutes<br/>";
            }

        }
        catch(Exception e){
            e.printStackTrace();
        }

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
                if (toWorkString.contains("imminent")) { // For strings like "Passage imminent"
                    realMinutesBeforeDepartures.add(new NameValuePair(busLine, Integer.toString(0)));
                }
                else if (!toWorkString.contains("dans")) { // For strings like "Passage suivant à 12 H 34 pour REPUBLIQUE"
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

        }
        catch(Exception e){
            e.printStackTrace();
        }
        return realMinutesBeforeDepartures;

    }

    public static Comparator<NameValuePair> comp = new Comparator<NameValuePair>() {        // solution than making method synchronized
        @Override
        public int compare(NameValuePair p1, NameValuePair p2) {
            return p1.getValue().compareTo(p2.getValue());
        }
    };

}
