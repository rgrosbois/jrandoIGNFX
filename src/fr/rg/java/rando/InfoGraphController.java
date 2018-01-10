package fr.rg.java.rando;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.prefs.Preferences;

import fr.rg.java.rando.util.GeoLocation;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;

public class InfoGraphController {

	static final float SEUIL_VITESSE = 1.5f; // en km/h (calcul des pauses)
	static final float MIN_DELTA_ALT = 10f; // en m (lissage des altitudes)
	static final float MAX_DELTA_ALT = 50f; // en m (lissage des altitudes)

	@FXML
	private BorderPane infoContainer;

	@FXML
	private Label altMinTF;

	@FXML
	private Label altMaxTF;

	@FXML
	private Label denivPosTF;

	@FXML
	private Label denivNegTF;

	@FXML
	private Label dureeTotTF;

	@FXML
	private Label dureeSansPauseTF;

	@FXML
	private Label distanceTF;

	@FXML
	private LineChart<Number, Number> lineChart;

	@FXML
	private NumberAxis yAxis;

	@FXML
	private ProgressBar progressBar;

	@FXML
	private ToggleButton gpsElevBtn;

	@FXML
	private ToggleButton modelElevBtn;

	@FXML
	private ToggleGroup elevationTypeGroup;

	XYChart.Series<Number, Number> serie = new XYChart.Series<>();
	private ArrayList<GeoLocation> geoLoc;

	public void setPrefWidth(double width) {
		infoContainer.setPrefWidth(width);
	}

	public void setPrefHeight(double height) {
		infoContainer.setPrefHeight(height);
	}

	@FXML
	void initialize() {
		lineChart.getData().add(serie);
		lineChart.setLegendVisible(false);
	}

	/**
	 *
	 * @param altMin
	 * @param altMax
	 * @param denivPos
	 * @param denivNeg
	 * @param dureeTot
	 * @param dureeSansPause
	 * @param distance
	 */
	public void updateContent(float altMin, float altMax, float denivPos, float denivNeg, long dureeTot,
			long dureeSansPause, float distance) {
		altMinTF.setText((int) altMin + "m");
		altMaxTF.setText((int) altMax + "m");
		denivPosTF.setText(dist2String(denivPos));
		denivNegTF.setText(dist2String(denivNeg));
		dureeTotTF.setText(time2String(dureeTot, true));
		dureeSansPauseTF.setText(time2String(dureeSansPause, true));
		distanceTF.setText(dist2String(distance));
	}

	/**
	 * Chaîne représentant une distance avec l'unité adéquate.
	 *
	 * @return Distance en mètres (si inférieure à 1km) ou kilomètre sinon.
	 */
	private String dist2String(float distance) {
		if (distance < 1000) { // Moins d'1km -> afficher en mètres
			return String.format(Locale.getDefault(), "%dm", (int) distance);
		} else { // Afficher en kilomètres
			return String.format(Locale.getDefault(), "%.1fkm", distance / 1000f);
		}
	}

	/**
	 * Chaîne représentant une durée au format __h__mn__s.
	 *
	 * @param duree
	 *            Durée en secondes.
	 * @param showSeconds
	 *            Afficher ou non les secondes
	 * @return
	 */
	private static String time2String(long duree, boolean showSeconds) {
		String s = "";

		if (duree > 3600) { // Quantité d'heures
			s += String.format(Locale.getDefault(), "%dh", duree / 60 / 60);
		}

		if (duree % 3600 > 60) { // Quantité de minutes
			s += String.format(Locale.getDefault(), "%dmn", (duree % 3600) / 60);
		}

		if (showSeconds && duree % 60 != 0) { // Quantité de secondes
			s += String.format(Locale.getDefault(), "%ds", duree % 60);
		}
		return s;
	}

	/**
	 * Spécifier la trace à analyser.
	 *
	 * @param list
	 */
	public void setTrack(ArrayList<GeoLocation> list) {
		geoLoc = list;

		// Compléter les données du modèle 3D (chargement dans un thread séparé)
		ElevationModeleLoadingService els = new ElevationModeleLoadingService();
		els.setParam(list);
		progressBar.progressProperty().bind(els.progressProperty());
		els.start();

		resetElevationSource(null);
	}

