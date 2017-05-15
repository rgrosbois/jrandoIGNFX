package fr.rg.java.rando.util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class Peer {
	// Constantes
	public static final String MULTICAST_ADDRESS = "224.0.71.75";
	public static final int MULTICAST_PORT = 7175;
	public static final int HELLO_INTERVAL = 2000; // 2s
	public static final int DEAD_INTERVAL = 10000; // 10s

	// Mise en forme de l'heure
	public static DateFormat df = new SimpleDateFormat("HH:mm:ss", Locale.FRANCE);

	// Attributs
	private final SimpleStringProperty ipAddress;
	private long timeStamp; // instant en ms
	private SimpleIntegerProperty ttl; // durée de vie restante (en s)

	/**
	 * Constructeur.
	 *
	 * @param address
	 *            adresse IPv
	 * @param time
	 *            Dernier instant de réception d'une balise
	 */
	public Peer(String address, long time) {
		ipAddress = new SimpleStringProperty(address);
		timeStamp = time;
		ttl = new SimpleIntegerProperty(DEAD_INTERVAL/1000);
	}

	public final String getIpAddress() {
		return ipAddress.get();
	}

	public final void setIpAddress(String ip) {
		ipAddress.set(ip);
	}

	public StringProperty ipAddProperty() {
		return ipAddress;
	}

	public long getTimeStamp() {
		return timeStamp;
	}

	public void setTimeStamp(long time) {
		timeStamp = time;
	}

	public final void setTtl(int t) {
		ttl.setValue(t);
	}

	public final int getTtl() {
		return ttl.getValue();
	}

	public IntegerProperty ttlProperty() {
		return ttl;
	}

	public String toString() {
		return ipAddress.get() + df.format(new Date(timeStamp));
	}
}
