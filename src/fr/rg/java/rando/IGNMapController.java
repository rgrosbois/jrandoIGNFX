package fr.rg.java.rando;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;

import javax.imageio.ImageIO;

import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Point2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.image.WritablePixelFormat;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polyline;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class IGNMapController {
	static final double DEFAULT_LATITUDE = 45.145f;
	static final double DEFAULT_LONGITUDE = 5.72f;
	static final int TILE_PIXEL_DIM = 256;
	static String cleIGN = "ldg97k7coed6xk53ji8hryvm"; // -> 10 mai 2016

	@FXML
	Pane contentPane;

	@FXML
	ImageView mapView;

	@FXML
	ScrollPane scrollPane;

	@FXML
	ProgressBar progressBar;

	int nTileX = 5;
	int nTileY = 5;
	int ignScale = 15;
	Point2D orig;
	Stage infoStage;
	boolean useCache = true;
	boolean ortho = false;

	public void initialize() {
		// Afficher la carte autour de la dernière position courante
		GeoLocation lastGeoLoc = new GeoLocation(DEFAULT_LONGITUDE, DEFAULT_LATITUDE);
		loadIGNMap(lastGeoLoc);
	}

	/**
	 * Récupérer et afficher les tuiles entourant et contenant la
	 * géolocalisation spécifiée.
	 *
	 * @param centerLoc
	 *            Géolocalisation centrale
	 */
	void loadIGNMap(GeoLocation centerLoc) {
		// Prévoir une nouvelle image
		WritableImage mapImg = new WritableImage(TILE_PIXEL_DIM * nTileX, TILE_PIXEL_DIM * nTileY);
		mapView.setImage(mapImg);

		// Identifier la tuile centrale et les tuiles limites
		int tileRowCenter = WMTS.latToTileRow(centerLoc.latitude, ignScale);
		int tileColCenter = WMTS.longToTileCol(centerLoc.longitude, ignScale);
		int tileRowMin = tileRowCenter - nTileY / 2;
		int tileRowMax = tileRowCenter + nTileY / 2;
		int tileColMin = tileColCenter - nTileX / 2;
		int tileColMax = tileColCenter + nTileX / 2;
		TileLoadingService tls = new TileLoadingService();
		progressBar.progressProperty().bind(tls.progressProperty());
		tls.setBounds(tileColMin, tileColMax, tileRowMin, tileRowMax);
		tls.start();

		// Centrer la carte autour de la position courante
		orig = new Point2D(tileColMin * WMTS.getTileDim(ignScale), tileRowMin * WMTS.getTileDim(ignScale));
		double xmin = tileColMin * WMTS.getTileDim(ignScale);
		double xmax = tileColMax * WMTS.getTileDim(ignScale);
		double x = WMTS.longToWmtsX(centerLoc.longitude);
		scrollPane.hvalueProperty().setValue((x - xmin) / (xmax - xmin));
		double ymin = tileRowMin * WMTS.getTileDim(ignScale);
		double ymax = tileRowMax * WMTS.getTileDim(ignScale);
		double y = WMTS.latToWmtsY(centerLoc.latitude);
		scrollPane.vvalueProperty().setValue((y - ymin) / (ymax - ymin));
	}

	@FXML
	void showInfo(ActionEvent e) {
		System.out.println("\nhValue=" + scrollPane.hvalueProperty().toString());
		System.out.println("vValue=" + scrollPane.vvalueProperty().toString());
	}

	@FXML
	void loadKMLTrack(ActionEvent e) {
		// Sélectionner le fichier
		FileChooser chooser = new FileChooser();
		FileChooser.ExtensionFilter filter = new FileChooser.ExtensionFilter("fichier KML (*.kml)", "*.kml");
		chooser.getExtensionFilters().add(filter);

		File file = chooser.showOpenDialog(contentPane.getScene().getWindow());
		if (file == null || !file.exists())
			return;

		// Extraction des géolocalisations du fichier KML
		HashMap<String, Object> infoKML = new KMLReader().extractLocWithStAXCursor(file.getAbsolutePath());
		ArrayList<GeoLocation> list = (ArrayList<GeoLocation>) infoKML.get(KMLReader.LOCATIONS_KEY);

		// Charger la nouvelle carte IGN
		loadIGNMap(list.get(0));

		// Créer et afficher la trace correspondante
		Polyline trace = new Polyline();
		double wmtsX, wmtsY, x, y;
		for (GeoLocation loc : list) {
			wmtsX = WMTS.longToWmtsX(loc.longitude) - orig.getX();
			x = wmtsX / WMTS.getTileDim(ignScale) * TILE_PIXEL_DIM;
			wmtsY = WMTS.latToWmtsY(loc.latitude) - orig.getY();
			y = wmtsY / WMTS.getTileDim(ignScale) * TILE_PIXEL_DIM;
			trace.getPoints().add(x);
			trace.getPoints().add(y);
		}
		trace.setStroke(Color.BLUE);
		trace.setStrokeWidth(3);
		contentPane.getChildren().add(trace);

		// Créer et afficher les courbes de dénivelé
		infoStage = new Stage();
		infoStage.setTitle("Informations");
		try {
			FXMLLoader loader = new FXMLLoader(getClass().getResource("info_ihm.fxml"));
			Parent root = loader.load();
			Scene scene = new Scene(root);
			infoStage.setScene(scene);
			infoStage.show();

			InfoControler ic = loader.getController();
			ic.setTrace(infoKML);
		} catch (IOException e1) {
			System.err.println("Erreur d'ouverture du FXML des informations");
			e1.printStackTrace();
		}
	}

	@FXML
	void exitApp(ActionEvent e) {
		System.exit(0);
	}

	/**
	 * Service pour le téléchargement des tuiles d'une carte.
	 */
	class TileLoadingService extends Service<Void> {

		int tileColMin, tileColMax, tileRowMin, tileRowMax;

		void setBounds(int tcMin, int tcMax, int trMin, int trMax) {
			tileColMin = tcMin;
			tileColMax = tcMax;
			tileRowMin = trMin;
			tileRowMax = trMax;
		}

		@Override
		protected Task<Void> createTask() {

			/**
			 * Tâche de récupération des tuiles de la carte
			 */
			return new Task<Void>() {

				@Override
				protected Void call() throws Exception {
					URL url;
					HttpURLConnection connection;
					Image img = null;
					PixelReader pxReader;
					PixelWriter pxWriter = ((WritableImage) mapView.getImage()).getPixelWriter();
					String key;
					File cacheFile;

					// Répertoire cache
					File localTileCacheDir = new File(System.getProperty("user.home") + File.separator + ".jrandoIGN"
							+ File.separator + "cache" + File.separator);
					if (!localTileCacheDir.exists()) {
						localTileCacheDir.mkdirs();
					}

					int[] buffer = new int[TILE_PIXEL_DIM * TILE_PIXEL_DIM];
					WritablePixelFormat<IntBuffer> pxFormat = PixelFormat.getIntArgbInstance();
					int maxIterations = (tileRowMax - tileRowMin + 1) * (tileColMax - tileColMin + 1);
					int iterations = 1;
					for (int r = tileRowMin; r <= tileRowMax; r++) {
						for (int c = tileColMin; c <= tileColMax; c++) {
							key = (ortho ? "ortho-" : "") + "z" + ignScale + "-r" + r + "-c" + c;
							cacheFile = new File(localTileCacheDir, key + ".jpg");
							if (useCache && cacheFile.exists()) { // Récupérer
																	// depuis le
								// cache
								img = new Image("file:" + cacheFile.getAbsolutePath());
							} else {
								// System.out.println("Téléchargement
								// "+r+"x"+c);
								try {
									url = new URL("http://gpp3-wxs.ign.fr/" + cleIGN + "/wmts/?"
											+ "SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0"
											+ (ortho ? "&LAYER=ORTHOIMAGERY.ORTHOPHOTOS"
													: "&LAYER=GEOGRAPHICALGRIDSYSTEMS.MAPS")
											+ "&STYLE=normal" + "&TILEMATRIXSET=PM&TILEMATRIX=" + ignScale + "&TILEROW="
											+ r + "&TILECOL=" + c + "&FORMAT=image/jpeg");
									connection = (HttpURLConnection) url.openConnection();
									// connection = (HttpURLConnection) url
									// .openConnection(new
									// Proxy(Proxy.Type.HTTP, new
									// InetSocketAddress("172.16.0.1", 3128)));
									connection.setRequestProperty("Referer", "http://localhost/IGN/");
									img = new Image(connection.getInputStream());
									// Enregistrer dans le cache
									if (useCache) {
										ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", cacheFile);
									}
								} catch (IOException e) {
									System.out.println("Erreur de connexion au service IGN");
									e.printStackTrace();
								}
							}
							// Recopier la tuile dans l'image finale
							pxReader = img.getPixelReader();
							pxReader.getPixels(0, 0, TILE_PIXEL_DIM, TILE_PIXEL_DIM, pxFormat, buffer, 0,
									TILE_PIXEL_DIM);
							pxWriter.setPixels((c - tileColMin) * TILE_PIXEL_DIM, (r - tileRowMin) * TILE_PIXEL_DIM,
									TILE_PIXEL_DIM, TILE_PIXEL_DIM, pxReader, 0, 0);
							updateProgress(iterations++, maxIterations);
						}
					}
					updateProgress(0, maxIterations);
					return null;
				}
			};
		}

	}
}
