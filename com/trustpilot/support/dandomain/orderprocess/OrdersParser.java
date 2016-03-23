package com.trustpilot.support.dandomain.orderprocess;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;

public class OrdersParser {

	List ordersList;
	Document dom;

	String apikey;
	String secret;
	String email;
	String password;
	String bizunit;
	String template;
	String replyto;
	String daysdelay;
	String sender;
	String locale;

	String accessToken;


	public OrdersParser(){
		ordersList = new ArrayList();
	}


	public void runParser() throws Exception {

		//prepare access token
		getAccessToken();

		//parse the xml file and get the dom object
		parseXmlFile();

		//get each order element and create a Order object
		parseDocument();

		//Iterate through the order Object list and print the data
		printData();


		//Send Invites
		sendInvites();
	}


	private void parseXmlFile(){
		//get the factory
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

		try {

			//Using factory get an instance of document builder
			DocumentBuilder db = dbf.newDocumentBuilder();

			//parse using builder to get DOM representation of the XML file
			dom = db.parse("C:\\orders.xml");


		}catch(ParserConfigurationException pce) {
			pce.printStackTrace();
		}catch(SAXException se) {
			se.printStackTrace();
		}catch(IOException ioe) {
			ioe.printStackTrace();
		}
	}

	private void parseDocument() throws Exception{
		//get the root elememt
		Element docEle = dom.getDocumentElement();

		//get a nodelist of <order> elements
		NodeList nl = docEle.getElementsByTagName("ORDER");
		if(nl != null && nl.getLength() > 0) {
			for(int i = 0 ; i < nl.getLength();i++) {

				//get the employee element
				Element el = (Element)nl.item(i);

				//get the Order object
				Order o = getOrder(el);

				//add it to list
				if(o != null)
					ordersList.add(o);
			}
		}

	}


	private Order getOrder(Element elemOrder) throws Exception {
		Order o = new Order();

		//Everything else before product list preparation		
		NodeList generalInfo = elemOrder.getElementsByTagName("GENERAL");
		Element general = (Element)generalInfo.item(0);
		o.setId(getTextValue(general,"ORDER_ID"));

		NodeList customers = elemOrder.getElementsByTagName("CUSTOMER");
		Element customer = (Element)customers.item(0);
		o.setCustomerName(getTextValue(customer,"CUST_NAME"));
		o.setCustomerEmail(getTextValue(customer,"CUST_EMAIL"));

		NodeList ols = elemOrder.getElementsByTagName("ORDERLINES");

		if(ols.getLength() == 0)
			return null;

		NodeList pl = ((Element)ols.item(0)).getElementsByTagName("ORDERLINE");

		if(pl.getLength() == 0)
			return null;

		List<Product> prodList = new ArrayList<Product>();
		if(pl != null && pl.getLength() > 0) {
			for(int i = 0 ; i < pl.getLength();i++) {

				//get the product element
				Element el = (Element)pl.item(i);

				String psku = getTextValue(el,"PROD_NUM");
				String pname = getTextValue(el,"PROD_NAME");

				Product p = new Product();
				p.setPname(pname);
				p.setSku(psku);

				//add it to list
				prodList.add(p);
			}
		}

		o.setProdList(prodList);
		String pril = "";

		try {
			pril = createPril(o.getCustomerName(),o.getCustomerEmail(),prodList);
		} catch(Exception e) {
			System.out.println("ALERT: PRIL Creation failed for " + prodList.get(0));
		}

		o.setPril(pril);

		return o;
	}

