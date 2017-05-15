package fr.rg.java.rando;

import java.awt.MouseInfo;
import java.awt.Point;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Iterator;
import java.util.prefs.Preferences;

import fr.rg.java.rando.util.Peer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

public class Main extends Application {
	// Sauvegarde du dernier répertoire de fichier KML consulté
	static final String KML_DIR_KEY = "last_used_dir";
	// Sauvegarde de la latitude
	static final String SAVED_LATITUDE_KEY = "saved_latitude";
	// Sauvegarde de la longitude
	static final String SAVED_LONGITUDE_KEY = "saved_longitude";
	// Sauvegarde de la clé IGN
	static final String IGNKEY_KEY = "ign_key";

	static final double DEFAULT_LATITUDE = 45.145f;
	static final double DEFAULT_LONGITUDE = 5.72f;
	static final String DEFAULT_IGNKEY = "ry9bshqmzmv1gao9srw610oq";

	// Reférences vers les contrôleurs de fenêtres
	IGNMapController mapController;
	InfoControler infoController;
	PeersController peersController;

	// Communauté réseau
	BufferedReader netIn;
	PrintWriter netOut;
	Socket commSocket;
	String netMsg = "";
	final ObservableList<Peer> peerList = FXCollections.observableArrayList();

	// Préférences utilisateur
	Preferences prefs = Preferences.userNodeForPackage(Main.class);

	Stage mainStage; // Fenêtre principale

