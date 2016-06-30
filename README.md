#Structured Topics

Different tools to compute structured topics from a disambiguated distributional thesaurus build from the JoBimText sense clusters.

##Requirements

 - java 8
 - git
 - maven
 
##Download & build Code


Build structured-topics-jar:
A simple `mvn install` will create a .jar with all dependencies (`structured-topics-0.0.1-SNAPSHOT_with_dependencies.jar`).

##Modules

All modules are contained in the same jar. As many of the computations work on large data structures, it is recommended to provide additional memory via `-Xms1024M -Xmx8192M` (or larger values).

###Compute Sense Similarities

Expected Input: sense clusters, as .csv.gz file with the format:

```
<word>#<pos-tag>\t<sense id>\t<word>#<pos-tag>#<sense id>:<weight>, <word>#<pos-tag>#<sense id>:<weight>, ...
```

Output: Gzipped file, containing one similarity per line:

```
<word>#<pos-tag>#<sense id>\t<word>#<pos-tag>#<sense id>\t<similarity>
```

Usage:

```
java -cp structured-topics-0.0.1-SNAPSHOT_with_dependencies.jar de.tudarmstadt.lt.structuredtopics.similarity.SenseSimilarityCalculator -in samples/senses-sample.csv.gz -out similarities.csv.gz -N 200 -filterpos -filterregex
```

Parameters:

 - in --> Input DDT
 - out --> Output file
 - N --> Number of top similar senses to be collected for each sense
 - filterpos --> (optional) All senses and words are filtered by pos-tag
 - filterregex --> (optional) All senses and words are filtered by the regex `".*[a-zA-Z]+.*"`
 - ALL --> (optional) Each sense is similar all words in the cluster with the weight of the word. This creates a larger graph and ignores the N-parameter
 
###Prune Sense Similarities

This takes the sense similarities from the previous step as input and prunes the number of similar senses.
For many runs with different _N_ settings, it is cheaper to reuse and prune a file with the maximum required _N_ for all further computations like clustering.
The similarities have to be sorted by first column (sense-name) first and third column (similarity) in reverse order secondly.
E.g. apply the command `zcat similarities.csv.gz | sort -k1,1 -k3,3rg | gzip -9  > similarities-sorted.csv.gz` prior to pruning.

Usage:

```
java -cp structured-topics-0.0.1-SNAPSHOT_with_dependencies.jar de.tudarmstadt.lt.structuredtopics.similarity.SortedSenseSimilarityPruner -in similarities-sorted.csv.gz -out similarities.csv.gz -sensesToKeep 200
```

Parameters:

 - in ---> Sorted similarities
 - out ---> Output file
 - sensesToKeep -> Number of top similar senses to keep for each sense
 - binarize ---> (optional) Replace all weights with 1
 - similarityThreshold ---> (optional) Additional pruning: If the similarity drops below this threshold (factor to the top similarity), all further senses are pruned.
 
###Clustering
 
The graph of similar senses can be clustered using the <https://github.com/tudarmstadt-lt/chinese-whispers> project.
 
Usage:

```
java -cp chinese-whispers.jar de.tudarmstadt.lt.cw.global.CWGlobal -in similarities.csv.gz -N 1000 -out clusters.csv.gz
```

Parameters:

 - in ---> The similarities
 - out ---> The output file
 - N ---> maximum number of Edges per Node (should be >= the N for the similarity calculation)
 
###Cluster Labeling
 
Further steps take labeled clusters as input. The label should be in the third column and may be empty:

```
<cluster-id>\t<cluster-size>\t<cluster-label>\t<cluster-node1>, <cluster-node2>, ... 
```

An elasticsearch index on the labeled clusters can be built using

```
java -cp structured-topics-0.0.1-SNAPSHOT_with_dependencies.jar de.tudarmstadt.lt.structuredtopics.evaluate.Indexer -clusters clusters.csv.gz -index /path/to/index/directory
```

A sample usage of the search index is the labeling of named entity data. The input may be one of the xml-files from <https://github.com/AKSW/n3-collection> and the index directory from the previous step.

```
java -cp structured-topics-0.0.1-SNAPSHOT_with_dependencies.jar de.tudarmstadt.lt.structuredtopics.evaluate.Searcher -index /path/to/index/directory -data data.xml -out result.csv
```

Each word of the data will be printed to a separate line and labeled if it was part of a named entity.
The result will be in the format

```
<word>\t<tag>\t<labels>
```
Where tag is empty or "NE" if the word is part of a named entity and labels is empty or a csv-list of all lables found in the search index for the given word.

###Crawling Babelnet

The babelnet crawler can be used to fetch domain information from <http://babelnet.org>.
The crawler will start at a given set of synsets and follow their edges.
The main sense of each synset is collected, together with the domain(s) of the synset.
Responses of the API are cached to maximize the value of the api-key quota.
The crawler will cache visited synsets and the queue in short intervals and can be restarted from the last state by calling it with the same parameters again. In case of connection errors or a reached key limit, the crawler will suspend and try again in one hour.

Usage:

```
java -cp structured-topics-0.0.1-SNAPSHOT_with_dependencies.jar de.tudarmstadt.lt.structuredtopics.babelnet.Crawler -key <key> -synsetStart bn:00048043n -steps 100000000 -out foundSenses.csv -queue queue.csv -visited visited.csv -apiCache babelnetApiCache -cleanSenses
```

