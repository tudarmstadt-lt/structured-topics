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

The modules are ordered by the data they are processing. Typically the output of one module is the input of the next one.

###1. Filtering DDTs
For different experiments a filtered version of a ddt can be used. Examples are the filtering of non-word characters or the splitting of ddts by pos-tag.

Usage:
```
java -cp structured-topics-0.0.1-SNAPSHOT_with_dependencies.jar de.tudarmstadt.lt.structuredtopics.convert.DdtFilter input-ddt.csv.gz output-ddt.csv.gz NN
```

All words (cluster words and senses) in the ddt are filtered by the regular expression `.*[a-zA-Z]+.*` (must contain at least one alphabetic character). If a POS-TAG is passed, all words and senses are filtered by the POS-Tag too.
If a sense is filtered, all words from the sense cluster are removed too. If all words of a sense cluster are filtered and the sense has an empty cluster after filtering, it is also removed.

The parameters are not named and are expected to be passed in order:

 - First parameter ---> Input ddt, may be in .csv oder .csv.gz-format. The file will not be modified.
 - Second parameter ---> Target file for the output ddt, may be .csv or .csv.gz. This file will be created.
 - Third parameter ---> (optional) a POS-Tag, e.g. _NN_ or _VB_ or _JJ_, ...


###2. Compute Sense Similarities

Expected Input: DDT with sense clusters, as .csv.gz file with the format:

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

 - in --> Input DDT.
 - out --> Output file.
 - N --> Number of top similar senses to be collected for each sense.
 - filterpos --> (optional) All senses and words are filtered by pos-tag.
 - filterregex --> (optional) All senses and words are filtered by the regex `".*[a-zA-Z]+.*"`.
 - ALL --> (optional) Each sense is similar all words in the cluster with the weight of the word. This creates a larger graph and ignores the N-parameter.
 
###2.5 Prune Sense Similarities

This takes the sense similarities from the previous step as input and prunes the number of similar senses.
For many runs with different _N_ settings, it is cheaper to reuse and prune a file with the maximum required _N_ for all further computations like clustering.
The similarities have to be sorted by first column (sense-name) first and third column (similarity) in reverse order secondly.
E.g. apply the command `zcat similarities.csv.gz | sort -k1,1 -k3,3rg | gzip -9  > similarities-sorted.csv.gz` prior to pruning.

Usage:

```
java -cp structured-topics-0.0.1-SNAPSHOT_with_dependencies.jar de.tudarmstadt.lt.structuredtopics.similarity.SortedSenseSimilarityPruner -in similarities-sorted.csv.gz -out similarities.csv.gz -sensesToKeep 200
```

Parameters:

 - in ---> Sorted similarities.
 - out ---> Output file.
 - sensesToKeep -> Number of top similar senses to keep for each sense.
 - binarize ---> (optional) Replace all weights with 1.
 - similarityThreshold ---> (optional) Additional pruning: If the similarity drops below this threshold (factor to the top similarity), all further senses are pruned..
 
###3. Clustering
 
The graph of similar senses can be clustered using the <https://github.com/tudarmstadt-lt/chinese-whispers> project (the jar can be build using the `mvn package` command.
 
Usage:

```
java -cp chinese-whispers.jar de.tudarmstadt.lt.cw.global.CWGlobal -in similarities.csv.gz -N 1000 -out clusters.csv.gz -cwOption TOP
```

