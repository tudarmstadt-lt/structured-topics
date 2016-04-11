package de.tudarmstadt.lt.structuredtopics.ddts;

import java.util.List;

public class ClusterWord extends MultiWord {

	private Integer relatedSenseId;
	private Double weight;

	public ClusterWord(List<SingleWord> words, Integer relatedSenseId, Double weight) {
		super(words);
		this.relatedSenseId = relatedSenseId;
		this.weight = weight;
	}

	public Integer getRelatedSenseId() {
		return relatedSenseId;
	}

	public void setRelatedSenseId(Integer relatedSenseId) {
		this.relatedSenseId = relatedSenseId;
	}

	public Double getWeight() {
		return weight;
	}

	public void setWeight(Double weight) {
		this.weight = weight;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((relatedSenseId == null) ? 0 : relatedSenseId.hashCode());
		result = prime * result + ((weight == null) ? 0 : weight.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		ClusterWord other = (ClusterWord) obj;
		if (relatedSenseId == null) {
			if (other.relatedSenseId != null)
				return false;
		} else if (!relatedSenseId.equals(other.relatedSenseId))
			return false;
		if (weight == null) {
			if (other.weight != null)
				return false;
		} else if (!weight.equals(other.weight))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "ClusterWord [relatedSenseId=" + relatedSenseId + ", weight=" + weight + ", getFullWord()="
				+ getFullWord() + "]";
	}

}
