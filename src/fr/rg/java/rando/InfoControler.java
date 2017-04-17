package fr.rg.java.rando;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;

import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.Pane;

public class InfoControler {
	@FXML
	Pane graph;

	@FXML
	ProgressBar progressBar;

	final int nbLocInQuery = 50;

	void setTrace(HashMap<String, Object> bundle) {
		// Création des axes
		NumberAxis xAxis = new NumberAxis();
		xAxis.setLabel("Distance [km]");
		NumberAxis yAxis = new NumberAxis();
		yAxis.setLabel("Altitude [m]");

		// Création du graphe
		LineChart<Number, Number> lineChart = new LineChart<>(xAxis, yAxis);
		lineChart.setTitle((String) bundle.get(KMLReader.PATHNAME_KEY));
		ArrayList<GeoLocation> list = (ArrayList<GeoLocation>) bundle.get(KMLReader.LOCATIONS_KEY);
		lineChart.setPrefWidth(750);
		lineChart.setPrefHeight(550);
		lineChart.setCreateSymbols(false);
		graph.getChildren().clear();
		graph.getChildren().add(lineChart);

		// Données issues du capteur
		XYChart.Series<Number, Number> serieGPS = new XYChart.Series<>();
		XYChart.Series<Number, Number> serieModel = new XYChart.Series<>();
		serieGPS.setName("Capteur GPS");
		serieModel.setName("Modélisation terrain");
		lineChart.getData().add(serieGPS);
		for (GeoLocation loc : list) {
			serieGPS.getData().add(new XYChart.Data<>(loc.length / 1000., loc.kmlElevation));
//			serieModel.getData().add(new XYChart.Data<>(loc.length / 1000., loc.modelElevation));
		}
		// Données du modèle 3D
		lineChart.getData().add(serieModel);

		ElevationLoadingService els = new ElevationLoadingService();
		els.setParam(list, serieModel);
		progressBar.progressProperty().bind(els.progressProperty());
		els.start();
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
		String cleIGN = IGNMapController.cleIGN;

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

}
