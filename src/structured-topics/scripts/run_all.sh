#!/bin/bash

BASEDIR="$HOME/pipeline"
JARS_BASEPATH="${BASEDIR}/jars"
RUN_JAVA=~/jdk8/jdk1.8.0_60/bin/java
JAVA_PARAMS='-Xms4G -Xmx10G'
JAR_ST="${JARS_BASEPATH}/structured-topics-0.0.1-SNAPSHOT_with_dependencies_2015_10_26_16_45.jar"

INPUT_FOLDER=~/pipeline/in

ddts=( )
ddts_label=( )

ddts[0]="${INPUT_FOLDER}/ddt-news-n50-485k-closure.csv.gz"
ddts_label[0]="n50-485k-closure"

ddts[1]="${INPUT_FOLDER}/ddt-news-n200-345k-closure.csv.gz"
ddts_label[1]="n200-345k-closure"

ddts[2]="${INPUT_FOLDER}/senses-wiki-n30-1600k.csv.gz"
ddts_label[2]="n30-1600k"

ddts[3]="${INPUT_FOLDER}/senses-wiki-n200-380k.csv.gz" 
ddts_label[3]="n200-380k"


word_freqs=( )
word_freqs_label=( )

word_freqs[0]="${INPUT_FOLDER}/word-freq-news.gz"
word_freqs_label[0]="wfn"

similar_senses=()

similar_senses[0]=200
similar_senses[1]=150
similar_senses[2]=100
similar_senses[3]=50
similar_senses[4]=25
similar_senses[5]=10
similar_senses[6]=5



for i in "${!ddts[@]}"; do 
  	ddt=${ddts[$i]}
	ddt_label=${ddts_label[$i]}

	dir_temp_ddt_sim="${INPUT_FOLDER}/tmp"

	# ompute similarities with max pruning value for current ddt

	sense_similarities="${dir_temp_ddt_sim}/sense_similarities_sorted.csv"
	sense_similarities_tmp="${dir_temp_ddt_sim}/sense_similarities.csv"

	mkdir ${dir_temp_ddt_sim}
	echo 'created '${dir_temp_ddt_sim}

	echo 'calculating sense similarities for '${ddt}
	${RUN_JAVA} ${JAVA_PARAMS} -cp ${JAR_ST} \
	de.tudarmstadt.lt.structuredtopics.similarity.SenseSimilarityCalculator \
	${ddt} \
	${sense_similarities_tmp} \
	${similar_senses[0]}  &> ${dir_temp_ddt_sim}'/log.txt'
	
	echo 'sorting similarities'
	sort -k 1,1 -k 3,3rn ${sense_similarities_tmp} > ${sense_similarities}
	rm ${sense_similarities_tmp}	

	echo 'output file available at '${sense_similarities}
	continue_step='step3'


	for j in "${!word_freqs[@]}"; do
		word_freq=${word_freqs[$j]}
		word_freq_label=${word_freqs_label[$j]}
		for k in "${!similar_senses[@]}"; do
			similar_senses_value=${similar_senses[$k]}
			folder_prefix="${ddt_label}_${word_freq_label}_${similar_senses_value}sim"
			echo "running configuration $folder_prefix"
			./run_pipeline.sh ${sense_similarities} ${word_freq} ${similar_senses} false "${folder_prefix}_weighted"
			./run_pipeline.sh ${sense_similarities} ${word_freq} ${similar_senses} true "${folder_prefix}_bin"  
		done
	done

	#cleanup for next ddt
	rm -r ${dir_temp_ddt_sim}
	echo 'removed '${dir_temp_ddt_sim}
done

#/bin/bash ./run_pipeline.sh ~/mt_pipeline/in/senses-wiki-n30-1600k.csv.gz ~/mt_pipeline/in/word-freq-news.gz 50 false test_config
