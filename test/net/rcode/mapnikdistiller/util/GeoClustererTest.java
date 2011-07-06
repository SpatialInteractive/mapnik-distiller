package net.rcode.mapnikdistiller.util;

import mapnik.Box2d;

import org.junit.Test;
import static org.junit.Assert.*;

public class GeoClustererTest {

	@Test
	public void testSimple() {
		GeoClusterer c=new GeoClusterer(new Box2d(-10,-10,10,10), 3);
		long code=c.calculate(9, 9);
		assertEquals(0, code);
		
		code=c.calculate(-9, -9);
		assertEquals(42, code);	// 101010
		
		code=c.calculate(-9, 9);
		assertEquals(21, code);	// 010101
		
		code=c.calculate(9, -9);
		assertEquals(63, code);	// 111111
		
		code=c.calculate(0.1, 0.1);
		assertEquals(10, code);	// 001010
		
		code=c.calculate(-0.1, 0.1);
		assertEquals(31, code);	// 011111
		
		code=c.calculate(-0.1, -0.1);
		assertEquals(32, code);	// 100000
		
		code=c.calculate(0.1, -0.1);
		assertEquals(53, code);	// 110101
		//System.out.println("Code=" + code);
	}
}
