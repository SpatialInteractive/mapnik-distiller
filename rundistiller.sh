#!/bin/bash
td=$(cd $(dirname $0);pwd)
cp="$td/build/classes"

for i in $td/lib/jar/*.jar $td/lib/jts/*.jar $td/build/depend/*.jar
do
	cp="$cp:$i"
done

java -Xmx768m -Djava.library.path=$td/build/depend -classpath $cp net.rcode.mapnikdistiller.DistillerMain "$@"



