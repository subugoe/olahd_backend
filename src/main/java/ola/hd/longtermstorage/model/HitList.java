package ola.hd.longtermstorage.model;

public class HitList {

	    private String title;
	    private String year;
	    private String creator;
	    private FulltextSnippets fulltextSnippets;

	public HitList(String title, String year, String creator, FulltextSnippets fulltextSnippets) {
		this.title = title;
		this.year = year;
		this.creator = creator;
		this.fulltextSnippets = fulltextSnippets;
	}

	//getters and setters

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getYear() {
		return year;
	}

	public void setYear(String year) {
		this.year = year;
	}

	public String getCreator() {
		return creator;
	}

	public void setCreator(String creator) {
		this.creator = creator;
	}

	public FulltextSnippets getFulltextSnippets() {
		return fulltextSnippets;
	}

	public void setFulltextSnippets(FulltextSnippets fulltextSnippets) {
		this.fulltextSnippets = fulltextSnippets;
	}

}

