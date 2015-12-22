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

###Compute sense similarities

Expected Input: sense clusters, as .csv.gz file with the format:

```
<word>#<pos-tag>	<sense id>	<word>#<pos-tag>#<sense id>:<weight>, <word>#<pos-tag>#<sense id>:<weight>, ...
```

Output: Gzipped file, containing one similarity per line:

```
<word>#<pos-tag>#<sense id>	<word>#<pos-tag>#<sense id>	<similarity>
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
 
###Prune sense similarities

This takes the sense similarities from the previous step as input and prunes the number of similar senses.
For many runs with different _N_ settings, it is cheaper to reuse and prune a file with the maximum required _N_ for all further computations like clustering.
The similarities have to be sorted by first column (sense-name) first and third column (similarity) in reverse order secondly.
E.g. apply the command `zcat similarities.csv.gz | sort -k1,1 -k3,3rg | gzip -9  > similarities-sorted.csv.gz` prior to pruning.

Usage:

```
java -cp structured-topics-0.0.1-SNAPSHOT_with_dependencies.jar de.tudarmstadt.lt.structuredtopics.similarity.SortedSenseSimilarityPruner -in similarities-sorted.csv.gz -out similarities.csv.gz -sensesToKeep 200
```

Parameters:

 - in --> Sorted similarities
 - out --> Output file
 - sensesToKeep -> Number of top similar senses to keep for each sense
 - binarize --> (optional) Replace all weights with 1
 - similarityThreshold --> (optional) Additional pruning: If the similarity drops below this threshold (factor to the top similarity), all further senses are pruned.
 
###Clustering
 
The graph of similar senses can be clustered using the https://github.com/tudarmstadt-lt/chinese-whispers project.
 
Usage:
```
java -cp chinese-whispers.jar de.tudarmstadt.lt.cw.global.CWGlobal -in similarities.csv.gz -N 1000 -out clusters.csv.gz
```

Parameters:

 - in --> The similarities
 - out --> The output file
 - N --> maximum number of Edges per Node (should be >= the N for the similarity calculation)