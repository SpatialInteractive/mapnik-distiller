package net.rcode.mapnikdistiller;

import java.util.HashMap;
import java.util.Map;

/**
 * Speeds up caching of native maps generated from strings that should be
 * equivilent at different parts of the process
 * @author stella
 *
 */
public class NativeMapCache {
	private static Map<String, mapnik.Map> cache=new HashMap<String, mapnik.Map>();
	
	public static mapnik.Map get(String xml) {
		return cache.get(xml);
	}
	
	public static void put(String xml, mapnik.Map map) {
		cache.put(xml, map);
	}
}
