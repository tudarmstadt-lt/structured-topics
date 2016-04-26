#!/bin/bash

#ensure sort order
export LC_ALL=C

EXPERIMENT_DIR=~/experiment2

LOG_DIR=$EXPERIMENT_DIR/0_logs
DDT_DIR=$EXPERIMENT_DIR/1_ddts
SIM_DIR=$EXPERIMENT_DIR/2_similarities
CLUSTER_DIR=$EXPERIMENT_DIR/3_clusters
MAPPING_DIR=$EXPERIMENT_DIR/4_mappings
BNET_SENSES=$EXPERIMENT_DIR/foundSenses.csv
RESULT_FILE=$EXPERIMENT_DIR/results_aggregated.csv

RUN_ST_JAR='-Xms4G -Xmx30G -cp /path/to/structured-topics-0.0.1-SNAPSHOT_with_dependencies.jar'
RUN_CW_JAR='-Xms4G -Xmx30G -cp /path/to/chinese-whispers.jar'

CW_OPTIONS=("TOP" "DIST_NOLOG" "DIST_LOG")


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
	-out "$SIM_DIR/$simFile.tmp.gz" \
	-ALL 2>&1 | tee $LOG_DIR/"$simFile.log.txt"

	echo "sorting"
	zcat "$SIM_DIR/$simFile.tmp.gz" | sort -k1,1 -k3,3rg | gzip -9 > "$SIM_DIR/$simFile"
	rm "$SIM_DIR/$simFile.tmp.gz"


        simFile2=lucene-similarities-$name

        java ${RUN_ST_JAR} \
        de.tudarmstadt.lt.structuredtopics.similarity.SenseSimilarityCalculator \
        -in $ddt \
        -out "$SIM_DIR/$simFile2.tmp.gz" \
        -N 100 2>&1 | tee $LOG_DIR/"$simFile2.log.txt"

        echo "sorting"
        zcat "$SIM_DIR/$simFile2.tmp.gz" | sort -k1,1 -k3,3rg | gzip -9 > "$SIM_DIR/$simFile2"
	rm "$SIM_DIR/$simFile2.tmp.gz"


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
	-out $MAPPING_DIR/$mappingFile".tmp.gz" 2>&1 | tee $LOG_DIR/$mappingFile.log

	echo "sorting mappings"
	zcat $MAPPING_DIR/$mappingFile".tmp.gz" | sort -k3,3rn | gzip -9 > $MAPPING_DIR/$mappingFile
	rm $MAPPING_DIR/$mappingFile".tmp.gz"

done


echo "aggregating results"
rm $RESULT_FILE

java ${RUN_ST_JAR} \
de.tudarmstadt.lt.structuredtopics.evaluate.Experiment2ResultAggregator \
-resultDir $EXPERIMENT_DIR \
-out $RESULT_FILE



echo "Finished!"
