package fr.rg.java.rando;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

public class ShowPeersController {
	@FXML
	ListView<String> listView;

	Stage peerStage;
	String netMsg = "";
	Socket commSocket;
	BufferedReader netIn;
	PrintWriter netOut;

	void setPeerStage(Stage stage) {
		peerStage = stage;
	}

	void setPeerList(ObservableList<String> peerList) {
		listView.setItems(peerList);
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
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	@FXML
	void initialize() {
		// Gestion du clic
		listView.setOnMouseClicked((event) -> {
			String host = (String) ((ListView) event.getSource()).getSelectionModel().getSelectedItem();
			new Thread(new Task<Void>() {

				@Override
				protected Void call() throws Exception {
					connectToPeer(host, peerStage);
					return null;
				}
			}).start();
		});
	}

	/**
	 * Connexion TCP avec un pair.
	 *
	 * @param host
	 *            adresse IP du pair
	 * @param lv
	 *            ListView pour afficher les résultats de communication.
	 */
	private void connectToPeer(String host, Stage stage) {
		// Nouvelle scène
		TextArea ta = new TextArea();
		ta.setEditable(false);
		Scene scene = new Scene(new AnchorPane(ta));

		Platform.runLater(new Runnable() {

			@Override
			public void run() {
				stage.setTitle(host);
				stage.setScene(scene);
			}
		});

		try { // Connexion au pair
			commSocket = new Socket(host, IGNMapController.MULTICAST_PORT);
			netIn = new BufferedReader(new InputStreamReader(commSocket.getInputStream()));
			netOut = new PrintWriter(commSocket.getOutputStream(), true);

			// Demande d'informations
			netOut.println("INFO");

			// Réception de la réponse terminant par END
			String reponse = netIn.readLine();
			while (!"END".equals(reponse)) {
				netMsg += reponse + "\n";
				reponse = netIn.readLine();
			}

			// Terminer la discussion
			netOut.println("QUIT");

			// Fermer les canaux de communication
			netOut.close();
			netIn.close();
			commSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		Platform.runLater(new Runnable() {

			@Override
			public void run() {
				ta.setText(netMsg);
			}
		});
	}

}
