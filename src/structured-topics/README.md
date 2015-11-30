#Structured Topics

Different tools to compute structured topics from a disambiguated distributional thesaurus build from the JoBimText sense clusters.

##Requirements

 - java 8
 - git
 - maven
 
##Download Code & Set Up Pipeline


_TODO_

Build structured-topics-jar:
A simple `mvn install` will create a .jar with all dependencies.

##Modules
All modules are contained in the same jar. As many of the computations work on large data structures, it is recommended to provide additional memory via `-Xms1024M -Xmx8192M` (or larger values).
```
java -cp structured-topics.jar de.tudarmstadt.lt.structuredtopics.similarity.SenseSimilarityCalculator -in input-ddt.csv.gz -out similarities.csv.gz -N 200 -filterpos -filterregex
```


###Compute sense similarities
_TODO_ Format specification

```
java -cp structured-topics.jar de.tudarmstadt.lt.structuredtopics.similarity.SenseSimilarityCalculator -in input-ddt.csv.gz -out similarities.csv.gz -N 200 -filterpos -filterregex
```

 - in --> Input DDT
 - out --> Output file
 - N --> Number of top similar senses to be collected for each sense
 - filterpos --> (optional) All senses and words are filtered by pos-tag
 - filterregex --> (optional) All senses and words are filtered by a regex
 - ALL --> (optional) Each sense is similar to all similar word-senses. This creates a larger graph and ignores the N-parameter
 
###Prune sense similarities

This takes the sense similarities from the previous step as input and prunes the number of similar senses.
For many runs with different _N_ settings, it is cheaper to reuse and prune a file with the maximum required _N_ for all further computations like clustering.
The similarities have to be sorted by first column (sense-name) first and third column (similarity) in reverse order secondly.
E.g. apply the command `zcat similarities.csv.gz | sort -k1,1 -k3,3rg | gzip -9  > similarities-sorted.csv.gz` prior to pruning.

```
java -cp structured-topics.jar de.tudarmstadt.lt.structuredtopics.similarity.SortedSenseSimilarityPruner -in input-ddt.csv.gz -out similarities.csv.gz -N 200 -filterpos -filterregex
```

_TODO_ API rework for command line