	/**
	 * Dimensionner et créer la fenêtre principale puis lancer les services
	 * réseau.
	 */
	@Override
	public void start(Stage primaryStage) {
		mainStage = primaryStage;

		// Récupérer la taille de l'écran courant pour dimensionner
		// la fenêtre et calculer le nombre de tuiles nécessaires
		Point p = MouseInfo.getPointerInfo().getLocation(); // position de la
															// souris
		Screen currentScreen = Screen.getPrimary(); // écran primaire par défaut
		for (Screen s : Screen.getScreens()) { // chercher l'écran contenant la
												// souris
			if (s.getBounds().contains(p.x, p.y)) {
				currentScreen = s;
			}
		}
		// La fenêtre a des dimensions égales à 13/15 de l'écran courant
		Rectangle2D screenBounds = currentScreen.getBounds();
		int stageWidth = (int) (13 * screenBounds.getWidth() / 15);
		int stageHeight = (int) (13 * screenBounds.getHeight() / 15);

		// Nombre impair de tuiles suivant les lignes et les colonnes
		// et permettant de couvrir 4 fois l'écran
		int numTileX = (int) Math.floor(screenBounds.getWidth() / IGNMapController.TILE_PIXEL_DIM);
		numTileX = 2 * numTileX + 1;
		int numTileY = (int) Math.floor(screenBounds.getHeight() / IGNMapController.TILE_PIXEL_DIM);
		numTileY = 2 * numTileY + 1;

		// Charger la scène et configurer la fenêtre
		try {
			FXMLLoader loader = new FXMLLoader(getClass().getResource("/res/Map_ihm.fxml"));
			Parent root = loader.load();
			mapController = ((IGNMapController) loader.getController());
			mapController.setMainInstance(this);
			mapController.setTilesNumber(numTileX, numTileY);
			Scene scene = new Scene(root);

			primaryStage.setScene(scene);
			primaryStage.setResizable(false);
			primaryStage.setX(screenBounds.getMinX() + screenBounds.getWidth() / 15);
			primaryStage.setY(screenBounds.getMinY() + screenBounds.getHeight() / 15);
			primaryStage.setWidth(stageWidth);
			primaryStage.setHeight(stageHeight);

			primaryStage.setTitle("Parcours de randonnées");
			primaryStage.setOnCloseRequest(new EventHandler<WindowEvent>() {

				@Override
				public void handle(WindowEvent event) {
					// Fermer les connexions réseau
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

					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					// Fermer les connexions réseau sur fenêtre fille
					if (peersController != null) {
						peersController.closeTCPConnection();
					}

					System.exit(0);
				}
			});
			primaryStage.show();
		} catch (Exception e) {
			e.printStackTrace();
		}

		// +-----------------+
		// | Services réseau |
		// +-----------------+
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
					pkt = new DatagramPacket(buf, buf.length, InetAddress.getByName(Peer.MULTICAST_ADDRESS),
							Peer.MULTICAST_PORT);
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
						Platform.runLater(new Runnable() {

							@Override
							public void run() {
								long instant = System.currentTimeMillis();
								Iterator<Peer> it = peerList.iterator();
								while (it.hasNext()) {
									Peer peer = it.next();
									int ttl = (int) ((Peer.DEAD_INTERVAL - (instant - peer.getTimeStamp())) / 1000);
									System.out.println("  ttl=" + ttl);
									if (ttl > 0) {
										peer.setTtl(ttl);
									} else {
										it.remove();
									}
								}
							}
						});

						Thread.sleep(Peer.HELLO_INTERVAL);
					} catch (InterruptedException | IOException e) {
						if (!isCancelled()) {
							e.printStackTrace();
						}
					}
				}
				return null;
			}
		};
		new Thread(beaconTask).start();

		// Écouter les balises émises par les pairs tout en mettant à jour
		// la liste des pairs
		Task<Void> listenTask = new Task<Void>() {

			@Override
			protected Void call() throws Exception {
				MulticastSocket s;
				byte[] buf = new byte[1500];
				DatagramPacket pkt = new DatagramPacket(buf, buf.length);

				try {
					// Rejoindre le groupe de multidiffusion
					s = new MulticastSocket(Peer.MULTICAST_PORT);
					InetAddress group = InetAddress.getByName(Peer.MULTICAST_ADDRESS);
					s.joinGroup(group);

					// Attendre les datagrammes et identifier les expéditeurs
					while (!isCancelled()) {
						s.receive(pkt); // attendre un message
						String addresse = pkt.getAddress().getHostAddress();
						long timeStamp = System.currentTimeMillis();
						System.out.println("- Réception balise");

						// Ajouter le pair à la liste (ou la mettre à jour)
						Platform.runLater(new Runnable() {

							@Override
							public void run() {
								Iterator<Peer> it = peerList.iterator();
								while (it.hasNext()) {
									if (it.next().getIpAddress().equals(addresse)) {
										it.remove();
									}
								}
								peerList.add(new Peer(addresse, timeStamp));
							}
						});

					}

					// Quitter le groupe de multidiffusion
					s.leaveGroup(group);
					s.close();
				} catch (IOException e) {
					if (!isCancelled()) {
						e.printStackTrace();
					}
				}
				return null;
			}
		};
		new Thread(listenTask).start();

		// Serveur TCP
		Task<Void> tcpServerTask = new Task<Void>() {

			@Override
			protected Void call() throws Exception {
				ServerSocket listenSocket = new ServerSocket(Peer.MULTICAST_PORT);

				while (!isCancelled()) {
					// Attendre le prochain client et ouvrir les canaux de
					// communications
					commSocket = listenSocket.accept();

					netIn = new BufferedReader(new InputStreamReader(commSocket.getInputStream()));
					netOut = new PrintWriter(commSocket.getOutputStream(), true);
					BufferedOutputStream netOutB = new BufferedOutputStream(commSocket.getOutputStream());

					String msg = netIn.readLine();
					while (msg != null && !msg.equals("QUIT")) {
						switch (msg) {
						case "INFO":
							netOut.println("Dépôt KML : " + prefs.get(Main.KML_DIR_KEY, "/tmp"));
							netOut.println("Clé IGN : " + prefs.get(Main.IGNKEY_KEY, Main.DEFAULT_IGNKEY));
							netOut.println("Géolocalisation : "
									+ prefs.getDouble(Main.SAVED_LONGITUDE_KEY, Main.DEFAULT_LONGITUDE) + ", "
									+ prefs.getDouble(Main.SAVED_LATITUDE_KEY, Main.DEFAULT_LATITUDE));
							break;
						case "FILES":
							File curDir = new File(prefs.get(Main.KML_DIR_KEY, "/tmp"));
							for (File file : curDir.listFiles()) {
								netOut.println(file.getName());
							}
							break;
						case "GET_FILE":
							msg = netIn.readLine(); // nom du fichier
							File file = new File(prefs.get(Main.KML_DIR_KEY, "/tmp"), msg);
							if (file.exists()) {
								// Envoyer la taille du fichier
								int len = (int) file.length();
								netOut.println("" + len);
								// Envoyer le fichier
								BufferedInputStream inFile = new BufferedInputStream(new FileInputStream(file));
								byte[] buf = new byte[len];
								inFile.read(buf, 0, len);
								netOutB.write(buf, 0, len);
								netOutB.flush();
								inFile.close();
							} else {
								netOut.println("" + 0); // longueur nulle
							}
							break;
						default:
							netOut.println(msg + " non supporté");
							break;
						}
						netOut.println("END");

						msg = netIn.readLine();
					}

					// Fermer les communications
					netIn.close();
					netOut.close();
				}
				listenSocket.close();

				return null;
			}
		};
		new Thread(tcpServerTask).start();
	}

	/**
	 * Lancer le processus de traitement d'un fichier KML par le contrôleur de
	 * la carte IGN.
	 *
	 * @param file
	 *            Fichier à traiter
	 */
	void loadLocalKMLTrack(File file) {
		mapController.loadLocalKMLTrack(file);
	}

	void showPeerWindow() {
		if (peersController == null) { // Créer la fenêtre
			// Boîte de dialogue pour afficher les pairs
			try {
				FXMLLoader loader = new FXMLLoader(getClass().getResource("/res/Peers_ihm.fxml"));
				Parent root = loader.load();
				peersController = (PeersController) loader.getController();
				peersController.setMainInstance(this);
				peersController.setPeerList(peerList);
				Scene scene;
				scene = new Scene(root);

				Stage peerStage = new Stage();
				peerStage.initOwner(mainStage);
				peerStage.setScene(scene);
				peerStage.setTitle("Liste de pairs");
				peerStage.show();
				peerStage.show();

				peerStage.setOnCloseRequest(new EventHandler<WindowEvent>() {

					@Override
					public void handle(WindowEvent event) {
						peersController = null;
					}
				});
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
	}

	/**
	 * Lancer le traitement de la nouvelle trace pour la fenêtre d'affichage des
	 * statistiques de la trace. Créer et faire apparaître cette fenêtre si
	 * nécessaire
	 *
	 * @param infoKML
	 */
	void showTrackInfoWindow(HashMap<String, Object> infoKML) {
		if (infoController == null) { // Réutiliser la fenêtre existante
			try {
				FXMLLoader loader = new FXMLLoader(getClass().getResource("/res/Info_ihm.fxml"));
				Parent root = loader.load();
				infoController = loader.getController();
				Scene scene = new Scene(root);

				Stage infoStage = new Stage();
				infoStage.setTitle("Informations");
				infoStage.setScene(scene);
				infoStage.show();
			} catch (IOException e1) {
				e1.printStackTrace();
			}

		}
		infoController.setTrace(infoKML);
	}

	public static void main(String[] args) {
		launch(args);
	}
}