	private String createPril(String name, String email, List<Product> prodList ) throws Exception {

		JSONObject consumer = new JSONObject();
		consumer.put("email", email);
		consumer.put("name", name);

		StringBuffer prodString = new StringBuffer();
		Iterator<Product> itr = prodList.iterator();
		while(itr.hasNext()) {
			Product p = itr.next();
			JSONObject prod = new JSONObject();
			prod.put("productUrl", "http://None"); //Invalid- As agreed with KD
			prod.put("name",p.getPname());
			prod.put("sku",p.getSku());
			prodString.append(prod.toString()+",");
		}

		String strProducts = prodString.toString().substring(0, prodString.length()-1);
		//System.out.println(strProducts);

		JSONObject json = new JSONObject();
		json.put("consumer", consumer);
		json.put("referenceId","None");
		json.put("locale","da-DK");
		json.put("products","["+strProducts+"]");


		//System.out.println(json.toString());

		StringBuilder buf = new StringBuilder();
		URL u = new URL("https://api.trustpilot.com/v1/private/product-reviews/business-units/" + this.bizunit + "/invitation-links?token="+accessToken);

		HttpURLConnection conn = (HttpURLConnection)u.openConnection();
		conn.setDoOutput(true);
		conn.setRequestMethod("POST");
		conn.setRequestProperty("Content-Type", "application/json");

		OutputStream os = conn.getOutputStream();
		os.write(json.toString().replace("\"[", "[").replace("]\"","]").replace("\\","").getBytes("UTF-8"));
		os.flush();

		BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));

		StringBuilder sbuf = new StringBuilder();

		String line;
		while((line = rd.readLine()) != null) {
			sbuf.append(line);
		}                        
		os.close();
		rd.close();

		conn.disconnect();

		JSONObject pril = new JSONObject(sbuf.toString());
		//System.out.println("PRIL: " + (String) pril.getString("reviewUrl"));
		return (String) pril.getString("reviewUrl");

	}


	private String getTextValue(Element ele, String tagName) {
		String textVal = null;
		NodeList nl = ele.getElementsByTagName(tagName);
		if(nl != null && nl.getLength() > 0) {
			Element el = (Element)nl.item(0);
			if(el.getFirstChild() != null)
				textVal = el.getFirstChild().getNodeValue();
			else
				textVal="";
		}

		return textVal;
	}

	public void printData() {

		//System.out.println(ordersList);
		Iterator<Order> itr = ordersList.iterator();
		while(itr.hasNext()){
			Order o = itr.next();
			System.out.println("****************************************************************************");
			System.out.println("***** Order ID " + o.getId() +" *****(" +o.getProdList().size()+" products)");
			System.out.println("*     Customer Name " + o.getCustomerName() +"     *");
			System.out.println("*     Customer Email " + o.getCustomerEmail() +"     *");
			System.out.println("*     Order PRIL " + o.getPril() +"     *");			
			System.out.println("****************************************************************************");

		}

	}

	public void sendInvites() {
		Calendar c = Calendar.getInstance();
		c.setTime(new Date()); 
		c.add(Calendar.DATE, Integer.parseInt(daysdelay)); // Adding 5 days
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		String preferredSendTime = sdf.format(c.getTime())+"T01:00:00";
		int numInvitesSuccess =0, numInvitesFailed=0;

		Iterator<Order> itr = ordersList.iterator();
		while(itr.hasNext()){
			Order o = itr.next();
			try {

				JSONObject json = new JSONObject();
				json.put("recipientEmail", o.getCustomerEmail());
				json.put("recipientName", o.getCustomerName());
				json.put("referenceId", o.getId());
				json.put("templateId", template);
				json.put("locale", locale);
				json.put("senderEmail", "noreply.invitations@trustpilot.com");
				json.put("senderName", sender);
				json.put("replyTo", replyto); 
				json.put("preferredSendTime", preferredSendTime);
				json.put("redirectUri", o.getPril());
				
				URL u = new URL("https://invitations-api.trustpilot.com/v1/private/business-units/" + bizunit + "/invitations");
				HttpURLConnection conn = (HttpURLConnection)u.openConnection();
	            conn.setDoOutput(true);
	            conn.setRequestMethod("POST");
	            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
	            conn.setRequestProperty("Content-Type", "application/json");
	            conn.setRequestProperty("Content-Length", Integer.toString(json.toString().length()));
	            
	            OutputStream os = conn.getOutputStream();
	            os.write(json.toString().getBytes());
	            os.flush();

	            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
	            
	            StringBuilder sbuf = new StringBuilder();
	            
	            String line;
	            while((line = rd.readLine()) != null) {
	                sbuf.append(line);
	            }

	            System.out.println(sbuf.toString());
	            
	            os.close();
	            rd.close();	            
	            conn.disconnect();
	            numInvitesSuccess++;
			} catch(Exception e) {
				System.out.println("ALERT: Send Invite API Failed for OrderID= "+o.getId());
				numInvitesFailed++;
			}
		}
		System.out.println("FINISHED: Invitation API called successfully " + numInvitesSuccess+" times, and failed " + numInvitesFailed+" times");

	}

	public static void main(String[] args){
		//create an instance
		try {
			OrdersParser oparser = new OrdersParser();
			oparser.runParser();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	private void getAccessToken() throws IOException, JSONException {

		Properties prop = new Properties();
		String propFile = "tpcredentials.properties";
		InputStream is =getClass().getClassLoader().getResourceAsStream(propFile);
		if(is!=null){
			prop.load(is);
		} else {
			throw new FileNotFoundException("Trustpilot Credentials File Missing. Cannot trigger invites!");
		}

		apikey = prop.getProperty("apikey");
		secret = prop.getProperty("secret");
		email = prop.getProperty("email");
		password = prop.getProperty("password");
		bizunit = prop.getProperty("bizunit");
		template = prop.getProperty("template");
		replyto = prop.getProperty("replyto");
		daysdelay = prop.getProperty("daysdelay");
		sender = prop.getProperty("sender");
		locale = prop.getProperty("locale");

		StringBuilder buf = new StringBuilder();
		buf.append(URLEncoder.encode("grant_type", "UTF-8")).append("=").append(URLEncoder.encode("password", "UTF-8"));
		buf.append("&").append(URLEncoder.encode("username", "UTF-8")).append("=").append(URLEncoder.encode(email, "UTF-8"));
		buf.append("&").append(URLEncoder.encode("password", "UTF-8")).append("=").append(URLEncoder.encode(password, "UTF-8"));


		String encoded = Base64.encode((apikey + ":" + secret).getBytes());

		URL u = new URL("https://api.trustpilot.com/v1/oauth/oauth-business-users-for-applications/accesstoken");
		HttpURLConnection conn = (HttpURLConnection)u.openConnection();
		conn.setDoOutput(true);
		conn.setRequestMethod("POST");
		conn.setRequestProperty("Authorization", "Basic " + encoded);
		conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		conn.setRequestProperty("Content-Length", Integer.toString(buf.length()));

		OutputStream os = conn.getOutputStream();
		os.write(buf.toString().getBytes());
		os.flush();

		BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
		StringBuilder sbuf = new StringBuilder();

		String line;
		while((line = rd.readLine()) != null) {
			sbuf.append(line);
		}                        
		os.close();
		rd.close();

		conn.disconnect();

		JSONObject authJson = new JSONObject(sbuf.toString());

		accessToken = (String)authJson.get("access_token");
	}
}
