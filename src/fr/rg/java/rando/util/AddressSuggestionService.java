/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fr.rg.java.rando.util;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.prefs.Preferences;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import fr.rg.java.rando.IGNMapController;
import fr.rg.java.rando.Main;
import javafx.concurrent.Service;
import javafx.concurrent.Task;

/**
 * Service pour suggérer des adresses correspondant à une saisie partielle.
 */
public class AddressSuggestionService extends Service<ArrayList<GeoLocation>> {

	String address;
	int maxResponses;

	public AddressSuggestionService(String partialAddress, int maxRep) {
		address = partialAddress;
		maxResponses = maxRep;
	}

	/**
	 * Récupérer une liste de suggestion d'adresses auprès de l'IGN grâce à une
	 * requête OpenLS.
	 *
	 * @return Liste de suggestion de géolocalisations
	 */
	@Override
	protected Task<ArrayList<GeoLocation>> createTask() {

		/**
		 * Tâche de récupération des tuiles de la carte.
		 */
		return new Task<ArrayList<GeoLocation>>() {

			@Override
			protected ArrayList<GeoLocation> call() throws Exception {
				URL url;
				HttpURLConnection urlConnection;
				Writer output;
				ArrayList<GeoLocation> loc = new ArrayList<>();
				Document dom;

				// Récupérer les préférences
				Preferences prefs = Preferences.userNodeForPackage(Main.class);
				String cleIGN = prefs.get(Main.IGNKEY_KEY, IGNMapController.DEFAULT_IGNKEY);
				String proxyHostname = prefs.get(Main.PROXY_HOSTNAME_KEY, "");
				String proxyPortNum = prefs.get(Main.PROXY_PORT_NUMBER_KEY, "0");

				// Récupérer la clé IGN
				String content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<XLS\n"
						+ "xmlns:xls=\"http://www.opengis.net/xls\"\n" + "xmlns:gml=\"http://www.opengis.net/gml\"\n"
						+ "xmlns=\"http://www.opengis.net/xls\"\n"
						+ "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" + "version=\"1.2\"\n"
						+ "xsi:schemaLocation=\"http://www.opengis.net/xls "
						+ "http://schemas.opengis.net/ols/1.2/olsAll.xsd\">\n" + "<RequestHeader/>\n"
						+ "<Request requestID=\"1\" version=\"1.2\" " + "methodName=\"LocationUtilityService\" "
						+ "maximumResponses=\"" + maxResponses + "\">\n" + "<GeocodeRequest returnFreeForm=\"false\">\n"
						+ "<Address countryCode=\"StreetAddress\">\n" + "<freeFormAddress>" + address
						+ "</freeFormAddress>\n" + "</Address>\n" + "</GeocodeRequest>\n" + "</Request>\n" + "</XLS>\n";
				try {
					// Envoyer la requête
					url = new URL("http://gpp3-wxs.ign.fr/" + cleIGN + "/geoportail/ols");
					if (!"".equals(proxyHostname)) { // utiliser un proxy
						urlConnection = (HttpURLConnection) url.openConnection(new Proxy(Proxy.Type.HTTP,
								new InetSocketAddress(proxyHostname, Integer.parseInt(proxyPortNum))));
					} else { // Pas de proxy
						urlConnection = (HttpURLConnection) url.openConnection();
					}
					urlConnection.setDoOutput(true); // pour poster
					urlConnection.setDoInput(true); // pour lire
					urlConnection.setUseCaches(false);
					urlConnection.setRequestProperty("Referer", "http://localhost/IGN/");
					urlConnection.setRequestMethod("POST");
					urlConnection.setRequestProperty("Content-Type", "text/xml");
					urlConnection.connect();
					output = new OutputStreamWriter(urlConnection.getOutputStream());
					output.append(content);
					output.flush();
					output.close();

					// Analyser la réponse
					DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
					DocumentBuilder db;
					Element document, adresse, gmlpos;
					NodeList nl, place, streetAddress, building, street, postalCode;
					NamedNodeMap place2;
					String codePostal = null, ville = null, rue = null, numeroRue = null;

					// Récupérer le modèle du document
					db = dbf.newDocumentBuilder();
					dom = db.parse(urlConnection.getInputStream());

					// Extraire les informations pertinentes
					document = dom.getDocumentElement();
					nl = document.getElementsByTagName("GeocodedAddress");

					for (int i = 0; i < nl.getLength(); i++) { // Uniquement la
																// première
																// réponse
						adresse = (Element) nl.item(i);
						gmlpos = (Element) adresse.getElementsByTagName("gml:pos").item(0);
						String[] geo = gmlpos.getTextContent().split(" ");
						loc.add(new GeoLocation(Double.parseDouble(geo[1]), // longitude
								Double.parseDouble(geo[0]))); // latitude

						// Compléments d'information
						place = adresse.getElementsByTagName("Place");
						for (int j = 0, n = place.getLength(); j < n; j++) { // éléments
																				// Place
							place2 = place.item(j).getAttributes();
							for (int k = 0, n2 = place2.getLength(); k < n2; k++) { // attributs
																					// de
																					// Place

								if (place2.item(k).getNodeValue().equalsIgnoreCase("Bbox")) { // Bounding
																								// Box
									String bbox = place.item(j).getTextContent();
									String[] geobox = bbox.split(";");
									loc.get(i).setBoundingBox(Double.parseDouble(geobox[0]), // longmin
											Double.parseDouble(geobox[2]), // longmax
											Double.parseDouble(geobox[1]), // latmin
											Double.parseDouble(geobox[3])); // latmax
								} else if (place2.item(k).getNodeValue().equalsIgnoreCase("Commune")) {
									ville = place.item(j).getTextContent();
									if (ville.isEmpty()) {
										ville = null;
									}
								}

							} // Boucle sur attributs de Place
						} // Boucle sur éléments Place

						streetAddress = adresse.getElementsByTagName("StreetAddress");
						if (streetAddress != null) {
							// Numéro de rue
							building = ((Element) streetAddress.item(0)).getElementsByTagName("Building");
							if (building != null && building.getLength() >= 1) {
								numeroRue = building.item(0).getAttributes().item(0).getTextContent();
								if (numeroRue.isEmpty()) {
									numeroRue = null;
								}
							}

							// Rue
							street = ((Element) streetAddress.item(0)).getElementsByTagName("Street");
							if (street != null && street.getLength() >= 1) {
								rue = street.item(0).getTextContent();
								if (rue.isEmpty()) {
									rue = null;
								}
							}

						}
						// Code postal
						postalCode = adresse.getElementsByTagName("PostalCode");
						if (postalCode != null && postalCode.getLength() >= 1) {
							codePostal = postalCode.item(0).getTextContent();
							if (codePostal.isEmpty()) {
								codePostal = null;
							}
						}
						// Ajouter une éventuelle adresse
						loc.get(i).setAdresse(((numeroRue != null) ? numeroRue + " " : "")
								+ ((rue != null) ? rue + ", " : "") + ((codePostal != null) ? codePostal + " " : "")
								+ ((ville != null) ? ville : ""));
					} // Boucle sur les adresses candidates
				} catch (ParserConfigurationException | SAXException | IOException e1) {
				}

				return loc;
			}
		};

	}

}
