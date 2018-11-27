package fr.rg.java.rando;

import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.prefs.Preferences;

import javax.imageio.ImageIO;

import fr.rg.java.rando.util.GeoLocation;
import fr.rg.java.rando.util.WMTS;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.image.WritablePixelFormat;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polyline;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Screen;

public class IGNMapController {
	@FXML
	private ProgressBar progressBar;

	@FXML
	private ImageView mapView;

	@FXML
	private Pane layerWMTS;

	@FXML
	private Polyline trace;

	@FXML
	private ImageView currentLocIV;

	@FXML
	private ScrollPane mapScrollPane;

	int numTileX;
	int numTileY;
	static final int TILE_PIXEL_DIM = 256;
	public static final String DEFAULT_IGNKEY = "8qea2r2xtmjxox2esrfaft9k";

	int ignScale = 15;
	boolean useCache = true;
	boolean ortho = false;
	Point2D mapWmtsOrig; // Coordonnées WMTS de l'origine
	TileLoadingService tls = null;

	private MainController mainController;

	public void setMainController(MainController m) {
		mainController = m;
	}

	@FXML
	void initialize() {
		// Nombre impair de tuiles suivant les lignes et les colonnes
		// et permettant de couvrir ~4 fois l'ecran
		Point p = MouseInfo.getPointerInfo().getLocation();
		Screen currentScreen = Screen.getPrimary(); // écran primaire par défaut
		for (Screen s : Screen.getScreens()) { // rechercher écran courant
			if (s.getBounds().contains(p.x, p.y)) {
				currentScreen = s;
			}
		}
		Rectangle2D screenBounds = currentScreen.getBounds();

		int nTx = (int) Math.floor(screenBounds.getWidth() / TILE_PIXEL_DIM);
		numTileX = 2 * nTx + 1;
		int nTy = (int) Math.floor(screenBounds.getHeight() / TILE_PIXEL_DIM);
		numTileY = 2 * nTy + 1;

		// Nouvelle image modifiable
		WritableImage mapImg = new WritableImage(TILE_PIXEL_DIM * numTileX, TILE_PIXEL_DIM * numTileY);
		mapView.setImage(mapImg);

		// Position actuelle
		currentLocIV.setVisible(false);
	}

	/**
	 * Récupérer et afficher les tuiles entourant et contenant une géolocalisation
	 * et centrer l'affichage sur cette dernière.
	 *
	 * @param centerLoc
	 *            Géolocalisation à positionner au centre de la carte
	 */
	public void initMap(GeoLocation centerLoc) {
		// Identifier la tuile centrale et les tuiles limites
		Point tileCenter = WMTS.getTileIndex(centerLoc.longitude, centerLoc.latitude, ignScale);
		int tileRowMin = tileCenter.y - numTileY / 2;
		int tileColMin = tileCenter.x - numTileX / 2;

		// Coordonnées WMTS de l'origine de l'image
		mapWmtsOrig = new Point2D(tileColMin, tileRowMin).multiply(WMTS.getTileDim(ignScale));

		// Charger les tuiles dans un fil d'exécution distinct
		tls = new TileLoadingService(new Rectangle(tileColMin, tileRowMin, numTileX, numTileY));
		progressBar.progressProperty().bind(tls.progressProperty());
		tls.start();

		// Recentrer la carte sur la géolocalisation
		centerMapToLoc(centerLoc.longitude, centerLoc.latitude);
	}

	/**
	 * Afficher une Polyline à partir de la liste des géolocalisations de la trace.
	 *
	 * @param list
	 */
	public void setTrack(ArrayList<GeoLocation> list) {
		initMap(list.get(0));

		trace.getPoints().clear();
		Point2D p;
		double dist2px = TILE_PIXEL_DIM / WMTS.getTileDim(ignScale);
		for (GeoLocation loc : list) {
			// Coordonnées WMTS locales
			p = WMTS.getWmtsDim(loc.latitude, loc.longitude).subtract(mapWmtsOrig).multiply(dist2px);
			trace.getPoints().addAll(p.getX(), p.getY());
		}
	}

	/**
	 * Pointer une géolocalisation sur la carte.
	 *
	 * @param loc
	 */
	public void highlightLocation(GeoLocation loc) {
		if (loc == null) {
			currentLocIV.setVisible(false);
			return;
		}
		currentLocIV.setVisible(true);
		double dist2px = TILE_PIXEL_DIM / WMTS.getTileDim(ignScale);
		Point2D p = WMTS.getWmtsDim(loc.latitude, loc.longitude).subtract(mapWmtsOrig).multiply(dist2px);
		Bounds bd = currentLocIV.getLayoutBounds();
		currentLocIV.setX(p.getX() - bd.getWidth() / 2);
		currentLocIV.setY(p.getY() - bd.getHeight());
	}

