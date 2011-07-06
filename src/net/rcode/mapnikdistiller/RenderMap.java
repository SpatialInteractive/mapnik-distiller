package net.rcode.mapnikdistiller;

import java.text.DecimalFormat;

import mapnik.Box2d;
import mapnik.Image;
import mapnik.Map;
import mapnik.Projection;
import mapnik.Renderer;

public class RenderMap {

	public static void main(String[] args) throws Exception {
		Globals.initialize();
		
		String source=args[0];
		String dest=args[1];
		
		Map map=new Map();
		map.loadMap(args[0], false);
		map.setWidth(2048);
		map.setHeight(2048);
		
		Box2d box=new Box2d(-122.370364, 47.578587, -122.278354, 47.621646);
		//Box2d box=new Box2d(-122.318159, 47.615296, -122.312795, 47.618738);
		Projection proj=new Projection(map.getSrs());
		proj.forward(box);
		
		DecimalFormat fmt=new DecimalFormat("0.0");
		System.out.println("Projected bounds: " + fmt.format(box.minx) + "," + 
				fmt.format(box.miny) + "," + 
				fmt.format(box.maxx) + "," + 
				fmt.format(box.maxy));
		map.zoomToBox(box);
		
		Image image=new Image(map.getWidth(), map.getHeight());
		long startTime=System.currentTimeMillis();
		Renderer.renderAgg(map, image);
		System.err.println("Render completed in " + ((System.currentTimeMillis()-startTime)/1000.0) + "s");
		image.saveToFile(dest, "png");
	}
}
