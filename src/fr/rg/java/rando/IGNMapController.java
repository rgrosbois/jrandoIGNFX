package fr.rg.java.rando;

import java.awt.Point;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.imageio.ImageIO;

import fr.rg.java.rando.util.AddressSuggestionService;
import fr.rg.java.rando.util.GeoLocation;
import fr.rg.java.rando.util.KMLReader;
import fr.rg.java.rando.util.WMTS;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.geometry.Side;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
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
import javafx.stage.Modality;

public class IGNMapController {
	static final int TILE_PIXEL_DIM = 256;
	public static String cleIGN;

	// Préférences utilisateur
	Preferences prefs = Preferences.userNodeForPackage(Main.class);

	// Zone où s'affiche à la fois la carte et les traces
	@FXML
	Pane contentPane;

	// Noeud contenant la carte
	@FXML
	ImageView mapView;

	// Gère le défilement de la carte
	@FXML
	ScrollPane scrollPane;

	// Barre de progression pour les tâches longues
	@FXML
	ProgressBar progressBar;

	// Propriétés de la carte IGN
	int nTileX = 5;
	int nTileY = 5;
	int ignScale = 15;
	Point2D mapWmtsOrig; // Coordonnées WMTS de l'origine
	boolean useCache = true;
	boolean ortho = false;
	Polyline trace; // trace courante

	Main main; // Classe principale

	/**
	 * Fournir la référence de la fenêtre principale.
	 *
	 * @param mStage
	 *            fenêtre principale
	 */
	public void setMainInstance(Main m) {
		main = m;
	}

	/**
	 * Initialisation du contrôleur après le chargement du fichier FXML.
	 */
	@FXML
	void initialize() {
		// Clé IGN
		cleIGN = prefs.get(Main.IGNKEY_KEY, Main.DEFAULT_IGNKEY); // 19/11/2017

	}