	@FXML
	public void toggleGraphVisibility(ActionEvent e) {
		mainController.toggleHideGraph();
	}

	/**
	 * Ajouter un calque qui affiche les limites des tuiles WMTS ainsi que les
	 * longitudes et latitudes associées.
	 */
	@FXML
	public void toggleWMTSLayer(ActionEvent e) {
		ToggleButton wmtsGridBtn = (ToggleButton) e.getSource();
		if (wmtsGridBtn.isSelected()) {
			Color color = Color.CRIMSON;
			Font f = new Font(14);
			Point2D p2;
			Text t;
			javafx.scene.shape.Rectangle r;
			GeoLocation g;
			for (int j = 1; j < numTileX; j++) { // longitudes
				Line l = new Line(TILE_PIXEL_DIM * j, 0, TILE_PIXEL_DIM * j, numTileY * TILE_PIXEL_DIM);
				l.setStroke(color);
				// Valeur de la longitude
				p2 = mapWmtsOrig.add(j * WMTS.getTileDim(ignScale), 0);
				g = WMTS.getGeolocation(p2);
				t = new Text(TILE_PIXEL_DIM * j - 33, 16, String.format("%.6f", g.longitude));
				t.setStroke(color);
				t.setFont(f);
				// Arrière plan
				r = new javafx.scene.shape.Rectangle(TILE_PIXEL_DIM * j - 34, 1, 70, 18);
				r.setArcWidth(10);
				r.setArcHeight(10);
				r.setFill(Color.WHITE);
				layerWMTS.getChildren().addAll(l, r, t);
			}
			for (int i = 1; i < numTileY; i++) { // latitudes
				Line l = new Line(0, TILE_PIXEL_DIM * i, numTileX * TILE_PIXEL_DIM, TILE_PIXEL_DIM * i);
				l.setStroke(color);
				// Valeur de la latitude
				p2 = mapWmtsOrig.add(0, i * WMTS.getTileDim(ignScale));
				g = WMTS.getGeolocation(p2);
				t = new Text(2, TILE_PIXEL_DIM * i + 4, String.format("%.6f", g.latitude));
				t.setStroke(color);
				t.setFont(f);
				// Arrière plan
				r = new javafx.scene.shape.Rectangle(1, TILE_PIXEL_DIM * i - 9, 80, 18);
				r.setFill(Color.WHITE);
				r.setArcWidth(10);
				r.setArcHeight(10);
				layerWMTS.getChildren().addAll(l, r, t);
			}
		} else {
			layerWMTS.getChildren().clear();
		}
	}

	/**
	 * Centrer l'affichage de l'affichage de l'image sur une géolocalisation donnée.
	 *
	 * @param longitude
	 * @param latitude
	 */
	private void centerMapToLoc(double longitude, double latitude) {
		Point2D p = getMapCoords(latitude, longitude);

		// Dimension du ScrollPane
		double scWidth = mapScrollPane.getWidth();
		double scHeight = mapScrollPane.getHeight();

		mapScrollPane.hvalueProperty().setValue((p.getX() - scWidth / 2) / (numTileX * TILE_PIXEL_DIM - scWidth));
		mapScrollPane.vvalueProperty().setValue((p.getY() - scHeight / 2) / (numTileY * TILE_PIXEL_DIM - scHeight));
	}

	/**
	 * Calculer les coordonnées d'une géolocalisation dans l'image de carte.
	 *
	 * @param latitude
	 * @param longitude
	 * @return
	 */
	private Point2D getMapCoords(double latitude, double longitude) {
		return WMTS.getWmtsDim(latitude, longitude).subtract(mapWmtsOrig)
				.multiply(TILE_PIXEL_DIM / WMTS.getTileDim(ignScale));
	}

	/**
	 * Service pour le téléchargement des tuiles d'une carte.
	 */
	class TileLoadingService extends Service<Void> {

		// int tileColMin, tileColMax, tileRowMin, tileRowMax;
		Rectangle tilesBounds;

