package fr.rg.java.rando;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.prefs.Preferences;

import fr.rg.java.rando.util.GeoLocation;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

public class InfoGraph extends BorderPane {

	static final float SEUIL_VITESSE = 1.5f; // en km/h (calcul des pauses)
	static final float MIN_DELTA_ALT = 10f; // en m (lissage des altitudes)
	static final float MAX_DELTA_ALT = 50f; // en m (lissage des altitudes)

	LineChart<Number, Number> lineChart;
	ProgressBar progressBar;
	Label altMinTF, altMaxTF, denivPosTF, denivNegTF, dureeTotTF, dureeSansPauseTF, distanceTF;

	public InfoGraph() {
		// Création du graphe
		NumberAxis xAxis = new NumberAxis();
		xAxis.setLabel("Distance [km]");
		NumberAxis yAxis = new NumberAxis();
		yAxis.setLabel("Altitude [m]");
		lineChart = new LineChart<>(xAxis, yAxis);
		lineChart.setCreateSymbols(false);
		lineChart.setMinHeight(100);
		lineChart.setPrefHeight(200);
		lineChart.setMaxHeight(300);
		AnchorPane.setLeftAnchor(lineChart, 0.0);
		AnchorPane.setRightAnchor(lineChart, 0.0);
		AnchorPane ap = new AnchorPane(lineChart);
		setCenter(ap);

		// Statistiques
		Font fl = new Font(8);
		Font fh = new Font(12);
		// Altitudes
		Label lAltMin = new Label("Altitude min");
		lAltMin.setFont(fl);
		altMinTF = new Label("-");
		altMinTF.setFont(fh);
		Label lAltMax = new Label("Altitude max");
		lAltMax.setFont(fl);
		altMaxTF = new Label("-");
		altMaxTF.setFont(fh);
		Label lDenivPos = new Label("Dénivelé pos");
		lDenivPos.setFont(fl);
		denivPosTF = new Label("-");
		denivPosTF.setFont(fh);
		Label lDenivNeg = new Label("Dénivelé neg");
		lDenivNeg.setFont(fl);
		denivNegTF = new Label("-");
		denivNegTF.setFont(fh);
		Label lDureeTot = new Label("Durée totale");
		lDureeTot.setFont(fl);
		dureeTotTF = new Label("-");
		dureeTotTF.setFont(fh);
		Label lDureeSP = new Label("sans pause");
		lDureeSP.setFont(fl);
		dureeSansPauseTF = new Label("-");
		dureeSansPauseTF.setFont(fh);
		Label lDist = new Label("Distance");
		lDist.setFont(fl);
		distanceTF = new Label("-");
		distanceTF.setFont(fh);

		VBox v1 = new VBox(lAltMin, altMinTF, lDenivPos, denivPosTF, lDureeTot, dureeTotTF, lDist, distanceTF);
		VBox v2 = new VBox(lAltMax, altMaxTF, lDenivNeg, denivNegTF, lDureeSP, dureeSansPauseTF);
		HBox h = new HBox(v1, v2);
		h.setSpacing(5);
		h.setPadding(new Insets(5));
		setRight(h);

		// Ajouter un indicateur de progression
		progressBar = new ProgressBar();
		progressBar.setMinHeight(2);
		progressBar.setPrefHeight(10);
		progressBar.setMaxHeight(20);
		progressBar.setProgress(0);
		AnchorPane aPane = new AnchorPane(progressBar);
		AnchorPane.setLeftAnchor(progressBar, 0.0);
		AnchorPane.setRightAnchor(progressBar, 0.0);
		setBottom(aPane);
	}

	public void setTrack(ArrayList<GeoLocation> list) {
		// Données issues du capteur
		XYChart.Series<Number, Number> serieGPS = new XYChart.Series<>();
		XYChart.Series<Number, Number> serieModel = new XYChart.Series<>();
		serieGPS.setName("Capteur GPS");
		serieModel.setName("Modélisation terrain");
		lineChart.getData().add(serieGPS);

		GeoLocation lastLoc = null;
		float denivPos = 0;
		float denivNeg = 0;
		float altMin = Float.POSITIVE_INFINITY;
		float altMax = Float.NEGATIVE_INFINITY;
		boolean pauseDetectee = false;
		long debutPause = 0; // Instant de départ de la pause
		long dureePause = 0; // Durée de la pause
		float deltaElev;
		for (GeoLocation loc : list) {
			loc.dispElevation = loc.kmlElevation;
			if(loc.dispElevation > altMax) {
				altMax = loc.dispElevation;
			}
			if(loc.dispElevation < altMin) {
				altMin = loc.dispElevation;
			}

			if (lastLoc != null) {
				// Lisser l'altitude
				deltaElev = loc.dispElevation - lastLoc.dispElevation;
				if ((Math.abs(deltaElev) < MIN_DELTA_ALT) || (Math.abs(deltaElev) > MAX_DELTA_ALT)) {
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
						debutPause = loc.timeStampS; // sauvegarder l'instant de
														// départ
						pauseDetectee = true;
					}
				} else { // Mouvement détecté
					if (pauseDetectee) { // Fin de pause
						dureePause += (loc.timeStampS - debutPause); // Calcul
																		// de
																		// la
																		// durée
						pauseDetectee = false;
					}
				}
			}
			serieGPS.getData().add(new XYChart.Data<>(loc.length / 1000., loc.dispElevation));
			// serieModel.getData().add(new XYChart.Data<>(loc.length / 1000.,
			// loc.modelElevation));
			lastLoc = loc;
		}
		// Données du modèle 3D
		lineChart.getData().add(serieModel);

		ElevationLoadingService els = new ElevationLoadingService();
		els.setParam(list, serieModel);
		progressBar.progressProperty().bind(els.progressProperty());
		els.start();

		// Afficher les statistiques du parcours
		altMinTF.setText((int) altMin + " m");
		altMaxTF.setText((int) altMax + " m");
		if (lastLoc != null) {
			distanceTF.setText(dist2String(lastLoc.length));
			denivPosTF.setText(dist2String(denivPos));
			denivNegTF.setText(dist2String(denivNeg));
			dureeTotTF.setText(time2String(lastLoc.timeStampS - list.get(0).timeStampS, true));
			dureeSansPauseTF.setText(time2String(lastLoc.timeStampS - list.get(0).timeStampS - dureePause, true));
		}
	}

	class ElevationLoadingService extends Service<Void> {

		ArrayList<GeoLocation> list;
		XYChart.Series<Number, Number> series;

		void setParam(ArrayList<GeoLocation> l, XYChart.Series<Number, Number> s) {
			list = l;
			series = s;
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
						if (g.modelElevation == -1) {
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
			for (GeoLocation loc : list) {
				series.getData().add(new XYChart.Data<>(loc.length / 1000., loc.modelElevation));
			}
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

		// Récupérer la clé IGN
		Preferences prefs = Preferences.userNodeForPackage(Main.class);
		String cleIGN = prefs.get(Main.IGNKEY_KEY, IGNMap.DEFAULT_IGNKEY);

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
			urlConnection = (HttpURLConnection) url.openConnection();
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
}
