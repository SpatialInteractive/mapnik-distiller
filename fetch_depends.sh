#!/bin/bash
cd $(dirname $0)
t=$(pwd)

lib=$t/lib
mkdir -p $lib
dest=$lib

verify_hash() {
	local file="$1"
	local url="$2"
	local sumprog="$3"
	
	if [ -z "$sumprog" ]; then
		sumprog="${url##*.}sum"
	fi
	
	local server_sum="$(curl -f $url 2>/dev/null | awk '{print $1}')"
	if [ -z "$server_sum" ]; then
		echo "No server sum for $url"
		return 1
	fi
	
	local local_sum="$($sumprog $file | awk '{print $1}')"
	#local_sum="$server_sum"
	if [ "$local_sum" != "$server_sum" ]; then
		echo "Hash sums do not match for $(basename file):"
		echo "   server='$server_sum'"
		echo "   local ='$local_sum'"
		return 1
	fi
	
	return 0
}

fetch() {
	local url=$1
	local sumurl="$2"
	local file="$3"
	
	if [ -z "$file" ]; then
		file=$(basename $url)
	fi

	if ! [ -z "$sumurl" ]; then
		if ! [[ $sumurl =~ '/' ]]; then
			sumurl="$url.$sumurl"
			#echo "Expanding sum url to $sumurl"
		fi
	fi
	
	mkdir -p $dest
	file=$dest/$file
	
	if ! [ -f $file ]; then
		echo "Downloading $url -> $file"
		if ! ( curl -f $url > $file ); then
			echo "Failed to download $url"
			rm $file > /dev/null 2>&1
			return 1
		fi
	fi
	
	if ! [ -z "$sumurl" ] && [ -f $file ]; then
		if ! ( verify_hash $file $sumurl ); then
			echo "File $file does not match its checksum"
			rm $file > /dev/null 2>&1
			return 1
		fi
	fi
}

fetch_jar() {
	dest=$lib/jar
	fetch "$@"
}

fetch_source() {
	dest=$lib/src
	fetch "$@"
}

fetch_maven() {
	local baseurl=$1
	shift
	local module
	local version
	
	while true
	do
		module="$1"
		version="$2"
		shift && shift
		
		if [ -z "$module" ] || [ -z "$version" ]; then
			return 0
		fi
		
		echo "Fetching maven depends $module:$version from $baseurl"
		fetch_jar $baseurl/$module/$version/$module-$version.jar sha1
		fetch_source $baseurl/$module/$version/$module-$version-sources.jar sha1
	done
}

# sqlite4java
sqlite4java_version=0.213
fetch_maven  http://repo2.maven.org/maven2/com/almworks/sqlite4java  \
	sqlite4java $sqlite4java_version \
	libsqlite4java-osx $sqlite4java_version \
	libsqlite4java-linux-amd64	$sqlite4java_version

# dom4j
fetch_maven http://repo2.maven.org/maven2/dom4j \
	dom4j 1.6.1
	
# SLF4j
fetch_maven http://repo2.maven.org/maven2/org/slf4j \
	slf4j-api 1.6.1 \
	slf4j-log4j12 1.6.1 \
	jul-to-slf4j 1.6.1
	
# log4j
fetch_maven http://repo2.maven.org/maven2/log4j \
	log4j 1.2.16
	
# commons
fetch_maven http://repo2.maven.org/maven2/commons-codec \
	commons-codec 1.5
fetch_maven http://repo2.maven.org/maven2/commons-lang \
	commons-lang 2.6
	
