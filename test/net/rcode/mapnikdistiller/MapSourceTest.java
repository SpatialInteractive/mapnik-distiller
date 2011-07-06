package net.rcode.mapnikdistiller;

import java.io.File;
import java.io.FileWriter;

import org.junit.Test;

public class MapSourceTest {

	@Test
	public void testLoad() throws Exception {
		MapSource source=new MapSource(new File("../external/mapquest-style/mapquest-us.xml"));
		String s=source.saveToString();
		System.out.println("Loaded source:" + s);
		FileWriter out=new FileWriter("build/test.xml");
		out.write(s);
		out.close();
	}
}
