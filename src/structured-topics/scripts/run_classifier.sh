#!/bin/bash

BASEDIR="$HOME/pipeline"
JARS_BASEPATH="${BASEDIR}/jars"
JAR_ST="${JARS_BASEPATH}/structured-topics-0.0.1-SNAPSHOT_with_dependencies_2015_11_13_13_46.jar"
RUN_JAVA=~/jdk8/jdk1.8.0_60/bin/java
JAVA_PARAMS='-Xms4G -Xmx10G'

if [ $# -ne 2 ]
then 
	echo 'missing parameters (expected 2 got '$#'). usage: run_classifier.sh pipeline_result_folder input_string'
	exit
fi

#remove trailing /
result_folder=${1%/}
input_string=$2

echo 'query: '${input_string}

clusters="${BASEDIR}/${result_folder}/2_clustering/clusters.csv"
index_dir="${BASEDIR}/${result_folder}/3_index"

echo 'using index: '${index_dir}


# execute search on index
${RUN_JAVA} ${JAVA_PARAMS} -cp ${JAR_ST} \
de.tudarmstadt.lt.structuredtopics.classify.Searcher \
"${index_dir}" \
"${clusters}" \
"${input_string}"


