package net.rcode.mapnikdistiller;

import java.io.File;

import com.almworks.sqlite4java.SQLiteConnection;

public class SqliteTest {
	public static void main(String[] args) throws Exception {
		SQLiteConnection db=new SQLiteConnection(new File("test.db"));
		db.open(true);
		db.enableLoadExtension(true);
		//db.exec("select load_extension('/usr/local/lib/libspatialite.dylib')");
		db.loadExtension("libspatialite.dylib", null);
	}
}