	/**
	 * Spécifier le nombre de tuiles à afficher sur la carte. Ce nombre
	 * dépendant de la taille de la fenêtre, cette méthode doit donc être
	 * appelée aussitôt après le chargement du fichier FXML de la scène.
	 *
	 * @param tX
	 *            nombre (impair) de tuiles selon l'horizontale
	 * @param tY
	 *            nombre (impair) de tuiles selon la verticale
	 */
	void setTilesNumber(int tX, int tY) {
		nTileX = tX;
		nTileY = tY;

		// Géolocalisation initiale
		GeoLocation lastGeoLoc = new GeoLocation(prefs.getDouble(Main.SAVED_LONGITUDE_KEY, Main.DEFAULT_LONGITUDE),
				prefs.getDouble(Main.SAVED_LATITUDE_KEY, Main.DEFAULT_LATITUDE));

		// Centrer la carte autour de cette position
		loadIGNMap(lastGeoLoc);

		// Pour le premier recentrage, il faut attendre que le scrollPane ait
		// ses dimensions finales
		scrollPane.widthProperty().addListener(new ChangeListener<Number>() {

			@Override
			public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
				if (scrollPane.getWidth() > 0 && scrollPane.getWidth() < mapView.getImage().getWidth()) {
					setScrollPaneCenter(lastGeoLoc.longitude, lastGeoLoc.latitude);
					scrollPane.widthProperty().removeListener(this);
				}
			}
		});
		scrollPane.heightProperty().addListener(new ChangeListener<Number>() {

			@Override
			public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
				if (scrollPane.getHeight() > 0 && scrollPane.getHeight() < mapView.getImage().getHeight()) {
					setScrollPaneCenter(lastGeoLoc.longitude, lastGeoLoc.latitude);
					scrollPane.heightProperty().removeListener(this);
				}
			}
		});
	}

	/**
	 * Récupérer et afficher les tuiles entourant et contenant la
	 * géolocalisation spécifiée et centrer l'affichage sur cette position.
	 *
	 * @param centerLoc
	 *            Géolocalisation centrale
	 */
	void loadIGNMap(GeoLocation centerLoc) {
		// Nouvelle image modifiable
		WritableImage mapImg = new WritableImage(TILE_PIXEL_DIM * nTileX, TILE_PIXEL_DIM * nTileY);
		mapView.setImage(mapImg);

		// Identifier la tuile centrale et les tuiles limites
		Point tileCenter = WMTS.getTileIndex(centerLoc.longitude, centerLoc.latitude, ignScale);
		int tileRowMin = tileCenter.y - nTileY / 2;
		int tileColMin = tileCenter.x - nTileX / 2;

		// Coordonnées WMTS de l'origine du repère
		mapWmtsOrig = new Point2D(tileColMin, tileRowMin).multiply(WMTS.getTileDim(ignScale));

		// Chargement des tuiles dans un fil d'exécution distinct
		TileLoadingService tls = new TileLoadingService(new Rectangle(tileColMin, tileRowMin, nTileX, nTileY));
		progressBar.progressProperty().bind(tls.progressProperty());
		tls.start();

		// Centrer la carte sur la géolocalisation
		setScrollPaneCenter(centerLoc.longitude, centerLoc.latitude);

		// Sauvegarder le nouveau centre de la carte
		prefs.putDouble(Main.SAVED_LATITUDE_KEY, centerLoc.latitude);
		prefs.putDouble(Main.SAVED_LONGITUDE_KEY, centerLoc.longitude);
		try {
			prefs.flush();
		} catch (BackingStoreException ex) {
			ex.printStackTrace();
		}

	}

	/**
	 * Centrer l'affichage de l'affichage de l'image sur une géolocalisation
	 * donnée.
	 *
	 * @param longitude
	 * @param latitude
	 */
	private void setScrollPaneCenter(double longitude, double latitude) {
		// Calculer les coordonnées en pixels dans l'image
		Point2D p = WMTS.getWmtsDim(latitude, longitude).subtract(mapWmtsOrig)
				.multiply(TILE_PIXEL_DIM / WMTS.getTileDim(ignScale));
		// Dimension du ScrollPane
		double scWidth = scrollPane.getWidth();
		double scHeight = scrollPane.getHeight();
		scrollPane.hvalueProperty().setValue((p.getX() - scWidth / 2) / (nTileX * TILE_PIXEL_DIM - scWidth));
		scrollPane.vvalueProperty().setValue((p.getY() - scHeight / 2) / (nTileY * TILE_PIXEL_DIM - scHeight));
	}

	/**
	 * Identifier les coordonnées du centre du scrollpane dans l'image
	 *
	 * @return
	 */
	private Point2D getScrollPaneCenter() {
		Image img = mapView.getImage();
		double minX = scrollPane.getHvalue() * (img.getWidth() - scrollPane.getWidth());
		double minY = scrollPane.getVvalue() * (img.getHeight() - scrollPane.getHeight());
		return new Point2D(minX + scrollPane.getWidth() / 2, minY + scrollPane.getHeight() / 2);
	}

	/**
	 * Identifier les coordonnées WMTS du centre de la carte.
	 *
	 * @return
	 */
	private Point2D getMapCenterWMTSCoordinates() {
		Image img = mapView.getImage();
		double x = scrollPane.getHvalue() * (img.getWidth() - scrollPane.getWidth()) + scrollPane.getWidth() / 2;
		double y = scrollPane.getVvalue() * (img.getHeight() - scrollPane.getHeight()) + scrollPane.getHeight() / 2;
		x = x / img.getWidth() * nTileX;
		y = y / img.getHeight() * nTileY;
		return new Point2D(x, y).multiply(WMTS.getTileDim(ignScale)).add(mapWmtsOrig);
	}

	/**
	 * Décaler l'affichage de la carte d'une tuile vers la gauche (= Ouest).
	 *
	 * @param e
	 */
	@FXML
	void translateLeft(ActionEvent e) {
		loadIGNMap(
				WMTS.getGeolocation(getMapCenterWMTSCoordinates().subtract(new Point2D(WMTS.getTileDim(ignScale), 0))));
		if (trace != null && trace.getPoints().size() != 0) {
			ObservableList<Double> list = trace.getPoints();
			for (int i = 0; i < list.size(); i += 2) {
				list.set(i, list.get(i) + TILE_PIXEL_DIM);
			}
		}
	}

	/**
	 * Décaler l'affichage de la carte d'une tuile vers la droite (= Est).
	 *
	 * @param e
	 */
	@FXML
	void translateRight(ActionEvent e) {
		loadIGNMap(WMTS.getGeolocation(getMapCenterWMTSCoordinates().add(new Point2D(WMTS.getTileDim(ignScale), 0))));
		if (trace != null && trace.getPoints().size() != 0) {
			ObservableList<Double> list = trace.getPoints();
			for (int i = 0; i < list.size(); i += 2) {
				list.set(i, list.get(i) - TILE_PIXEL_DIM);
			}
		}
	}

	/**
	 * Décaler l'affichage de la carte d'une tuile vers le haut (= Nord).
	 *
	 * @param e
	 */
	@FXML
	void translateUp(ActionEvent e) {
		loadIGNMap(
				WMTS.getGeolocation(getMapCenterWMTSCoordinates().subtract(new Point2D(0, WMTS.getTileDim(ignScale)))));
		if (trace != null && trace.getPoints().size() != 0) {
			ObservableList<Double> list = trace.getPoints();
			for (int i = 1; i < list.size(); i += 2) {
				list.set(i, list.get(i) + TILE_PIXEL_DIM);
			}
		}
	}

	/**
	 * Décaler l'affichage de la carte d'une tuile vers le bas (= Sud).
	 *
	 * @param e
	 */
	@FXML
	void translateDown(ActionEvent e) {
		loadIGNMap(WMTS.getGeolocation(getMapCenterWMTSCoordinates().add(new Point2D(0, WMTS.getTileDim(ignScale)))));
		if (trace != null && trace.getPoints().size() != 0) {
			ObservableList<Double> list = trace.getPoints();
			for (int i = 1; i < list.size(); i += 2) {
				list.set(i, list.get(i) - TILE_PIXEL_DIM);
			}
		}
	}

	/**
	 * Ouvrir une boîte de dialogue pour éditer la clé IGN utilisée par
	 * l'application.
	 *
	 * @param e
	 */
	@FXML
	void modifyIGNKey(ActionEvent e) {
		TextInputDialog dialog = new TextInputDialog(prefs.get(Main.IGNKEY_KEY, Main.DEFAULT_IGNKEY));
		dialog.setTitle("Clé IGN");
		dialog.setHeaderText("Clé utilisée pour télécharger les tuiles IGN");
		dialog.setContentText("Clé:");
		dialog.initModality(Modality.WINDOW_MODAL);
		dialog.initOwner(contentPane.getScene().getWindow());

		// Afficher la boîte de dialogue
		Optional<String> result = dialog.showAndWait();

		// Traiter l'éventuelle réponse
		result.ifPresent((cle) -> {
			prefs.put(Main.IGNKEY_KEY, cle);
			try {
				prefs.flush();
			} catch (BackingStoreException ex) {
				ex.printStackTrace();
			}
		});
	}

	@FXML
	void changerZoomIGN(ActionEvent e) {
		RadioMenuItem item = (RadioMenuItem) e.getSource();

		int newScale = item.getText().contains("15") ? 15 : 16;
		if (newScale != ignScale) {
			ignScale = newScale;
			loadIGNMap(WMTS.getGeolocation(getMapCenterWMTSCoordinates()));

			// Modifier la trace -> à revoir
			if (trace != null && trace.getPoints().size() != 0) {
				ObservableList<Double> list = trace.getPoints();
				for (int i = 0; i < list.size(); i += 2) {
					list.set(i, list.get(i) + TILE_PIXEL_DIM);
				}
			}
		}
	}

	/**
	 * Ouvrir une boîte de dialogue pour saisir l'adresse où recentrer la carte.
	 * Apparition de suggestions (sous la forme d'un menu contextuel) lorsque
	 * plus de 3 caractères sont entrés.
	 *
	 * @param e
	 */
	@FXML
	void saisirAdresse(ActionEvent e) {
		ContextMenu listeSuggest = new ContextMenu();
		listeSuggest.hide();

		TextInputDialog dialog = new TextInputDialog();
		dialog.setTitle("Adresse");
		dialog.setHeaderText("Rechercher une adresse");
		dialog.setContentText("adresse:");
		dialog.initModality(Modality.WINDOW_MODAL);
		dialog.initOwner(contentPane.getScene().getWindow());

		// Autocomplétion
		TextField tf = dialog.getEditor();
		tf.textProperty().addListener(new ChangeListener<String>() {

			@Override
			public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
				// uniquement si plus de 3 caractères et que la chaîne
				// est tapée à la main (i.e. pas après un clic sur une
				// suggestion)
				if (Math.abs(newValue.length() - oldValue.length()) <= 1 && newValue.length() >= 3) {
					// Configurer une recherche de 5 suggestions
					AddressSuggestionService as = new AddressSuggestionService(newValue, 5);

					// Remplir le menu contextuel avec les suggestions trouvées
					as.setOnSucceeded((WorkerStateEvent event) -> {
						// Récupérer la liste de géolocalisations
						ArrayList<GeoLocation> loc = (ArrayList<GeoLocation>) event.getSource().getValue();

						if (loc.size() > 0) { // Liste contenant au moins 1
												// élément
							// Remplir une liste d'item de menu cliquables
							List<CustomMenuItem> menuItems = new LinkedList<>();
							for (int i = 0; i < loc.size(); i++) {
								String result = loc.get(i).address;
								CustomMenuItem item = new CustomMenuItem(new Label(result), true);
								menuItems.add(item);

								// Si clic sur la suggestion, remplir le
								// TextField et cacher le menu
								item.setOnAction((ActionEvent e1) -> {
									tf.setText(result);
									listeSuggest.hide();
								});
							}

							// Remplacer le contenu du menu avec celui
							// de la nouvelle liste.
							listeSuggest.getItems().clear();
							listeSuggest.getItems().addAll(menuItems);

							// Afficher le menu en dessous du TextField
							listeSuggest.show(tf, Side.BOTTOM, 0, 0);
						} else { // liste vide -> cacher le menu contextuel
							listeSuggest.hide();
						}
					});
					// lancer la recherche
					as.start();
				}
			}
		});

		// Afficher la boîte de dialogue
		Optional<String> result = dialog.showAndWait();

		// Traiter l'éventuelle réponse
		result.ifPresent(address -> {
			// Lancer une nouvelle recherche d'adresse et utiliser la première
			// réponse valide
			AddressSuggestionService as = new AddressSuggestionService(address, 5);
			as.setOnSucceeded((WorkerStateEvent event) -> {
				ArrayList<GeoLocation> loc = (ArrayList<GeoLocation>) event.getSource().getValue();
				if (loc.size() > 0) {
					// Supprimer une éventuelle trace en mémoire
					trace = null;
					// Recentrer la carte
					loadIGNMap(loc.get(0));
				}
			});
			as.start();
		});

	}

	/**
	 * Lire une trace depuis un fichier KML et l'afficher en surimpression sur
	 * la carte.
	 *
	 * @param e
	 */
	@FXML
	void loadKMLTrack(ActionEvent e) {
		// Sélecteur de fichier
		FileChooser chooser = new FileChooser();
		FileChooser.ExtensionFilter filter = new FileChooser.ExtensionFilter("fichier KML (*.kml)", "*.kml");
		chooser.getExtensionFilters().add(filter);

		// Répertoire fichiers KML
		chooser.setInitialDirectory(new File(prefs.get(Main.KML_DIR_KEY, "/tmp")));

		File file = chooser.showOpenDialog(contentPane.getScene().getWindow());
		if (file == null || !file.exists())
			return;

		// Enregistrer le répertoire courant
		prefs.put(Main.KML_DIR_KEY, file.getParent());
		try {
			prefs.flush();
		} catch (BackingStoreException ex) {
			ex.printStackTrace();
		}

		loadLocalKMLTrack(file);
	}

	void loadLocalKMLTrack(File file) {
		// Extraction des géolocalisations du fichier KML
		HashMap<String, Object> infoKML = new KMLReader().extractLocWithStAXCursor(file.getAbsolutePath());
		ArrayList<GeoLocation> list = (ArrayList<GeoLocation>) infoKML.get(KMLReader.LOCATIONS_KEY);

		// Charger la nouvelle carte IGN
		loadIGNMap(list.get(0));

		// Créer et afficher la trace correspondante
		if (trace == null) {
			trace = new Polyline();
			trace.setStroke(Color.BLUE);
			trace.setStrokeWidth(3);
			contentPane.getChildren().add(trace);
		} else { // supprimer les points de la trace existante
			trace.getPoints().clear();
		}
		Point2D p;
		double dist2px = TILE_PIXEL_DIM / WMTS.getTileDim(ignScale);
		for (GeoLocation loc : list) {
			// Coordonnées WMTS locales
			p = WMTS.getWmtsDim(loc.latitude, loc.longitude).subtract(mapWmtsOrig).multiply(dist2px);
			trace.getPoints().addAll(p.getX(), p.getY());
		}

		// Créer et afficher les courbes de dénivelé
		main.showTrackInfoWindow(infoKML);
	}

	/**
	 * Terminer l'application.
	 *
	 * @param e
	 */
	@FXML
	void exitApp(ActionEvent e) {
		System.exit(0);
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

					// Répertoire cache
					File localTileCacheDir = new File(System.getProperty("user.home") + File.separator + ".jrandoIGN"
							+ File.separator + "cache" + File.separator);
					if (!localTileCacheDir.exists()) {
						localTileCacheDir.mkdirs();
					}

					int[] buffer = new int[TILE_PIXEL_DIM * TILE_PIXEL_DIM];
					WritablePixelFormat<IntBuffer> pxFormat = PixelFormat.getIntArgbInstance();
					int maxIterations = tilesBounds.width * tilesBounds.height;
					int iterations = 1;
					for (int row = 0; row < tilesBounds.height; row++) {
						for (int col = 0; col < tilesBounds.width; col++) {
							key = (ortho ? "ortho-" : "") + "z" + ignScale + "-r" + (row + tilesBounds.y) + "-c"
									+ (col + tilesBounds.x);
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
											+ (row + tilesBounds.y) + "&TILECOL=" + (col + tilesBounds.x)
											+ "&FORMAT=image/jpeg");
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
							pxWriter.setPixels(col * TILE_PIXEL_DIM, row * TILE_PIXEL_DIM, TILE_PIXEL_DIM,
									TILE_PIXEL_DIM, pxReader, 0, 0);
							updateProgress(iterations++, maxIterations);
						}
					}
					updateProgress(0, maxIterations);
					return null;
				}
			};
		}

	}

	@FXML
	void showPeers(ActionEvent e) {
		main.showPeerWindow();
	}

}
