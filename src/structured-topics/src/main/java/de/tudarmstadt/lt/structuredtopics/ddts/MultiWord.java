package de.tudarmstadt.lt.structuredtopics.ddts;

import java.util.List;

public abstract class MultiWord {

	private List<SingleWord> words;
	private String fullWord = null;

	public MultiWord(List<SingleWord> words) {
		this.words = words;
	}

	/**
	 * Returns the text of all words concatenated by whitespace
	 */
	public String getFullWord() {
		// lazy initialize the field
		if (fullWord == null) {
			if (words.size() > 1) {
				String full = "";
				for (int i = 0; i < words.size(); i++) {
					full += words.get(i).getText();
					if (i < words.size() - 1) {
						full += " ";
					}
				}
				fullWord = full;
			} else if (words.size() == 1) {
				fullWord = words.get(0).getText();
			} else {
				fullWord = "";
			}
		}
		return fullWord;
	}

	public List<SingleWord> getWords() {
		return words;
	}

	public void setWords(List<SingleWord> word) {
		this.words = word;
		fullWord = null;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((words == null) ? 0 : words.hashCode());
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
		MultiWord other = (MultiWord) obj;
		if (words == null) {
			if (other.words != null)
				return false;
		} else if (!words.equals(other.words))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "MultiWord [words=" + words + "]";
	}

}