package de.tudarmstadt.lt.structuredtopics.ddts;

import java.util.List;

/**
 * Represents one line in a DDT.
 *
 */
public class SenseCluster {

	private Sense sense;
	private List<ClusterWord> clusterWords;

	public SenseCluster(Sense sense, List<ClusterWord> clusterWords) {
		this.sense = sense;
		this.clusterWords = clusterWords;
	}

	public Sense getSense() {
		return sense;
	}

	public void setSense(Sense sense) {
		this.sense = sense;
	}

	public List<ClusterWord> getClusterWords() {
		return clusterWords;
	}

	public void setClusterWords(List<ClusterWord> cluster) {
		this.clusterWords = cluster;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((clusterWords == null) ? 0 : clusterWords.hashCode());
		result = prime * result + ((sense == null) ? 0 : sense.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SenseCluster other = (SenseCluster) obj;
		if (clusterWords == null) {
			if (other.clusterWords != null)
				return false;
		} else if (!clusterWords.equals(other.clusterWords))
			return false;
		if (sense == null) {
			if (other.sense != null)
				return false;
		} else if (!sense.equals(other.sense))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "SenseCluster [sense=" + sense + ", cluster=" + clusterWords + "]";
	}
}