	/**
	 * Redessiner la courbe et recalculer les statistiques avec les données
	 * d'altitude appropriées.
	 *
	 * @param e
	 */
	@FXML
	private void resetElevationSource(ActionEvent e) {
		// Identification de la source
		boolean elevFromGPS = gpsElevBtn.isSelected();

		// Effacer les anciennes données
		serie.getData().clear();

		GeoLocation lastLoc = null;
		float denivPos = 0;
		float denivNeg = 0;
		float altMin = Float.POSITIVE_INFINITY;
		float altMax = Float.NEGATIVE_INFINITY;
		boolean pauseDetectee = false;
		long debutPause = 0; // Instant de départ de la pause
		long dureePause = 0; // Durée de la pause
		float deltaElev;
		float cumulDist = 0;
		for (GeoLocation loc : geoLoc) {
			// Utiliser la bonne information d'altitude
			if (elevFromGPS) {
				loc.dispElevation = loc.kmlElevation;
			} else {
				loc.dispElevation = loc.modelElevation;
			}

			if (loc.dispElevation > altMax) {
				altMax = loc.dispElevation;
			}
			if (loc.dispElevation < altMin) {
				altMin = loc.dispElevation;
			}

			if (lastLoc != null) {
				// Lisser l'altitude si données GPS
				deltaElev = loc.dispElevation - lastLoc.dispElevation;
				if (elevFromGPS && ((Math.abs(deltaElev) < MIN_DELTA_ALT) || (Math.abs(deltaElev) > MAX_DELTA_ALT))) {
					loc.dispElevation = lastLoc.dispElevation;
					deltaElev = 0;
				}
				// Dénivelés
				if (deltaElev > 0) {
					denivPos += deltaElev;
				} else {
					denivNeg += -deltaElev;
				}
				// Détection des pauses
				if (loc.speed < SEUIL_VITESSE) { // Pas de mouvement
					if (!pauseDetectee) { // Début de pause
						// sauvegarder l'instant de départ
						debutPause = loc.timeStampS;
						pauseDetectee = true;
					}
				} else { // Mouvement détecté
					if (pauseDetectee) { // Fin de pause
						// Calcul durée
						dureePause += (loc.timeStampS - debutPause);
						pauseDetectee = false;
					}
				}
				// Distance cumulative
				cumulDist += loc.distance(lastLoc);
				loc.length = (int)cumulDist;
			} else {
				loc.length = 0;
			}
			serie.getData().add(new XYChart.Data<>(loc.length / 1000., loc.dispElevation));
			lastLoc = loc;
		}

		// Adapter l'échelle de l'axe vertical
		yAxis.setAutoRanging(false);
		yAxis.setLowerBound(((int) altMin / 100) * 100);
		yAxis.setUpperBound(((int) (altMax + 99) / 100) * 100);

		// Afficher les statistiques du parcours
		if (lastLoc != null) {
			updateContent(altMin, altMax, denivPos, denivNeg, lastLoc.timeStampS - geoLoc.get(0).timeStampS,
					lastLoc.timeStampS - geoLoc.get(0).timeStampS - dureePause, cumulDist);
		} else {
			updateContent(altMin, altMax, 0, 0, 0, 0, 0);
		}

	}

	class ElevationModeleLoadingService extends Service<Void> {

		ArrayList<GeoLocation> list;

		void setParam(ArrayList<GeoLocation> l) {
			list = l;
		}

