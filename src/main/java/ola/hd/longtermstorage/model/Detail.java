package ola.hd.longtermstorage.model;

import java.io.File;

public class Detail {

	private String title;
	private int year;
	private String creator;
	private String publisher;
	private File fileTree;

	public Detail(String title, int year, String creator, String publisher, File fileTree) {
		this.title = title;
		this.year = year;
		this.creator = creator;
		this.publisher = publisher;
		this.fileTree = fileTree;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public int getYear() {
		return year;
	}

	public void setYear(int year) {
		this.year = year;
	}

	public String getCreator() {
		return creator;
	}

	public void setCreator(String creator) {
		this.creator = creator;
	}

	public String getPublisher() {
		return publisher;
	}

	public void setPublisher(String publisher) {
		this.publisher = publisher;
	}

	public File getFileTree() {
		return fileTree;
	}

	public void setFileTree(File fileTree) {
		this.fileTree = fileTree;
	}

}
