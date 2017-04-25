package fr.rg.java.rando;

import java.awt.Point;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.imageio.ImageIO;

import fr.rg.java.rando.util.GeoLocation;
import fr.rg.java.rando.util.KMLReader;
import fr.rg.java.rando.util.WMTS;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Point2D;
import javafx.geometry.Side;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
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
import javafx.stage.Stage;

public class IGNMapController {
	// Sauvegarde du dernier répertoire de fichier KML consulté
	private static final String KML_DIR_KEY = "last_used_dir";
	// Sauvegarde de la latitude
	private static final String SAVED_LATITUDE_KEY = "saved_latitude";
	// Sauvegarde de la longitude
	private static final String SAVED_LONGITUDE_KEY = "saved_longitude";

	static final double DEFAULT_LATITUDE = 45.145f;
	static final double DEFAULT_LONGITUDE = 5.72f;
	static final int TILE_PIXEL_DIM = 256;
	static String cleIGN = "ry9bshqmzmv1gao9srw610oq"; // -> 19/11/2017

	Preferences prefs = Preferences.userNodeForPackage(this.getClass());

	// Zone où s'affiche à la fois la carte et les traces
	@FXML
	Pane contentPane;

	// Noeud contenant la carte
	@FXML
	ImageView mapView;

	// Gère l'affichage de la carte
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

	Stage infoStage; // fenêtre pour les courbes de dénivelés

	// Communauté réseau
	static final String MULTICAST_ADDRESS = "224.0.71.75";
	static final int MULTICAST_PORT = 7175;
	static final long HELLO_INTERVAL = 2000;
	static final long DEAD_INTERVAL = 10000;
	ObservableList<String> peerList = FXCollections.observableArrayList();
	ObservableList<Long> peerTimeStamp = FXCollections.observableArrayList();
	BufferedReader netIn;
	PrintWriter netOut;
	Socket commSocket;
	String netMsg = "";

	// Fenêtres
	Stage mainStage; // Fenêtre principale
	Stage peerStage; // Fenêtre des pairs
	ShowPeersController peerController;

	/**
	 * Fournir la référence de la fenêtre principale.
	 *
	 * @param mStage
	 *            fenêtre principale
	 */
	public void setMainStage(Stage mStage) {
		mainStage = mStage;
	}