		@Override
		protected Task<Void> createTask() {
			return new Task<Void>() {

				@Override
				protected Void call() throws Exception {
					// Identifier le nombre d'altitudes à corriger et repérer
					// les indices
					// de leurs géolocalisations
					int[] glIndexes = new int[list.size()];
					int len = 0;
					GeoLocation g;
					for (int i = 0; i < list.size(); i++) {
						g = list.get(i);
						if (g.modelElevation == GeoLocation.NO_ELEVATION) {
							glIndexes[len++] = i;
						}
					}
					if (len == 0)
						return null;
					int j;
					final int nbLocInQuery = 50;
					double[] latitude = new double[nbLocInQuery];
					double[] longitude = new double[nbLocInQuery];
					int[] elevation = new int[nbLocInQuery];

					for (int i = 0; i < len; i += nbLocInQuery) {
						// Créer un groupe de géolocalisation
						for (j = 0; j < nbLocInQuery && i + j < len; j++) {
							g = list.get(glIndexes[i + j]);
							latitude[j] = g.latitude;
							longitude[j] = g.longitude;
						}
						// Récupérer les altitudes corrigées
						getQuickIGNElevations(latitude, longitude, elevation, j);

						// Recopier les altitudes corrigées
						for (j = 0; j < nbLocInQuery && i + j < len; j++) {
							g = list.get(glIndexes[i + j]);
							g.modelElevation = elevation[j];
						}
						updateProgress(i, len);

					}
					updateProgress(0, len);
					return null;
				}
			};
		}

		@Override
		protected void succeeded() {
			resetElevationSource(null);
			super.succeeded();
		}

	}

	/**
	 * Gestion des requêtes IGN pour l'obtention des altitudes corrigées.
	 *
	 * @param latitude
	 *            tableau de latitudes des géolocalisations à corriger
	 * @param longitude
	 *            tableau de longitudes des géolocalisations à corriger
	 * @param elevation
	 *            tableau pour stocker les altitudes corrigées
	 * @param nbLoc
	 *            taille utile des tableaux
	 */
	private void getQuickIGNElevations(double[] latitude, double[] longitude, int[] elevation, int nbLoc) {
		String urlString;
		URL url;
		HttpURLConnection urlConnection;
		BufferedReader in;

		// Récupérer les préférences
		Preferences prefs = Preferences.userNodeForPackage(Main.class);
		String cleIGN = prefs.get(Main.IGNKEY_KEY, IGNMapController.DEFAULT_IGNKEY);
		String proxyHostname = prefs.get(Main.PROXY_HOSTNAME_KEY, "");
		String proxyPortNum = prefs.get(Main.PROXY_PORT_NUMBER_KEY, "0");

		urlString = "http://gpp3-wxs.ign.fr/" + cleIGN + "/alti/rest/elevation.json?lat=" + latitude[0];
		for (int j = 1; j < nbLoc; j++) {
			urlString += "," + latitude[j];
		}
		urlString += "&lon=" + longitude[0];
		for (int j = 1; j < nbLoc; j++) {
			urlString += "," + longitude[j];
		}
		urlString += "&zonly=true&delimiter=,";
		try {
			url = new URL(urlString);
			if (!"".equals(proxyHostname)) { // utiliser un proxy
				urlConnection = (HttpURLConnection) url.openConnection(new Proxy(Proxy.Type.HTTP,
						new InetSocketAddress(proxyHostname, Integer.parseInt(proxyPortNum))));
			} else { // Pas de proxy
				urlConnection = (HttpURLConnection) url.openConnection();
			}
			urlConnection.setRequestProperty("Referer", "http://localhost/IGN/");

			// Lecture de la réponse, ligne par ligne.
			String reponse = "", line;
			in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
			while ((line = in.readLine()) != null) {
				reponse += line;
			}
			in.close();

			int startAltIdx, endAltIdx;
			startAltIdx = reponse.indexOf("[");
			endAltIdx = reponse.indexOf("]", startAltIdx + 1);
			StringTokenizer st = new StringTokenizer(reponse.substring(startAltIdx + 1, endAltIdx), ",");
			int j = 0;
			while (st.hasMoreTokens()) {
				elevation[j++] = (int) Float.parseFloat(st.nextToken());
			}
		} catch (IOException ex) {
			System.err.println("Erreur de communication avec le serveur");
		}
	}

}
