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
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.chart.Axis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;

public class InfoGraphController {

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
	private Label vitMoyTF;

	@FXML
	private Label vitMoySansPauseTF;

	@FXML
	private Label distanceTF;

	// Séries de données pour les graphes
	private XYChart.Series<Number, Number> elevationSerie = new XYChart.Series<>();
	private XYChart.Series<Number, Number> speedSerie = new XYChart.Series<>();

	// Liste de géolocalisations de la tracer courante
	private ArrayList<GeoLocation> geoLoc;

	@FXML
	private LineChart<Number, Number> elevationChart;

	@FXML
	private LineChart<Number, Number> speedChart;

	@FXML
	private AnchorPane chartArea;

	@FXML
	private ProgressBar progressBar;

	@FXML
	private ToggleButton gpsElevBtn;

	@FXML
	private ToggleButton modelElevBtn;

	@FXML
	private ToggleGroup elevationTypeGroup;

	@FXML
	private AnchorPane detailsLayer;

	@FXML
	private VBox detailsPopup;

	@FXML
	private Label distanceInfoLbl;

	@FXML
	private Label elevationInfoLbl;

	@FXML
	private Label speedInfoLbl;

	@FXML
	private Label timeInfoLbl;

	/**
	 * Finaliser l'initialisation des graphes
	 */
	@FXML
	void initialize() {
		chartArea.setOnMouseMoved(null);
		chartArea.setMouseTransparent(false);
		final double yAxisWidth = 80.0;

		// Graphe initial (prévoir 80 px à droite pour l'axe du 2eme graphe)
		elevationChart.getData().add(elevationSerie);
		elevationChart.prefHeightProperty().bind(chartArea.heightProperty());
		elevationChart.minWidthProperty().bind(chartArea.widthProperty().subtract(yAxisWidth));
		elevationChart.prefWidthProperty().bind(chartArea.widthProperty().subtract(yAxisWidth));
		elevationChart.maxWidthProperty().bind(chartArea.widthProperty().subtract(yAxisWidth));

		elevationChart.setVerticalZeroLineVisible(false);
		elevationChart.setHorizontalZeroLineVisible(false);
		elevationChart.setVerticalGridLinesVisible(false);
		elevationChart.setHorizontalGridLinesVisible(false);

		// Graphe surimposé décalé de la largeur de l'axe Y du graphe 1 - Largeur moins 20 ?
		speedChart.getData().add(speedSerie);
		speedChart.prefHeightProperty().bind(chartArea.heightProperty());
		speedChart.minWidthProperty().bind(chartArea.widthProperty().subtract(yAxisWidth+20));
		speedChart.prefWidthProperty().bind(chartArea.widthProperty().subtract(yAxisWidth+20));
		speedChart.maxWidthProperty().bind(chartArea.widthProperty().subtract(yAxisWidth+20));
		speedChart.translateXProperty().bind(elevationChart.getYAxis().widthProperty());
		speedChart.setMouseTransparent(true);

		// Couche d'information avec popup
		bindMouseEvents();
		elevationInfoLbl.managedProperty().bind(elevationInfoLbl.visibleProperty());
		speedInfoLbl.managedProperty().bind(speedInfoLbl.visibleProperty());
		timeInfoLbl.managedProperty().bind(timeInfoLbl.visibleProperty());
	}

	public void setPrefWidth(double width) {
		((BorderPane) chartArea.getParent()).setPrefWidth(width);
	}

	public void setPrefHeight(double height) {
		((BorderPane) chartArea.getParent()).setPrefWidth(height);
	}

