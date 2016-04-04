#!/bin/bash

#directory where folders will be generated and files are located.
#this directory should contain a folder 1_ddts where ddts are located and a file foundSenses.csv with all babelnet senses to use
BASEDIR="$HOME/experiment2"

#java and jar locations
RUN_JAVA=~/jdk8/jdk1.8.0_60/bin/java
RUN_ST_JAR='-Xms4G -Xmx25G -cp /home/haftstein/pipeline/jars/structured-topics-0.0.1-SNAPSHOT_with_dependencies.jar'
RUN_CW_JAR='-Xms4G -Xmx25G -cp /home/haftstein/pipeline/jars/chinese-whispers.jar'

#options used by the cw clustering
CW_OPTIONS=("TOP" "DIST_NOLOG" "DIST_LOG")

##no modifications below this line should be necessary

#ensure sort order
export LC_ALL=C

export _JAVA_OPTIONS=-Djava.io.tmpdir=/home/haftstein/tmp

LOG_DIR=$BASEDIR/0_logs
DDT_DIR=$BASEDIR/1_ddts
SIM_DIR=$BASEDIR/2_similarities
CLUSTER_DIR=$BASEDIR/3_clusters
MAPPING_DIR=$BASEDIR/4_mappings
BNET_SENSES=$BASEDIR/foundSenses.csv

rm -r $LOG_DIR
mkdir $LOG_DIR

### Similarities ###

echo "calculating similarities, cleaning $SIM_DIR"
rm -r $SIM_DIR
mkdir $SIM_DIR

for ddt in $DDT_DIR/*
do

	name=${ddt##*/}
	echo "all similarities for "$name
	simFile=all-similarities-$name

	java ${RUN_ST_JAR} \
	de.tudarmstadt.lt.structuredtopics.similarity.SenseSimilarityCalculator \
	-in $ddt \
	-out "$SIM_DIR/$simFile.tmp" \
	-ALL 2>&1 | tee $LOG_DIR/"$simFile.log.txt"

	echo "sorting"
	zcat "$SIM_DIR/$simFile.tmp" | sort -k1,1 -k3,3rg | gzip -9 > "$SIM_DIR/$simFile"
	rm "$SIM_DIR/$simFile.tmp"


        simFile2=lucene-similarities-$name

        java ${RUN_ST_JAR} \
        de.tudarmstadt.lt.structuredtopics.similarity.SenseSimilarityCalculator \
        -in $ddt \
        -out "$SIM_DIR/$simFile2.tmp" \
        -N 100 2>&1 | tee $LOG_DIR/"$simFile2.log.txt"

        echo "sorting"
        zcat "$SIM_DIR/$simFile2.tmp" | sort -k1,1 -k3,3rg | gzip -9 > "$SIM_DIR/$simFile2"
        rm "$SIM_DIR/$simFile2.tmp"


done

### Clusters ###

rm -r $CLUSTER_DIR
mkdir $CLUSTER_DIR

for sim in $SIM_DIR/*
do

	simName=${sim##*/}
	
	for cwOption in ${CW_OPTIONS[@]}
	do
    		echo "clustering "$simName" with "$cwOption
		
		clusterFile=clusters-$cwOption-$simName
	
		java ${RUN_CW_JAR}\
		de.tudarmstadt.lt.cw.global.CWGlobal \
		-in $sim \
		-N 1000 \
		-cwOption $cwOption \
		-out $CLUSTER_DIR/$clusterFile.tmp.gz 2>&1 | tee $LOG_DIR/$clusterFile.log
		
		echo "sorting clusters"
		zcat $CLUSTER_DIR/$clusterFile.tmp.gz | sort -k2,2rn | gzip -9 > $CLUSTER_DIR/$clusterFile
		rm $CLUSTER_DIR/$clusterFile.tmp.gz

	done

	


done

### Mappings ###

rm -r $MAPPING_DIR
mkdir $MAPPING_DIR


for cluster in $CLUSTER_DIR/*
do

	clusterName=${cluster##*/}
	echo "mappings for "$clusterName

	mappingFile=domains-$clusterName	

	java ${RUN_ST_JAR} \
	de.tudarmstadt.lt.structuredtopics.evaluate.MapClustersToBabelnetSenses \
	-bnetSenses $BNET_SENSES \
	-clusters $cluster \
	-out $MAPPING_DIR/$mappingFile 2>&1 | tee $LOG_DIR/$mappingFile.log

done


echo "Finished!"
