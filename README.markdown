mapnik-distiller
================
I haven't had time to document this yet.

Basically, the idea here is that we take a regular mapnik xml file and distill it.  The result is a mapnik xml file
and a set of sqlite databases.  All external datasources are condensed into the sqlite databases in a highly optimized
way organized on disk such that typical map renders will run as fast as possible.

As an example of the principle, I started with an OSM PostGIS extract database of North America plus Central America that was
about 90GB on disk.  I set the distiller to produce four detail levels (each detail level is its own mapnik.xml + sqlite dbs).

* DL1: Raw (unsimplified) data needed to render lowest scale levels (zoom >= 15)
* DL2: Simplified data needed to render medium scale levels (zoom >= 11 && zoom < 15)
* DL3: Simplified data needed to render high scale levels (zoom <= 7)
* DL4: Simplified data needed to render global views (zoom <= 4)

Here is a directory listing of the results.  The files are standalone (minus some symbols and rasters in
the linked directories) and just need to be copied out to a map server to generate maps.

	-rw-r--r-- 1 stella stella 2.0G 2011-07-05 00:08 mqstreet_dl1.index.sqlite
	-rw-r--r-- 1 stella stella 143K 2011-07-05 16:16 mqstreet_dl1.mapnik.xml
	-rw-r--r-- 1 stella stella  14G 2011-07-05 00:08 mqstreet_dl1.sqlite
	-rw-r--r-- 1 stella stella 2.0G 2011-07-05 00:08 mqstreet_dl2.index.sqlite
	-rw-r--r-- 1 stella stella 143K 2011-07-05 16:16 mqstreet_dl2.mapnik.xml
	-rw-r--r-- 1 stella stella 5.0G 2011-07-05 00:08 mqstreet_dl2.sqlite
	-rw-r--r-- 1 stella stella 555M 2011-07-04 23:47 mqstreet_dl3.index.sqlite
	-rw-r--r-- 1 stella stella 132K 2011-07-05 16:16 mqstreet_dl3.mapnik.xml
	-rw-r--r-- 1 stella stella 1.1G 2011-07-04 23:47 mqstreet_dl3.sqlite
	-rw-r--r-- 1 stella stella 104M 2011-07-04 23:48 mqstreet_dl4.index.sqlite
	-rw-r--r-- 1 stella stella 117K 2011-07-05 16:16 mqstreet_dl4.mapnik.xml
	-rw-r--r-- 1 stella stella 130M 2011-07-04 23:48 mqstreet_dl4.sqlite
	lrwxrwxrwx 1 stella stella   49 2011-07-05 16:22 symbols -> ../../../external/mapquest-style/mapquest_symbols
	lrwxrwxrwx 1 stella stella   33 2011-07-05 16:19 world_boundaries -> /extra/data/world_boundaries_data

The data is organized internally to optimize locality during rendering.  The result is better cache utilization
and a larger percentage of "linear" reads on cache miss.  With these data files I have been able to render arbitrary
OSM tiles on my 6 year old PC with 2GB of RAM within a couple hundred milliseconds each.

I'd be happy to share the files to collaborate with anyone on this further, but they are big and I haven't figured out where
to post them yet.

Stay tuned...