		public TileLoadingService(Rectangle bounds) {
			tilesBounds = bounds;
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

					// Récupérer les préférences
					Preferences prefs = Preferences.userNodeForPackage(Main.class);
					String cleIGN = prefs.get(Main.IGNKEY_KEY, DEFAULT_IGNKEY);
					String proxyHostname = prefs.get(Main.PROXY_HOSTNAME_KEY, "");
					String proxyPortNum = prefs.get(Main.PROXY_PORT_NUMBER_KEY, "0");

					// Répertoire cache
					File localTileCacheDir = new File(System.getProperty("user.home") + File.separator + ".jrandoIGN"
							+ File.separator + "cache" + File.separator);
					if (!localTileCacheDir.exists()) {
						localTileCacheDir.mkdirs();
					}

					// Tampon de la grande image à remplir à l'aide de tuiles
					int[] buffer = new int[TILE_PIXEL_DIM * TILE_PIXEL_DIM];
					WritablePixelFormat<IntBuffer> pxFormat = PixelFormat.getIntArgbInstance();

					// Créer une HashMap pour classer les tuiles en fonction de leur distance au
					// centre: 1 clé par distance, la valeur associée est une ArrayList de noms de
					// tuiles
					int dist;
					HashMap<Integer, ArrayList<String>> classement = new HashMap<>();
					ArrayList<String> a;
					int maxDist = 0;
					int r, c;
					for (r = tilesBounds.y; r < tilesBounds.y + tilesBounds.height; r++) { // Lignes de tuiles
						for (c = tilesBounds.x; c < tilesBounds.x + tilesBounds.width; c++) { // Colonnes de tuiles
							dist = (int) Math.sqrt(Math.pow(r - tilesBounds.y - tilesBounds.height / 2, 2)
									+ Math.pow(c - tilesBounds.x - tilesBounds.width / 2, 2));
							key = (ortho ? "ortho-" : "") + "z" + ignScale + "-r" + r + "-c" + c;

							// Créer l'Arraylist si nécessaire
							a = classement.get(dist);
							if (a == null) {
								classement.put(dist, new ArrayList<String>());
								a = classement.get(dist);
							}
							if (dist > maxDist) {
								maxDist = dist;
							}

							// Insérer l'élément
							a.add(key);
						}
					}

					// Récupérer les tuiles dans l'ordre de leur distance au centre
					int maxIterations = tilesBounds.width * tilesBounds.height;
					int iterations = 1;

					Iterator<String> it;
					String[] tInfo;
					for (int i = 0; i <= maxDist; i++) {
						a = classement.get(i);
						if (a == null) {
							continue;
						}
						it = a.iterator();
						while (it.hasNext()) { // Boucle sur les tuiles à la même distance
							key = it.next();

							// Récupérer les indices de colonne et ligne
							tInfo = key.split("-");
							r = 0;
							c = 0;
							if (tInfo.length == 3) {
								r = Integer.parseInt(tInfo[1].substring(1));
								c = Integer.parseInt(tInfo[2].substring(1));
							} else if (tInfo.length == 4) {
								r = Integer.parseInt(tInfo[2].substring(1));
								c = Integer.parseInt(tInfo[3].substring(1));
							}

							cacheFile = new File(localTileCacheDir, key + ".jpg");
							System.out.println(cacheFile);
							if (useCache && cacheFile.exists()) { // utiliser cache
								img = new Image("file:" + cacheFile.getAbsolutePath());
							} else {
								try {
									url = new URL("http://gpp3-wxs.ign.fr/" + cleIGN + "/wmts/?"
											+ "SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0"
											+ (ortho ? "&LAYER=ORTHOIMAGERY.ORTHOPHOTOS"
													: "&LAYER=GEOGRAPHICALGRIDSYSTEMS.MAPS")
											+ "&STYLE=normal" + "&TILEMATRIXSET=PM&TILEMATRIX=" + ignScale + "&TILEROW="
											+ r + "&TILECOL=" + c + "&FORMAT=image/jpeg");
									if (!"".equals(proxyHostname)) { // utiliser un proxy
										connection = (HttpURLConnection) url.openConnection(new Proxy(Proxy.Type.HTTP,
												new InetSocketAddress(proxyHostname, Integer.parseInt(proxyPortNum))));
									} else { // Pas de proxy
										connection = (HttpURLConnection) url.openConnection();
									}
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
							pxWriter.setPixels((c - tilesBounds.x) * TILE_PIXEL_DIM,
									(r - tilesBounds.y) * TILE_PIXEL_DIM, TILE_PIXEL_DIM, TILE_PIXEL_DIM, pxReader, 0,
									0);
							updateProgress(iterations++, maxIterations);
						}
					}

					// Réinitialiser la barre de progression
					updateProgress(0, maxIterations);
					return null;
				}
			};
		}

	}
}
