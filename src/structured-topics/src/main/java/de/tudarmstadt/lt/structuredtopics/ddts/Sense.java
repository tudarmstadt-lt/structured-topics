package de.tudarmstadt.lt.structuredtopics.ddts;

import java.util.List;

/**
 * Represents a Sense from a DDT (a list of words + the id of the sense).
 */
public class Sense extends MultiWord {

	private Integer senseId;

	public Sense(List<SingleWord> words, Integer senseId) {
		super(words);
		this.senseId = senseId;
	}

	public Integer getSenseId() {
		return senseId;
	}

	public void setSenseId(Integer senseId) {
		this.senseId = senseId;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((senseId == null) ? 0 : senseId.hashCode());
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
		Sense other = (Sense) obj;
		if (senseId == null) {
			if (other.senseId != null)
				return false;
		} else if (!senseId.equals(other.senseId))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "Sense [senseId=" + senseId + ", getFullWord()=" + getFullWord() + "]";
	}

}
