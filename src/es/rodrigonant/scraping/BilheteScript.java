package es.rodrigonant.scraping;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.http.*;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.ws.rs.core.UriBuilder;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;



//if curl -s "$website" | grep -q "$keyword"
//then
//        echo "No ticket."
//else
//		echo "Found."
//        curl -s -X POST https://api.telegram.org/bot$TOKEN/sendMessage -d chat_id=$CHAT_ID -d text="$MESSAGE$website" > /dev/null
//fi

public class BilheteScript {
	
	// site
	static final String keyword = "Aucun billet";
	static final String keywordCart = "Ajouter au panier";
	//static String keywordII = "En cours d'achat";

	// telegram
	static String TOKEN="";
	static String CHAT_ID="";
	
	public static void main(String args[]) {
		try {
			setup();
			mainLoop(args);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			tearDown();
		}
	}
	
	static void mainLoop(String args[]) {
		String website = "https://resell.seetickets.com/olympiahall/category/4396/patti-smith-and-her-band-perform-horses";
		String websiteII = "https://resell.seetickets.com/olympiahall/category/4397/patti-smith-and-her-band-perform-horses";
		
		do {
			try {
				scrapeSite(website);
				scrapeSite(websiteII);
//				Thread.sleep(5000);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} while (args.length > 0 && args[0].equals("loop"));
	}
	
	static List<WebDriver> openDrivers;
	static ChromeOptions headlessOptions = new ChromeOptions();
	static ChromeOptions openOptions = new ChromeOptions();
    
	
	static void setup() throws IOException {
		Properties prop = getProp();
		
		TOKEN = prop.getProperty("bilhete.telegram.token");
		CHAT_ID = prop.getProperty("bilhete.telegram.chatid");
		
		headlessOptions.addArguments("--headless");
		headlessOptions.addArguments("--log-level=2");
		headlessOptions.setBrowserVersion("137");
		openOptions.setBrowserVersion("137");
		
		openDrivers = new ArrayList<>();
		// login?
	}
	
	static void tearDown() {
		if (openDrivers == null)
			return;
		for (WebDriver openDriver : openDrivers)
			openDriver.quit();
	}
	
	static void scrapeSite(String url) {
		ScrapeResponse response = scrape(url, keyword, keywordCart);
		if (response.found) {
			sendTelegramChat(url, response.message);
		}
		System.out.print(".");
	}
	
	static BilheteScript.ScrapeResponse scrape(String url, String noTicketWord, String... words) {
		//selenium
		WebDriver headlessDriver = new ChromeDriver(headlessOptions);
		try {
			//driver.manage().window().setPosition(new Point(-2000, 0));
			headlessDriver.get(url);
			//jsoup
			Document doc = Jsoup.parse(headlessDriver.getPageSource());
			//driver.manage().window().minimize();
			StringBuilder sb = new StringBuilder();
			
			Elements subElement = doc.select("post-adverts-pagination");
			for (Element div : subElement.select("div")) {
				sb.append(div.text()).append("\n");
				if (div.text().contains(noTicketWord)) {
					//System.err.println("No ticket: " + div.text());
					return new BilheteScript.ScrapeResponse(false, div.text());
				} else {
					for (String word : words) {
						if (div.text().toLowerCase().contains(word.toLowerCase())) {
							sb.append("Found: ").append(div.text()).append("\n");
							if (word.equals(keywordCart)) {
								addToCart(url);
							}
						}
					}
				}
			
			}
			//System.err.println(sb.toString());
			if (sb.toString().trim().isBlank()) {
				return new ScrapeResponse(false, "No relevant content");
			}
			return new ScrapeResponse(true, sb.toString());
		} finally {
			headlessDriver.quit();
		}
	}
	
	static Map<String, String> previouslySentId = new HashMap<>();
	static SimpleDateFormat sdf = new SimpleDateFormat("yy-MMM-dd:HH");
	
	static void sendTelegramChat(String site, String msg) {
		String currDt = sdf.format(new Date());
		String messageId = msg + currDt;
		
		if (! messageId.equals(previouslySentId.get(site))) {
			previouslySentId.put(site, messageId);
			System.out.println(site + " messageId: "+ messageId);
			
			UriBuilder builder = UriBuilder
	                .fromUri("https://api.telegram.org")
	                .path("/{token}/sendMessage")
	                .queryParam("chat_id", CHAT_ID)
	                .queryParam("text", msg +": "+ site);
			
			String params = "chatid="+ CHAT_ID + "&text="+ msg + site;
			
	        HttpClient client = HttpClient.newBuilder()
	                .connectTimeout(Duration.ofSeconds(5))
	                .version(HttpClient.Version.HTTP_2)
	                .build();
			
			HttpRequest request = HttpRequest.newBuilder()
					.header("Content-Type", "application/x-www-form-urlencoded")
					.POST(HttpRequest.BodyPublishers.ofString(params))
					.timeout(Duration.ofSeconds(10))
					.uri(builder.build("bot" + TOKEN))
					.build();
			
			try {
				HttpResponse<String> response = client
				          .send(request, HttpResponse.BodyHandlers.ofString());
				
				System.out.println(response.statusCode());
		        System.out.println(response.body());
			} catch (IOException | InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	static void addToCart(String url) {
		try {
			ChromeDriver openDriver = new ChromeDriver(openOptions);
			openDrivers.add(openDriver);
			openDriver.get(url);
			
			System.err.println("\n\t Add to cart: \n" + Jsoup.parse(openDriver.getPageSource()));
			openDriver.findElement(By.linkText("ajouter")).click();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static Properties getProp() throws IOException {
		Properties props = new Properties();
		FileInputStream file = new FileInputStream(
				"src/resources/bilhete.properties");
		props.load(file);
		return props;

	}
	
	static class ScrapeResponse {
		boolean found;
		String message;
		
		ScrapeResponse(boolean f, String m) {
			found = f;
			message = m;
		}
	}
	
}