Parameters:

 - key ---> API key for babelnet
 - apiCache ---> directory where responses from the api are cached
 - synsetStart ---> IDs of the starting synsets. Pass as csv list (<id1>,<id2>,...) or single element (<id1>)
 - out ---> File where the found senses are saved in csv format
 - visited ---> File where ids of visited synsets are saved in csv format
 - queue ---> File where the queue of synsets is cached (csv.format)
 - cleanSenses ---> (optional) Some senses have additional information like pos-tag or the domain attached. This option will clean the senses before writing them to the output-file (no arguments)
 
The output file will have the following format:
```
<sense>\t<weight>\t<domain>\t<synset-id>\t<list_of_all_senses_for_the_synset>
```

Example:

```
interview       1.0     LANGUAGE_AND_LINGUISTICS        bn:00047238n       Interview_tips,Interviewy,Interviewed,Interviewers 
procedure       0.430608877889  COMPUTING       bn:00036826n        subprogram,Algorithm_function,Leaf_function,procedure_call
procedure       0.385539749925  MATHEMATICS     bn:00036826n        subprogram,Algorithm_function,Leaf_function,procedure_call
```

If a senses belongs to multiple domains, it is added once for each domain.
Some commands to work with the result file:

Print all domains:

```
<foundSenses.csv awk '{print $3}' | sort | uniq
```

Write all senses from one domain to a separate file:

```
awk -F$'\t' '{print >> ("foundSenses_"$3".csv")}' foundSenses.csv
```

###Map clusters to babelnet domains
To find clusters which are similar to the babelnet domains, the following module can be used:

```
java -cp structured-topics-0.0.1-SNAPSHOT_with_dependencies.jar de.tudarmstadt.lt.structuredtopics.evaluate.MapClustersToBabelnetSenses -bnetSenses foundSenses_COMPUTING.csv -clusters clusters-sorted-nsi-similarities-senses-wiki-n30-1600k.csv.gz -out clustersCOMPUTING.csv
```

Parameters:

 - bnetSenses ---> Senses from the previous step (file of the out-argument).
 - clusters ---> Clusters from the clustering module
 - out ---> The scored clusters.
 
The output file will have the following format:
```
<clusterId>\t<clusterSize>\t<topOverlap>\t<topDomain1>\t<score1>\t<topDomain2>\t<score2>\t...\t<cluster-size>\t<cluster-words>
```

 - Overlap is the number of words from the cluster contained in a babelnet domain, divided by the size of the cluster
 - TopOverlap is the best score over all domains
 - The first domain and score is the max simple score (see below) weighted by the log size of the cluster
 - The second domain and score is the max simple score which is the sum of weights from matched words in the domain divided by the cluster size
 - The third domain and score is the max cosine score between the cluster and each domain. Words in the cluster are treated with weight 1, words in the domain with their given weights.
 
 In most cases, all scores will map to the same domain but there may be small differences related to the different metrics.
 
 
Example:
 
```
2641	13	1	PHYSICS_AND_ASTRONOMY	1.9544985425833155	PHYSICS_AND_ASTRONOMY	0.7620027806387693	PHYSICS_AND_ASTRONOMY 0.00003836444703270673	Phobos#6, Mimas#8, Deimos#7, Iapetus#2, Triton#14, Tethys#2, Charon#4, Himalia#0, Io#4, Dione#2, Ganymede#10, Callisto#9, Enceladus#6, 
```

###Collect sense images
The index built from the babelnet crawler contains links to different images, which can be used to build an index for sense images.

The following module can be used to extract the image urls from the cache:

```
java -cp structured-topics-0.0.1-SNAPSHOT_with_dependencies.jar de.tudarmstadt.lt.structuredtopics.babelnet.ExtractImagesFromCache -apiCache babelnetApiCache -out senseImagesDownloadList.csv
```

Parameters:

 - apiCache ---> refers to the directory of the crawlers apiCache.
 - out ---> Creates a list of urls which can be used for the downloader (next step).
 
 The download list will have the following format:
```
<synsetId>\t<list-of-unique-senses>\t<list-of-image-urls>
```

Images can be downloaded and stored in an index using the next module:
```
java -cp structured-topics-0.0.1-SNAPSHOT_with_dependencies.jar  de.tudarmstadt.lt.structuredtopics.babelnet.DownloadImages -downloadList senseImagesDownloadList.csv -downloadTo senseImages -index senseImages.csv
```

Parameters:

 - downloadList ---> the output file from the image extractor (previous step)
 - downloadTo ---> directory where all images will be stored
 - index ---> csv file with an index for all senses and images
 
 Images will be named by the synsetId, the hashed url and the extension of the url.
 
 The index will have the following format:
 ```
<sense>\t<synsetId>\t<pathToImage>
```

Note, that senses may be contained multiple times for different synsets.

##Piped modules
The script scripts/run_experiment2.sh can be used to run an aggregated version of the modules.
It requires the configuration of a base directory, the structured topics jar and the chinese whispers jar.
DDTs have to be placed in the experimentDirectory/1_ddts.
In addition the foundSenses.csv from the babelnet crawler is required for the mappings.

When executed, the script will create similarities for all ddts, cluster the similarities with different options and map the clusters to the babelnet domains. Finally, the results are aggregated to a .csv-file with some metrics.
For more details, the class `de.tudarmstadt.lt.structuredtopics.evaluate.Experiment2ResultAggregator` may be inspected or modified.