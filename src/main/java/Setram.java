/**
 * Created by pierregrandjean on 28/12/2016.
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

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

    public static String display(WebClient client) {

        // To forge searchURL, we have to capture date and hour as : time=07|29&date=2016|12|28
        SimpleDateFormat customDateFormat = new SimpleDateFormat("'time='HH|mm'&date='yyyy|MM|dd");
        customDateFormat.setTimeZone(TimeZone.getTimeZone("Europe/Paris"));
        Date now = new Date();
        String dateAndHour = customDateFormat.format(now);
/* Ce sera utile plus tard
        SimpleDateFormat isoDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        isoDateFormat.setTimeZone(TimeZone.getTimeZone("Europe/Paris"));
        Date date = isoDateFormat.parse("2010-05-23T09:01:02");
*/
        String searchUrl = "http://setram.mobi/module/mobile/itineraire/iti_choix.php?depart=StopArea%7C342%7CUniversite%7CLe_Mans%7C%7C%7C437035%7C2337548%7C678%210%2114%3B677%210%2114%3B680%210%2114%3B679%210%2114%3B&arrive=StopArea%7C321%7CSaint_Martin%7CLe_Mans%7C%7C%7C440979%7C2333984%7C632%210%2114%3B629%210%2114%3B631%210%2114%3B630%210%2114%3B&sens=1&" + dateAndHour;

        String htmlTextToDisplay = "ERROR - display() method";

        try {

            HtmlPage timetablePage = client.getPage(searchUrl);

            List<DomText> items = (List<DomText>)timetablePage.getByXPath("//td[@class='ligne-heure']/text()");

            ArrayList<String> plannedDepartures = new ArrayList<>();
            ArrayList<String> plannedArrivals = new ArrayList<>();
            Integer itemsNumber = 0;
            for (DomText item : items) {
                if (itemsNumber%2 == 0) { // nombre pair
                    plannedDepartures.add(item.toString());
                }
                else { // nombre impair
                    plannedArrivals.add(item.toString());
                }
                itemsNumber++;
            }


            final WebClient webClient = new WebClient();

            // Instead of requesting the page directly we create a WebRequestSettings object
            WebRequest requestSettings = new WebRequest(
                    new URL("http://dev.actigraph.fr/actipages/setram/module/mobile/pivk/relais.html.php"), HttpMethod.POST);

            // Then we set the request parameters
            requestSettings.setRequestParameters(new ArrayList());
            requestSettings.getRequestParameters().add(new NameValuePair("a", "refresh"));
            requestSettings.getRequestParameters().add(new NameValuePair("refs", "271694081|271691009|271689218|271688193|271687425|271686913|271686657|271682305|271682049"));
            requestSettings.getRequestParameters().add(new NameValuePair("ran", "860407218"));
            // Finally, we can get the page
            HtmlPage realTimePage = webClient.getPage(requestSettings);

            List<DomText> realTimes = (List<DomText>)realTimePage.getByXPath("//li[@id]/text()");
            ArrayList<String> realDepartures = new ArrayList<>();
            for (DomText realTime : realTimes) {
                String toWorkString = realTime.toString();
                if (!toWorkString.contains("dans ")) {
                    realDepartures.add("Imminent");
                }
                else {
                    String requiredString = toWorkString.substring(toWorkString.indexOf("dans ") + 5, toWorkString.indexOf(" minutes"));
                    realDepartures.add(requiredString);
                }
            }


            htmlTextToDisplay = "DEBUG - dateAndHour : " + dateAndHour + "<br/>"
                    + "Prochains trajets Université - République <br/>";
            Integer i;
            for (i=0; i<itemsNumber/2; i++) {
                htmlTextToDisplay = htmlTextToDisplay + plannedDepartures.get(i) + " - " + plannedArrivals.get(i) + "<br/>";
            }
            htmlTextToDisplay = htmlTextToDisplay + "Réels départs dans (en minutes) : <br/>";
            for (String realDeparture : realDepartures) {
                htmlTextToDisplay = htmlTextToDisplay + realDeparture + "<br/>";
            }

        }catch(Exception e){
            e.printStackTrace();
        }

        return htmlTextToDisplay;

    }

}