	private void bindMouseEvents() {
		detailsLayer.prefHeightProperty().bind(chartArea.heightProperty());
		detailsLayer.prefWidthProperty().bind(chartArea.widthProperty());
		detailsLayer.setMouseTransparent(true);

		final Axis<Number> xAxis = elevationChart.getXAxis();
		final Axis<Number> yAxis = elevationChart.getYAxis();
		final Line yLine = new Line();
		yLine.setFill(Color.GRAY);
		yLine.setStrokeWidth(1.0);
		yLine.setVisible(false);

		final Node chartBackground = elevationChart.lookup(".chart-plot-background");
		for (Node n : chartBackground.getParent().getChildrenUnmodifiable()) {
			if (n != chartBackground && n != xAxis && n != yAxis) {
				n.setMouseTransparent(true);
			}
		}
		chartBackground.setCursor(Cursor.CROSSHAIR);
		chartBackground.setOnMouseEntered((event) -> {
			chartBackground.getOnMouseMoved().handle(event);
			detailsPopup.setVisible(true);
			yLine.setVisible(true);
			detailsLayer.getChildren().addAll(yLine);
		});
		chartBackground.setOnMouseExited((event) -> {
			detailsPopup.setVisible(false);
			yLine.setVisible(false);
			detailsLayer.getChildren().removeAll(yLine);
		});
		chartBackground.setOnMouseMoved(event -> {
			double x = event.getX() + chartBackground.getLayoutX();
			double y = event.getY() + chartBackground.getLayoutY();

			yLine.setStartX(x + 5);
			yLine.setEndX(x + 5);
			yLine.setStartY(10);
			yLine.setEndY(detailsLayer.getHeight() - 10);

			updateChartInfo(event);

			if (y + detailsPopup.getHeight() + 10 < chartArea.getHeight()) {
				AnchorPane.setTopAnchor(detailsPopup, y + 10);
			} else {
				AnchorPane.setTopAnchor(detailsPopup, y - 10 - detailsPopup.getHeight());
			}

			if (x + detailsPopup.getWidth() + 10 < chartArea.getWidth()) {
				AnchorPane.setLeftAnchor(detailsPopup, x + 10);
			} else {
				AnchorPane.setLeftAnchor(detailsPopup, x - 10 - detailsPopup.getWidth());
			}
		});
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
	public void updateStatContent(float altMin, float altMax, float denivPos, float denivNeg, long dureeTot,
			long dureeSansPause, float distance) {
		altMinTF.setText((int) altMin + "m");
		altMaxTF.setText((int) altMax + "m");
		denivPosTF.setText(dist2String(denivPos));
		denivNegTF.setText(dist2String(denivNeg));
		dureeTotTF.setText(time2String(dureeTot, true));
		dureeSansPauseTF.setText(time2String(dureeSansPause, true));
		vitMoyTF.setText(distDuree2Vitesse(distance, dureeTot));
		vitMoySansPauseTF.setText(distDuree2Vitesse(distance, dureeSansPause));
		distanceTF.setText(dist2String(distance));
	}

	/**
	 * Calcule et retourne la vitesse en km/h.
	 *
	 * @param distance
	 *            en mètre
	 * @param duree
	 *            en secondes
	 * @return
	 */
	private String distDuree2Vitesse(double distance, double duree) {
		double vitesse = 0;
		if (duree != 0) {
			vitesse = (distance / 1000) / (duree / 3600);
		}

		return String.format("%.1f km/h", vitesse);
	}

	/**
	 * Chaîne représentant une distance avec l'unité adéquate.
	 *
	 * @return Distance en mètres (si inférieure à 1km) ou kilomètre sinon.
	 */
	private String dist2String(double distance) {
		if (distance < 1000) { // Moins d'1km -> afficher en mètres
			return String.format(Locale.getDefault(), "%d m", (int) distance);
		} else { // Afficher en kilomètres
			return String.format(Locale.getDefault(), "%.3f km", distance / 1000f);
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

		resetFromElevationSource(null);
	}

	/**
	 * Redessiner la courbe et recalculer les statistiques avec les données
	 * d'altitude appropriées.
	 *
	 * @param e
	 */
	@FXML
	private void resetFromElevationSource(ActionEvent e) {
		// Identification de la source
		boolean elevFromGPS = gpsElevBtn.isSelected();

		// Effacer les anciennes données
		elevationSerie.getData().clear();
		speedSerie.getData().clear();

		// Constantes pour analyse
		final float SEUIL_VITESSE = 1.5f; // en km/h (calcul des pauses)
		final float MIN_DELTA_ALT = 10f; // en m (lissage des altitudes)
		final float MAX_DELTA_ALT = 50f; // en m (lissage des altitudes)

		GeoLocation lastLoc = null;
		float denivPos = 0;
		float denivNeg = 0;
		float altMin = Float.POSITIVE_INFINITY;
		float altMax = Float.NEGATIVE_INFINITY;
		float speedMin = Float.POSITIVE_INFINITY;
		float speedMax = Float.NEGATIVE_INFINITY;
		boolean pauseDetectee = false;
		long debutPause = 0; // Instant de départ de la pause
		long dureePause = 0; // Durée de la pause
		float deltaElev;
		float cumulDist = 0;
		XYChart.Data<Number, Number> dataElevation = null;
		XYChart.Data<Number, Number> dataSpeed = null;
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
			if (loc.speed > speedMax) {
				speedMax = loc.speed;
			}
			if (loc.speed < speedMin) {
				speedMin = loc.speed;
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
				loc.length = (int) cumulDist;
			} else {
				loc.length = 0;
			}

			// Compléter les courbes
			dataElevation = new XYChart.Data<>(loc.length / 1000., loc.dispElevation);
			elevationSerie.getData().add(dataElevation);
			dataSpeed = new XYChart.Data<>(loc.length / 1000., loc.speed);
			speedSerie.getData().add(dataSpeed);

			// Se souvenir de cette géolocalisation à la prochaine itération.
			lastLoc = loc;
		}

		System.out.println(dataElevation);
		System.out.println(dataSpeed);

		// Adapter les échelles des axes verticaux
		NumberAxis elevationYAxis = (NumberAxis) elevationChart.getYAxis();
		elevationYAxis.setAutoRanging(false);
		int min = ((int) altMin / 100) * 100;
		elevationYAxis.setLowerBound(min);
		int max = ((int) (altMax + 99) / 100) * 100;
		elevationYAxis.setUpperBound(max);
		elevationYAxis.setTickUnit((max - min) / 10);

		NumberAxis speedYAxis = (NumberAxis) speedChart.getYAxis();
		speedYAxis.setAutoRanging(false);
		min = 0;
		speedYAxis.setLowerBound(min);
		max = ((int) (speedMax + 9) / 10) * 10;
		speedYAxis.setUpperBound(max);
		speedYAxis.setTickUnit((max - min) / 10);

		// Afficher les statistiques du parcours
		if (lastLoc != null) {
			updateStatContent(altMin, altMax, denivPos, denivNeg, lastLoc.timeStampS - geoLoc.get(0).timeStampS,
					lastLoc.timeStampS - geoLoc.get(0).timeStampS - dureePause, cumulDist);
		} else {
			updateStatContent(altMin, altMax, 0, 0, 0, 0, 0);
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
			resetFromElevationSource(null);
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

	public void updateChartInfo(MouseEvent event) {
		// Distance
		double xValue = (double) elevationChart.getXAxis().getValueForDisplay(event.getX());
		distanceInfoLbl.setText(String.format("%.3f km", xValue));

		if (geoLoc != null && geoLoc.size() > 0) {
			GeoLocation g = findGeoLoc(xValue * 1000);
			if (g != null) {
				elevationInfoLbl.setVisible(true);
				speedInfoLbl.setVisible(true);
				timeInfoLbl.setVisible(true);

				elevationInfoLbl.setText(String.format("%.0f m", g.dispElevation));
				speedInfoLbl.setText(String.format("%.2f km/h", g.speed));
				// Durée
				long duree = g.timeStampS - geoLoc.get(0).timeStampS;
				timeInfoLbl.setText(time2String(duree, true));
				return;
			}
		}
		elevationInfoLbl.setVisible(false);
		speedInfoLbl.setVisible(false);
		timeInfoLbl.setVisible(false);

	}

	public GeoLocation findGeoLoc(double dist) {
		GeoLocation lastLoc = null;
		for (GeoLocation g : geoLoc) {
			if (g.length > dist) {
				if (lastLoc == null) {
					return g;
				} else {
					if (Math.abs(g.length - dist) < Math.abs(lastLoc.length - dist)) {
						return g;
					} else {
						return lastLoc;
					}
				}
			}
			lastLoc = g;
		}

		return null;
	}
}