	public void closeConnections() {
		try {
			if (netIn != null) {
				netIn.close();
			}
			if (netOut != null) {
				netOut.close();
			}
			if (commSocket != null) {
				commSocket.close();
			}
			if(peerController != null) {
				peerController.closeConnections();
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	/**
	 * Initialisation du contrôleur après le chargement du fichier FXML.
	 */
	@FXML
	void initialize() {
		// Géolocalisation initiale
		GeoLocation lastGeoLoc = new GeoLocation(prefs.getDouble(SAVED_LONGITUDE_KEY, DEFAULT_LONGITUDE),
				prefs.getDouble(SAVED_LATITUDE_KEY, DEFAULT_LATITUDE));

		// Centrer la carte autour de cette position
		loadIGNMap(lastGeoLoc);

		startNetworkServices();
	}

	/**
	 * Démarrer les services réseau de l'application:
	 * <ul>
	 * <li>Émission de balises vers les pairs.</li>
	 * <li>Écoute des balises émises par les pairs.</li>
	 * <li>Serveur TCP</li>
	 * </ul>
	 */
	private void startNetworkServices() {
		// Émettre des balises de présence sur le réseau
		Task<Void> beaconTask = new Task<Void>() {

			@Override
			protected Void call() throws Exception {
				// Configuration de la socket et préparation du paquet à émettre
				MulticastSocket s = null;
				String msg = "Hello";
				byte[] buf = msg.getBytes();
				DatagramPacket pkt = null;
				try {
					pkt = new DatagramPacket(buf, buf.length, InetAddress.getByName(MULTICAST_ADDRESS), MULTICAST_PORT);
					s = new MulticastSocket();
					s.setLoopbackMode(true); // Ne pas envoyer sur lo
				} catch (IOException e1) {
					e1.printStackTrace();
				}

				// Émission périodique
				while (!isCancelled()) {
					try {
						s.send(pkt);

						// Purger la liste des pairs
						Platform.runLater(() -> {
							long now = System.currentTimeMillis();
							Iterator<String> it = peerList.iterator();
							Iterator<Long> it2 = peerTimeStamp.iterator();
							while (it.hasNext()) {
								it.next();
								long time = it2.next();
								if ((now - time) > DEAD_INTERVAL) {
									it.remove();
									it2.remove();
								}
							}
						});

						Thread.sleep(HELLO_INTERVAL);
					} catch (InterruptedException | IOException e) {
						e.printStackTrace();
					}
				}
				return null;
			}
		};
		new Thread(beaconTask).start();

		// Écouter les balises émises par les pairs
		Task<Void> listenTask = new Task<Void>() {

			@Override
			protected Void call() throws Exception {
				MulticastSocket s;
				byte[] buf = new byte[1500];
				DatagramPacket pkt = new DatagramPacket(buf, buf.length);

				try {
					// Rejoindre le groupe de multidiffusion
					s = new MulticastSocket(MULTICAST_PORT);
					InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
					s.joinGroup(group);

					// Attendre les datagrammes et identifier les expéditeurs
					while (!isCancelled()) {
						s.receive(pkt); // attendre un message
						String peer = pkt.getAddress().getHostAddress();
						long timeStamp = System.currentTimeMillis();

						Platform.runLater(() -> {
							if (!peerList.contains(peer)) { // Nouveau pair
								peerList.add(peer);
								peerTimeStamp.add(timeStamp);
							} else { // Pair connu, mettre à jour le timeStamp
								peerTimeStamp.set(peerList.indexOf(peer), timeStamp);
							}
						});
					}

					// Quitter le groupe de multidiffusion
					s.leaveGroup(group);
					s.close();
				} catch (IOException e) {
				}
				return null;
			}
		};
		new Thread(listenTask).start();

		// Serveur TCP
		Task<Void> tcpServerTask = new Task<Void>() {

			@Override
			protected Void call() throws Exception {
				ServerSocket listenSocket = new ServerSocket(MULTICAST_PORT);

				boolean continuer = true;
				while (continuer) {
					// Attendre le prochain client et ouvrir les canaux de
					// communications
					commSocket = listenSocket.accept();

					netIn = new BufferedReader(new InputStreamReader(commSocket.getInputStream()));
					netOut = new PrintWriter(commSocket.getOutputStream(), true);

					String msg = netIn.readLine();
					while (msg != null && !msg.equals("QUIT")) {
						switch (msg) {
						case "INFO":
						default:
							netOut.println("coucou");
							netOut.println("END");
							break;
						}

						msg = netIn.readLine();
					}

					// Fermer les communications
					netIn.close();
					netOut.close();
					listenSocket.close();
				}

				return null;
			}
		};
		new Thread(tcpServerTask).start();
	}

	/**
	 * Récupérer et afficher les tuiles entourant et contenant la
	 * géolocalisation spécifiée.
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
		int tileRowMax = tileCenter.y + nTileY / 2;
		int tileColMin = tileCenter.x - nTileX / 2;
		int tileColMax = tileCenter.x + nTileX / 2;
		mapWmtsOrig = new Point2D(tileColMin * WMTS.getTileDim(ignScale), tileRowMin * WMTS.getTileDim(ignScale));
		TileLoadingService tls = new TileLoadingService();
		progressBar.progressProperty().bind(tls.progressProperty());
		tls.setBounds(tileColMin, tileColMax, tileRowMin, tileRowMax);
		tls.start();

		// Centrer la carte autour de la position courante
		double xmin = tileColMin * WMTS.getTileDim(ignScale);
		double xmax = tileColMax * WMTS.getTileDim(ignScale);
		double ymin = tileRowMin * WMTS.getTileDim(ignScale);
		double ymax = tileRowMax * WMTS.getTileDim(ignScale);
		Point2D p = WMTS.getWmtsDim(centerLoc.latitude, centerLoc.longitude);
		scrollPane.hvalueProperty().setValue((p.getX() - xmin) / (xmax - xmin));
		scrollPane.vvalueProperty().setValue((p.getY() - ymin) / (ymax - ymin));

		prefs.putDouble(SAVED_LATITUDE_KEY, centerLoc.latitude);
		prefs.putDouble(SAVED_LONGITUDE_KEY, centerLoc.longitude);
		try {
			prefs.flush();
		} catch (BackingStoreException ex) {
			ex.printStackTrace();
		}

	}

	@FXML
	void showInfo(ActionEvent e) {
		System.out.println("\nhValue=" + scrollPane.hvalueProperty().toString());
		System.out.println("vValue=" + scrollPane.vvalueProperty().toString());
	}

	/**
	 * Afficher la liste des pairs.
	 *
	 * @param e
	 */
	@FXML
	void showPeers(ActionEvent e) {
		// Boîte de dialogue pour afficher les pairs
		try {
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/res/Show_peers.fxml"));
			Parent root = loader.load();
			peerController = (ShowPeersController)loader.getController();
			Scene scene;
			scene = new Scene(root);

			if (peerStage == null) {
				peerStage = new Stage();
				peerStage.initOwner(mainStage);
			}
			peerStage.setScene(scene);
			peerStage.setTitle("Liste de pairs");
			peerStage.show();
			peerController.setPeerStage(peerStage);
			peerController.setPeerList(peerList);
		} catch (IOException e1) {
			e1.printStackTrace();
		}

	}

	/**
	 * Ouvrir une boîte de dialogue permettant de saisir l'adresse où recentrer
	 * la carte. Utilisation d'une fonction d'autosuggestion lorsque plus de 3
	 * caractères sont tapés sont la forme d'un menu contextuel.
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
		dialog.initOwner(mainStage);

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
		chooser.setInitialDirectory(new File(prefs.get(KML_DIR_KEY, "/tmp")));

		File file = chooser.showOpenDialog(contentPane.getScene().getWindow());
		if (file == null || !file.exists())
			return;

		// Enregistrer le répertoire courant
		prefs.put(KML_DIR_KEY, file.getParent());
		try {
			prefs.flush();
		} catch (BackingStoreException ex) {
			ex.printStackTrace();
		}

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
		if (infoStage == null) { // Réutiliser la fenêtre existante
			infoStage = new Stage();
			infoStage.setTitle("Informations");
		}
		try {
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/res/Info_ihm.fxml"));
			Parent root = loader.load();
			Scene scene = new Scene(root);
			infoStage.setScene(scene);
			infoStage.show();

			InfoControler ic = loader.getController();
			ic.setTrace(infoKML);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
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
