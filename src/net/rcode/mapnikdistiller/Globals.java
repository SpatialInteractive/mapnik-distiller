package net.rcode.mapnikdistiller;

import org.apache.log4j.BasicConfigurator;

import mapnik.DatasourceCache;
import mapnik.FreetypeEngine;
import mapnik.Mapnik;

import com.almworks.sqlite4java.SQLiteConnection;
import com.almworks.sqlite4java.SQLiteException;

/**
 * Global configuration and factories for the app.
 * @author stella
 *
 */
public class Globals {

	private static boolean inited;
	public static void initialize() {
		if (inited) return;
		BasicConfigurator.configure();
		initializeMapnik();
		inited=true;
	}
	
	public static void configureSQLite(SQLiteConnection db) throws SQLiteException {
		//db.exec("PRAGMA synchronous=OFF");
		//db.enableLoadExtension(true);
		//db.loadExtension("libspatialite.so", null);
	}

	private static boolean mapnikInited;
	public static void initializeMapnik() {
		if (mapnikInited) return;
		Mapnik.initialize();
		DatasourceCache.registerDatasources("/usr/local/lib/mapnik2/input");
		FreetypeEngine.registerFonts("/usr/local/lib/mapnik2/fonts");
		for (String pn: DatasourceCache.pluginNames()) {
			System.out.println("Found plugin: " + pn);
		}
		mapnikInited=true;
	}
	
}