Parameters:

 - in ---> The similarities.
 - out ---> The output file.
 - N ---> maximum number of Edges per Node (should be >= the N for the similarity calculation).
 - cwOption ---> Strategy for the clustering, may be TOP, DIST_LOG, or DIST_NOLOG, see the [original manual](http://wortschatz.informatik.uni-leipzig.de/~cbiemann/software/CW.html#_Toc135565171) for a detailed explanation.
 
 The output will have the following format, with one cluster per line:
 ```
<cluster-id>\t<cluster-size>\t<sense1>, <sense2>, ... 
```
 
###3.5 Cluster Labeling
 
Further steps take labeled clusters as input. The label should be in the third column and may be empty:

```
<cluster-id>\t<cluster-size>\t<cluster-label>\t<cluster-node1>, <cluster-node2>, ... 
```

An elasticsearch index on the labeled clusters can be built using:

```
java -cp structured-topics-0.0.1-SNAPSHOT_with_dependencies.jar de.tudarmstadt.lt.structuredtopics.evaluate.Indexer -clusters clusters.csv.gz -index /path/to/index/directory
```

A sample usage of the search index is the labeling of named entity data. The input may be one of the xml-files from <https://github.com/AKSW/n3-collection> and the index directory from the previous step.

```
java -cp structured-topics-0.0.1-SNAPSHOT_with_dependencies.jar de.tudarmstadt.lt.structuredtopics.evaluate.Searcher -index /path/to/index/directory -data data.xml -out result.csv
```

Each word of the data will be printed to a separate line and labeled if it was part of a named entity.
The result will be in the format:

```
<word>\t<tag>\t<labels>
```
Where tag is empty or "NE" if the word is part of a named entity and labels is empty or a csv-list of all lables found in the search index for the given word.

###4. Crawling Babelnet

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

 - key ---> API key for babelnet.
 - apiCache ---> directory where responses from the api are cached.
 - synsetStart ---> IDs of the starting synsets. Pass as csv list (<id1>,<id2>,...) or single element (<id1>).
 - out ---> File where the found senses are saved in csv format.
 - visited ---> File where ids of visited synsets are saved in csv format.
 - queue ---> File where the queue of synsets is cached (csv.format).
 - cleanSenses ---> (optional) Some senses have additional information like pos-tag or the domain attached. This option will clean the senses before writing them to the output-file (no arguments).
 
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

###5. Map clusters to babelnet domains
To find clusters which are similar to the babelnet domains, the following module can be used:

```
java -cp structured-topics-0.0.1-SNAPSHOT_with_dependencies.jar de.tudarmstadt.lt.structuredtopics.evaluate.MapClustersToBabelnetSenses -bnetSenses foundSenses_COMPUTING.csv -clusters clusters-sorted-nsi-similarities-senses-wiki-n30-1600k.csv.gz -out clustersCOMPUTING.csv
```

Parameters:

 - bnetSenses ---> Senses from the previous step (file of the out-argument).
 - clusters ---> Clusters from the clustering module.
 - out ---> The scored clusters.
 - outDomainIndex ---> (optional) creates a dump of the used babelnet domains.
 

 
The output file will have the following format:
```
<clusterId>\t<clusterSize>\t<topOverlap>\t<topOverlapDomain>\t<topOverlap>\t<topPurityDomain>\t<topPurity>\t<topSimpleScoreDomain>\t<topSimpleScore>\t<topCosineScoreDomain>\t<topCosineScore>\t<cluster-words>
```

The domain index is written to a csv-file with the format `<domain-name>\t<domain-size>\t<domain-words>` where the domain-words are separated by `, ` and each word has the corresponding weight concatenated with `:` as separator.

Example:
```
MUSIC   203479  do_you_wanna_touch_me:0.414955511805, greatest hits volume ii:0.425302327594, alt-wiener_tanzweisen:0.361288690968, ...
```

####Metrics
The following metrics are used:

 - Overlap: Number of cluster words contained in the domain, divided by the size of the cluster
 - Simple Score: Sum of the weights of the domain words which are contained in the cluster, divided by the size of the cluster
 - Cosine score: cosine score between the cluster and each domain. Words in the cluster are treated with weight 1, words in the domain with their given weights.
 - Purity: Simple score multiplied by the log size of the cluster
 
 In most of the cases, all scores will map to the same domain but there may be small differences related to the different metrics.
 
 
Example:
 
```
2641	13	1	PHYSICS_AND_ASTRONOMY	1.9544985425833155	PHYSICS_AND_ASTRONOMY	0.7620027806387693	PHYSICS_AND_ASTRONOMY 0.00003836444703270673	Phobos#6, Mimas#8, Deimos#7, Iapetus#2, Triton#14, Tethys#2, Charon#4, Himalia#0, Io#4, Dione#2, Ganymede#10, Callisto#9, Enceladus#6, 
```

###6. Collect sense images
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

 - downloadList ---> the output file from the image extractor (previous step).
 - downloadTo ---> directory where all images will be stored.
 - index ---> csv file with an index for all senses and images.
 
 Images will be named by the synsetId, the hashed url and the extension of the url.
 
 The index will have the following format:
 ```
<sense>\t<synsetId>\t<pathToImage>
```

Note, that senses may be contained multiple times for different synsets.

###7. REST-Server for images

A lookup-server with a REST-interface (using an embedded Jetty) for the images can be run using the following command:
 ```
java -cp structured-topics-0.0.1-SNAPSHOT_with_dependencies.jar de.tudarmstadt.lt.structuredtopics.babelnet.ImageServer 9090 ./senseImages.csv http://localhost:9090
```
The parameters have to be passed in order:

- First parameter ---> the port to use.
- Second parameter ---> path to the image index file from the image collector.
- Third parameter ---> Path to the server, must include the port and should not end with a /

The server will build an index over all images, using the paths from the image index.
The index is expected to have the following format, which is exactly the format produced by the image collector from the previous module:

 ```
<sense>\t<synsetId>\t<pathToImage>
```

The second column is not (yet?) used and can be kept empty. The paths to the images must be valid and accessible from the server.

The following REST-endpoints are provided by the server (relative to the base-url):

 - `/images/one/{word}` ---> `{word}` can be any word. This will return the first found image for the given word or no content if no image for the word was found
 - `/images/all/{word}` ---> returns a list (json) with all urls to access all images found for the given word
 - `/images/index/{word}/{index}` ---> can be used to access a specific image for a given word. Use the `/all/{word}` endpoint for a list of valid URLs. `{index}` is the index number, starting from 0. If the index is out of range or no images for the words exist at all this will return no content, similar to the `/images/one/{word}` endpoint.
 - `/images/index` ---> returns a list of all known words (may be a large response, depending on the size of the index)


##Piped modules
The script scripts/run_experiment2.sh can be used to run an aggregated version of the modules.
It requires the configuration of a base directory, the structured topics jar and the chinese whispers jar.
DDTs have to be placed in the experimentDirectory/1_ddts.
In addition the foundSenses.csv from the babelnet crawler is required for the mappings.

When executed, the script will:

 1. create similarities for all ddts.
 2. cluster the similarities with different options.
 3. map the clusters to the babelnet domains.
 4. Aggregate the results to a .csv-file.
 
Filenames and paths can be modified inside the script, the script itself takes no arguments.

###Result Aggregation
The result aggregation can be modified in the class `de.tudarmstadt.lt.structuredtopics.evaluate.Experiment2ResultAggregator`.

For the result-aggregation to work correctly, the following naming patterns are expected (excluding the ddts, all are generated by the called modules):

 - DDTs: any filename, `filtered-` prefix if the ddt was filtered in any way (maps to the filtered-column). May be a `.csv.gz` or `.csv`-file.
 - Similarities: `<metric>-` prefix (metric is the selected similarity metric) plus the filename of the input-ddt.
 - Clusters: `clusters-<option>-` prefix (option is the selected chinese whispers option) plus the filename of the clustered similarities.
 - Mappings: `domains-` prefix plus the filename of the mapped clusters.

For example the file `domains-clusters-TOP-lucene-similarities-senses-wiki-n30-1600k.csv.gz` is the ddt `senses-wiki-n30-1600k.csv.gz` where similarities have been calculated using the `lucene`-metric, the clusters have been calculated using the `TOP`-option and the clusters have been mapped agains babelnet domains.


The current output format of the result aggregation contains the following columns:
```
ddtName,filtered,totalSenses,uniqueSenseWords,totalClusterWords,uniqueClusterWords,averageClusterSize,similarityMetric,numberOfEdges,cwOption,numberOfClusters,maxOverlap,avgOverlap,totalOverlap,maxCosineScore,totalCosineScore
```
The columns are added as header to the csv-file for a simple import to a table calculator.

 - ddtName ---> name of the input file without extension.
 - filtered ---> true or false if the ddt was filtered.
 - totalSenses ---> overall number of senses in the ddt.
 - uniqueSenseWords ---> unique words without disambiguation.
 - totalClusterWords ---> overall number of words in all sense clusters.
 - uniqueClusterWords ---> unique words in all sense clusters.
 - averageClusterSize ---> average size of a sense cluster.
 - similarityMetric ---> all or lucene (see the similarity module).
 - numberOfEdges ---> number of generated edges in the similarity graph.
 - cwOption ---> selected chinese whispers option. (see clustering section)
 - numberOfClusters ---> number of clusters found by chinese whispers.
 - maxOverlap ---> highest overlap score for this dataset (see metrics section).
 - avgOverlap ---> average overlap for this dataset.
 - totalOverlap ---> summed up overlap for this dataset.
 - maxCosineScore ---> highest cosine score for this dataset.
 - totalCosineScore ---> summed up cosine score for this dataset.
